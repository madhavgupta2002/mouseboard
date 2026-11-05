import AppKit
import IOBluetooth

struct PairedPhone: Identifiable {
    let id: String
    let name: String
    let connected: Bool
}

final class BluetoothSetup: ObservableObject {
    @Published var devices: [PairedPhone] = []
    @Published var selectedAddress = ""
    @Published var bluetoothOn = false
    @Published var transferStatus = "The installer is sent directly over Bluetooth."
    @Published var sending = false
    let macName = Host.current().localizedName ?? "this Mac"
    private var timer: Timer?

    var selectedPhone: PairedPhone? { devices.first { $0.id == selectedAddress } }
    var apkURL: URL? { Bundle.main.url(forResource: "MouseBoard-Android", withExtension: "apk") }

    func start() {
        refresh()
        guard timer == nil else { return }
        timer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self] _ in self?.refresh() }
    }

    func stop() { timer?.invalidate(); timer = nil }

    func refresh() {
        bluetoothOn = IOBluetoothHostController.default()?.powerState.rawValue == 1
        let paired = IOBluetoothDevice.pairedDevices() as? [IOBluetoothDevice] ?? []
        var unique: [String: PairedPhone] = [:]
        for device in paired {
            guard let address = device.addressString else { continue }
            let id = address.replacingOccurrences(of: "-", with: ":").uppercased()
            let connected = device.isConnected() || unique[id]?.connected == true
            unique[id] = PairedPhone(id: id, name: device.nameOrAddress ?? address,
                                     connected: connected)
        }
        devices = unique.values.sorted {
            let aPhone = $0.name.localizedCaseInsensitiveContains("S24") ||
                $0.name.localizedCaseInsensitiveContains("Galaxy")
            let bPhone = $1.name.localizedCaseInsensitiveContains("S24") ||
                $1.name.localizedCaseInsensitiveContains("Galaxy")
            if aPhone != bPhone { return aPhone }
            return $0.name < $1.name
        }
        if !devices.contains(where: { $0.id == selectedAddress }) {
            selectedAddress = devices.first?.id ?? ""
        }
    }

    func openSettings() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.BluetoothSettings") {
            NSWorkspace.shared.open(url)
        }
    }

    func revealInstaller() {
        guard let apkURL else { transferStatus = "Installer missing. Rebuild with scripts/build-all.sh."; return }
        NSWorkspace.shared.activateFileViewerSelecting([apkURL])
    }

    func sendInstaller() {
        guard let apkURL, let phone = selectedPhone else {
            transferStatus = "Pair and select your phone first."; return
        }
        guard !sending else { return }
        sending = true
        transferStatus = "Sending to \(phone.name)… Accept the file on your phone."
        let script = """
        on run argv
            tell application "Bluetooth File Exchange"
                activate
                send file (POSIX file (item 1 of argv)) to device (item 2 of argv)
            end tell
        end run
        """
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
        process.arguments = ["-e", script, apkURL.path, phone.id]
        let errors = Pipe()
        process.standardError = errors
        process.standardOutput = Pipe()
        process.terminationHandler = { [weak self] task in
            DispatchQueue.main.async {
                guard let self else { return }
                self.sending = false
                if task.terminationStatus == 0 {
                    self.transferStatus = "Transfer requested. Accept the file, then open it on your phone."
                } else {
                    self.transferStatus = "Use File Exchange to select your phone, then accept the transfer."
                    self.openFileExchange(apkURL)
                }
            }
        }
        do { try process.run() }
        catch {
            sending = false
            transferStatus = "Select your phone in Bluetooth File Exchange."
            openFileExchange(apkURL)
        }
    }

    private func openFileExchange(_ apk: URL) {
        let app = URL(fileURLWithPath: "/System/Applications/Utilities/Bluetooth File Exchange.app")
        NSWorkspace.shared.open([apk], withApplicationAt: app,
                                configuration: NSWorkspace.OpenConfiguration()) { _, _ in }
    }

    func diagnostics() -> [String: Any] {
        refresh()
        return [
            "transport": "Bluetooth HID",
            "networkReceiver": false,
            "bluetoothOn": bluetoothOn,
            "mac": macName,
            "installerPresent": apkURL != nil,
            "devices": devices.map { ["name": $0.name, "address": $0.id, "connected": $0.connected] as [String: Any] }
        ]
    }
}
