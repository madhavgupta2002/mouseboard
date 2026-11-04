import Foundation
import Network

/// Small, read-only installer page. It serves only the bundled APK and generated HTML.
final class InstallServer {
    let port: UInt16
    let details: () -> (String, String, UInt16, String)
    var onStatus: ((Bool, String) -> Void)?
    private var listener: NWListener?
    private var clients: [UUID: NWConnection] = [:]
    private var apk: Data?

    init(port: UInt16, details: @escaping () -> (String, String, UInt16, String)) {
        self.port = port
        self.details = details
        if let url = Bundle.main.url(forResource: "AirMouse-Android", withExtension: "apk") {
            apk = try? Data(contentsOf: url)
        }
    }

    func start() throws {
        let listener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: port)!)
        self.listener = listener
        listener.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready: self?.onStatus?(true, "Install page ready")
            case .failed(let error): self?.onStatus?(false, error.localizedDescription)
            default: break
            }
        }
        listener.newConnectionHandler = { [weak self] connection in self?.accept(connection) }
        listener.start(queue: .main)
    }

    func stop() {
        listener?.cancel()
        listener = nil
        for connection in clients.values { connection.cancel() }
        clients.removeAll()
    }

    private func accept(_ connection: NWConnection) {
        guard clients.count < 12 else { connection.cancel(); return }
        let id = UUID()
        clients[id] = connection
        connection.start(queue: .main)
        // Bound slow/incomplete requests; never leave an idle connection open.
        DispatchQueue.main.asyncAfter(deadline: .now() + 5) { [weak self] in self?.close(id) }
        read(id, buffer: Data())
    }

    private func read(_ id: UUID, buffer: Data) {
        guard let connection = clients[id] else { return }
        connection.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self] data, _, done, error in
            guard let self, self.clients[id] != nil else { return }
            let request = buffer + (data ?? Data())
            if request.count > 8192 { self.close(id); return }
            if request.range(of: Data("\r\n\r\n".utf8)) != nil {
                self.respond(id, request: request)
            } else if done || error != nil { self.close(id) }
            else { self.read(id, buffer: request) }
        }
    }

    private func respond(_ id: UUID, request: Data) {
        guard let first = String(data: request, encoding: .utf8)?.components(separatedBy: "\r\n").first else { close(id); return }
        let parts = first.split(separator: " ")
        guard parts.count == 3, parts[0] == "GET" else {
            reply(id, status: "405 Method Not Allowed", type: "text/plain", body: Data("GET only".utf8))
            return
        }
        let path = parts[1].split(separator: "?").first ?? ""
        let token = details().3
        if path == "/\(token)/AirMouse-Android.apk", let apk {
            reply(id, status: "200 OK", type: "application/vnd.android.package-archive", body: apk,
                  extra: "Content-Disposition: attachment; filename=\"AirMouse-Android.apk\"\r\n")
        } else if path == "/\(token)" || path == "/\(token)/" {
            reply(id, status: "200 OK", type: "text/html; charset=utf-8", body: Data(page().utf8))
        } else {
            reply(id, status: "404 Not Found", type: "text/plain", body: Data("Not found".utf8))
        }
    }

    private func reply(_ id: UUID, status: String, type: String, body: Data, extra: String = "") {
        guard let connection = clients[id] else { return }
        let headers = "HTTP/1.1 \(status)\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\(extra)\r\n"
        connection.send(content: Data(headers.utf8) + body, completion: .contentProcessed { [weak self] _ in self?.close(id) })
    }

    private func close(_ id: UUID) {
        clients.removeValue(forKey: id)?.cancel()
    }

    private func page() -> String {
        let (host, code, receiverPort, token) = details()
        let link = "airmouse://connect?host=\(host)&port=\(receiverPort)&code=\(code)"
        let download = apk == nil ? "<p>APK missing. Run scripts/build-all.sh on your Mac.</p>" :
            "<a class=\"button\" href=\"/\(token)/AirMouse-Android.apk\">Download Android app ↓</a>"
        return """
        <!doctype html><html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1"><title>AirMouse · Connect</title>
        <style>
        *{box-sizing:border-box}body{margin:0;background:#0c131c;color:#f3f6fa;font:17px/1.6 system-ui,sans-serif;padding:40px 24px}
        main{max-width:520px;margin:auto}.brand{color:#88eac4;font-size:14px;font-weight:700;letter-spacing:.18em}
        h1{font-size:40px;line-height:1.1;letter-spacing:-.04em;margin:24px 0 16px}p{color:#aebac9}
        section{border-top:1px solid #2b3745;padding:24px 0}h2{font-size:20px;margin:0 0 8px}
        .button{display:block;background:#88eac4;color:#092a24;padding:15px 20px;border-radius:14px;text-decoration:none;text-align:center;font-weight:750;margin:18px 0}
        .secondary{background:#233247;color:#f3f6fa}code{color:#88eac4;overflow-wrap:anywhere}small{color:#aebac9}
        </style></head><body><main><div class="brand">↗ AIR MOUSE</div>
        <h1>Your phone.<br>Your pointer.</h1><p>A little movement goes a long way. Keep both devices on the same trusted Wi-Fi.</p>
        <section><h2>1. Install on your Samsung</h2><p>Download the APK, open it, and allow installation from your browser when Android asks. Only enable this for an APK you trust; you can turn the permission off afterward.</p>
        \(download)
        <small>If Samsung Auto Blocker prevents installation, review Settings → Security and privacy → Auto Blocker. A managed phone may not permit sideloading.</small></section>
        <section><h2>2. Pair with this Mac</h2><p>After installing, return here and tap below. Then tap Connect inside AirMouse.</p>
        <a class="button secondary" href="\(link)">Open AirMouse &amp; fill pairing details ↗</a>
        <p>Manual pairing: Mac address <code>\(host)</code><br>Code <code>\(code)</code></p></section>
        <section><h2>3. Point, tilt, click</h2><p>On your Mac, enable AirMouse in Accessibility. Hold your phone screen-up near horizontal and tap Recenter. Tilt the top up/down to move vertically; tilt side to side to move horizontally. Tap the two pads to click.</p>
        <small>Local prototype · No account · No cloud connection<br>The local connection is not encrypted. Use a trusted private network.</small></section>
        </main></body></html>
        """
    }
}
