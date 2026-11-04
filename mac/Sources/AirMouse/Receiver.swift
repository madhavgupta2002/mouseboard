import AppKit
import Network
import Security
import AirMouseCore

private final class Peer {
    let connection: NWConnection
    var lines = LineBuffer()
    let created = Date()
    var lastSeen = Date()
    var authenticated = false
    init(_ connection: NWConnection) { self.connection = connection }
}

final class Receiver: ObservableObject {
    @Published var code = ""
    @Published var installToken = ""
    @Published var addresses: [String] = []
    @Published var selectedAddress = ""
    @Published var status = "Starting receiver…"
    @Published var connected = false
    @Published var enabled = true
    @Published var hasPermission = false
    @Published var serverReady = false
    @Published var installStatus = ""
    @Published var installReady = false
    let port: UInt16 = 47821
    let webPort: UInt16 = 47822
    private var listener: NWListener?
    private var installer: InstallServer?
    private var peers: [UUID: Peer] = [:]
    private var activePeer: UUID?
    private var timer: Timer?
    private var failedAttempts: [Date] = []
    let mouse: MouseDriver

    init() {
        mouse = MouseDriver(diagnostic: CommandLine.arguments.contains("--diagnostic-server"))
        rotateCode()
    }

    var installURL: String { "http://\(selectedAddress):\(webPort)/\(installToken)" }

    func start() {
        guard listener == nil else { return }
        refreshAddresses()
        hasPermission = mouse.hasPermission
        do {
            let tcp = NWProtocolTCP.Options()
            tcp.noDelay = true
            let parameters = NWParameters(tls: nil, tcp: tcp)
            parameters.allowLocalEndpointReuse = true
            let listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)
            self.listener = listener
            listener.stateUpdateHandler = { [weak self] state in
                guard let self else { return }
                switch state {
                case .ready:
                    self.serverReady = true
                    self.status = "Ready to pair"
                    if self.mouse.diagnostic {
                        print("READY \(self.port)")
                        fflush(stdout)
                    }
                case .failed(let error):
                    self.serverReady = false
                    self.status = "Receiver error: \(error.localizedDescription)"
                default: break
                }
            }
            listener.newConnectionHandler = { [weak self] in self?.accept($0) }
            listener.start(queue: .main)
            let installer = InstallServer(port: webPort, details: { [weak self] in
                guard let self else { return ("", "", UInt16(47821), "") }
                return (self.selectedAddress, self.code, self.port, self.installToken)
            })
            installer.onStatus = { [weak self] ready, message in
                self?.installReady = ready
                self?.installStatus = message
            }
            self.installer = installer
            try installer.start()
        } catch {
            if listener == nil { status = "Cannot start: \(error.localizedDescription)" }
            else { installStatus = "Install page unavailable: \(error.localizedDescription)" }
        }
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self else { return }
            let permission = self.mouse.hasPermission
            if self.hasPermission != permission {
                self.hasPermission = permission
                self.sendState()
            }
            for (id, peer) in self.peers {
                if Date().timeIntervalSince(peer.authenticated ? peer.lastSeen : peer.created) > 6 {
                    self.remove(id)
                }
            }
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        for peer in peers.values { peer.connection.cancel() }
        peers.removeAll()
        activePeer = nil
        listener?.cancel()
        listener = nil
        installer?.stop()
        installer = nil
    }

    func refreshAddresses() {
        var result: [(String, String)] = []
        var interfaces: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&interfaces) == 0 else { return }
        defer { freeifaddrs(interfaces) }
        var pointer = interfaces
        while let interface = pointer {
            defer { pointer = interface.pointee.ifa_next }
            let item = interface.pointee
            guard let addr = item.ifa_addr, addr.pointee.sa_family == UInt8(AF_INET),
                  (item.ifa_flags & UInt32(IFF_UP)) != 0,
                  (item.ifa_flags & UInt32(IFF_LOOPBACK)) == 0 else { continue }
            let name = String(cString: item.ifa_name)
            guard name.hasPrefix("en") || name.hasPrefix("bridge") else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(addr, socklen_t(addr.pointee.sa_len), &host, socklen_t(host.count),
                           nil, 0, NI_NUMERICHOST) == 0 {
                result.append((name, String(cString: host)))
            }
        }
        addresses = result.sorted { $0.0 < $1.0 }.map(\.1)
        if !addresses.contains(selectedAddress) { selectedAddress = addresses.first ?? "" }
    }

    func rotateCode() {
        if let id = activePeer { remove(id) }
        installToken = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        var bytes = [UInt8](repeating: 0, count: 6)
        if SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess {
            code = bytes.map { String(format: "%02X", $0) }.joined()
        } else {
            code = String(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12))
        }
        if mouse.diagnostic, let testCode = ProcessInfo.processInfo.environment["AIR_MOUSE_TEST_CODE"] {
            code = testCode
        }
        if mouse.diagnostic, let testToken = ProcessInfo.processInfo.environment["AIR_MOUSE_TEST_TOKEN"] {
            installToken = testToken
        }
    }

    func toggleEnabled() { enabled.toggle(); sendState() }
    func disconnect() { if let id = activePeer { remove(id) } }

    private func accept(_ connection: NWConnection) {
        guard peers.count < 8 else { connection.cancel(); return }
        let id = UUID()
        let peer = Peer(connection)
        peers[id] = peer
        connection.stateUpdateHandler = { [weak self] state in
            if case .failed = state { self?.remove(id) }
            if case .cancelled = state { self?.remove(id) }
        }
        connection.start(queue: .main)
        receive(id)
    }

    private func receive(_ id: UUID) {
        guard let peer = peers[id] else { return }
        peer.connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { [weak self] data, _, done, error in
            guard let self, let peer = self.peers[id] else { return }
            if let data, !data.isEmpty {
                do {
                    for line in try peer.lines.append(data) {
                        guard self.peers[id] != nil else { break }
                        try self.handle(WireMessage.decode(line), id: id)
                    }
                } catch { self.remove(id); return }
            }
            if done || error != nil { self.remove(id) }
            else if self.peers[id] != nil { self.receive(id) }
        }
    }

    private func handle(_ message: WireMessage, id: UUID) throws {
        guard let peer = peers[id] else { return }
        if !peer.authenticated {
            failedAttempts.removeAll { Date().timeIntervalSince($0) > 60 }
            let supplied = (message.code ?? "").uppercased().filter { !$0.isWhitespace && $0 != "-" }
            guard failedAttempts.count < 10, message.type == "hello",
                  message.version == 1, constantTimeEqual(supplied, code) else {
                failedAttempts.append(Date())
                reject(id, "Pairing failed. Check the code; after 10 attempts wait one minute.")
                return
            }
            guard activePeer == nil else { reject(id, "Another phone is already connected."); return }
            peer.authenticated = true
            peer.lastSeen = Date()
            activePeer = id
            connected = true
            status = "Phone connected"
            send(id, ["type": "welcome", "version": 1, "enabled": enabled && hasPermission])
            return
        }
        peer.lastSeen = Date()
        switch message.type {
        case "move":
            guard let (dx, dy) = message.movement else { remove(id); return }
            if enabled && hasPermission { mouse.move(dx: dx, dy: dy) }
        case "click":
            guard message.button == "left" || message.button == "right" else { remove(id); return }
            if enabled && hasPermission { mouse.click(button: message.button!) }
        case "heartbeat":
            send(id, ["type": "heartbeat"])
        case "bye": remove(id)
        default: remove(id)
        }
    }

    private func constantTimeEqual(_ a: String, _ b: String) -> Bool {
        let left = Array(a.utf8), right = Array(b.utf8)
        guard left.count == right.count else { return false }
        return zip(left, right).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
    }

    private func sendState() {
        if let id = activePeer { send(id, ["type": "state", "enabled": enabled && hasPermission]) }
    }

    private func send(_ id: UUID, _ object: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: object), let peer = peers[id] else { return }
        peer.connection.send(content: data + Data([10]), completion: .contentProcessed { [weak self] error in
            if error != nil { self?.remove(id) }
        })
    }

    private func reject(_ id: UUID, _ reason: String) {
        guard let peer = peers[id], let data = try? JSONSerialization.data(withJSONObject: ["type": "error", "message": reason]) else {
            remove(id); return
        }
        peer.connection.send(content: data + Data([10]), completion: .contentProcessed { [weak self] _ in
            self?.remove(id)
        })
    }

    private func remove(_ id: UUID) {
        guard let peer = peers.removeValue(forKey: id) else { return }
        peer.connection.cancel()
        if activePeer == id {
            activePeer = nil
            connected = false
            status = serverReady ? "Ready to pair" : "Receiver stopped"
        }
    }
}
