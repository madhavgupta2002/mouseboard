import SwiftUI

@main
struct AirMouseApp: App {
    @StateObject private var setup = BluetoothSetup()

    var body: some Scene {
        WindowGroup {
            BluetoothSetupView(setup: setup)
                .onAppear {
                    setup.start()
                    NSApp.activate(ignoringOtherApps: true)
                    if CommandLine.arguments.contains("--diagnostics"),
                       let data = try? JSONSerialization.data(withJSONObject: setup.diagnostics(), options: [.prettyPrinted, .sortedKeys]) {
                        print(String(decoding: data, as: UTF8.self))
                        fflush(stdout)
                        NSApp.terminate(nil)
                    }
                    captureIfRequested()
                }
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.willTerminateNotification)) { _ in setup.stop() }
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 800, height: 650)
        .windowResizability(.contentSize)
        MenuBarExtra("AirMouse Bluetooth", systemImage: "cursorarrow.motionlines") {
            Text("Bluetooth mouse setup")
            Button("Bluetooth settings") { setup.openSettings() }
            Button("Show AirMouse") {
                NSApp.activate(ignoringOtherApps: true)
                NSApp.windows.first(where: { $0.canBecomeMain })?.makeKeyAndOrderFront(nil)
            }
            Divider()
            Button("Quit setup helper") { NSApp.terminate(nil) }
        }
    }

    private func captureIfRequested() {
        guard let index = CommandLine.arguments.firstIndex(of: "--screenshot"),
              CommandLine.arguments.indices.contains(index + 1) else { return }
        let path = CommandLine.arguments[index + 1]
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
            guard let view = NSApp.windows.first(where: { $0.canBecomeMain })?.contentView,
                  let rep = view.bitmapImageRepForCachingDisplay(in: view.bounds) else { return }
            view.cacheDisplay(in: view.bounds, to: rep)
            try? rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: path))
        }
    }
}

private struct BluetoothSetupView: View {
    @ObservedObject var setup: BluetoothSetup
    private let mint = Color(red: 0.53, green: 0.92, blue: 0.77)
    private let subtle = Color(red: 0.64, green: 0.70, blue: 0.77)

    var body: some View {
        VStack(alignment: .leading, spacing: 25) {
            HStack {
                Label("AIR MOUSE", systemImage: "cursorarrow.motionlines")
                    .font(.system(size: 13, weight: .bold, design: .rounded)).tracking(2).foregroundColor(mint)
                Spacer()
                Circle().fill(setup.bluetoothOn ? mint : Color.orange).frame(width: 7, height: 7)
                Text(setup.bluetoothOn ? "Bluetooth is on" : "Turn on Bluetooth")
                    .font(.system(size: 12)).foregroundColor(subtle)
            }
            HStack {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Point without\nWi-Fi.").font(.system(size: 47, weight: .semibold, design: .rounded)).tracking(-2)
                    Text("Your Galaxy becomes a standard Bluetooth mouse.")
                        .font(.system(size: 14)).foregroundColor(subtle)
                }
                Spacer()
                Image(systemName: "computermouse")
                    .font(.system(size: 73, weight: .ultraLight)).foregroundColor(mint)
                    .rotationEffect(.degrees(-12)).padding(.trailing, 28)
            }
            HStack(alignment: .top, spacing: 24) {
                VStack(alignment: .leading, spacing: 15) {
                    section("01", "INSTALL ON YOUR GALAXY")
                    if setup.devices.isEmpty {
                        Text("Pair your phone in Mac Bluetooth settings. Allow Bluetooth access for AirMouse if macOS asks.")
                            .font(.system(size: 13)).foregroundColor(subtle)
                    } else {
                        Picker("Paired phone", selection: $setup.selectedAddress) {
                            ForEach(setup.devices) { Text($0.name).tag($0.id) }
                        }.labelsHidden().frame(maxWidth: 305)
                        if let phone = setup.selectedPhone {
                            Label(phone.connected ? "System Bluetooth link connected" : "Paired with macOS",
                                  systemImage: phone.connected ? "checkmark.circle.fill" : "checkmark.circle")
                                .font(.system(size: 11)).foregroundColor(mint)
                        }
                    }
                    Button {
                        setup.sendInstaller()
                    } label: {
                        Label(setup.sending ? "Sending installer…" : "Send APK over Bluetooth", systemImage: "paperplane")
                    }.disabled(setup.selectedPhone == nil || !setup.bluetoothOn || setup.sending)
                    Text(setup.transferStatus).font(.system(size: 12)).foregroundColor(subtle)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Accept the file on your phone, open the APK, and allow installation when Android asks.")
                        .font(.system(size: 12)).foregroundColor(subtle)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack {
                        Button("Show APK in Finder") { setup.revealInstaller() }
                        Button("Bluetooth settings") { setup.openSettings() }
                    }.font(.system(size: 11))
                }.frame(maxWidth: .infinity, alignment: .leading)
                Rectangle().fill(Color.white.opacity(0.1)).frame(width: 1)
                VStack(alignment: .leading, spacing: 17) {
                    section("02", "CONNECT & POINT")
                    instruction("1", "Open AirMouse on your phone.",
                                "Allow Nearby devices when asked.")
                    instruction("2", "Tap Connect over Bluetooth.",
                                "Choose \(setup.macName) from the paired-device list.")
                    instruction("3", "Hold the phone screen-up.",
                                "Tap Recenter, then tilt gently. Tap LEFT or RIGHT to click.")
                    Text("Keep the phone app open. Pause or disconnect from the phone.")
                        .font(.system(size: 12)).foregroundColor(subtle)
                        .fixedSize(horizontal: false, vertical: true)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }.padding(22).background(Color.white.opacity(0.035)).cornerRadius(20)
            HStack(spacing: 14) {
                Image(systemName: "checkmark.shield").font(.system(size: 24)).foregroundColor(mint)
                VStack(alignment: .leading, spacing: 5) {
                    Text("macOS receives mouse input directly.").font(.system(size: 14, weight: .semibold))
                    Text("No desktop receiver or Accessibility permission needed. You can quit this helper after installation.")
                        .font(.system(size: 11)).foregroundColor(subtle)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Text("BLUETOOTH ONLY  ·  NO NETWORK CONNECTION  ·  NO ACCOUNT")
                .font(.system(size: 9, weight: .medium)).tracking(1).foregroundColor(subtle)
        }
        .buttonStyle(.bordered)
        .padding(.horizontal, 34).padding(.vertical, 30)
        .frame(width: 800)
        .background(Color(red: 0.047, green: 0.075, blue: 0.11))
        .foregroundColor(Color(red: 0.95, green: 0.97, blue: 0.99))
        .preferredColorScheme(.dark)
    }

    private func section(_ number: String, _ title: String) -> some View {
        HStack(spacing: 8) {
            Text(number).foregroundColor(mint)
            Text(title).foregroundColor(subtle)
        }.font(.system(size: 10, weight: .bold)).tracking(1)
    }

    private func instruction(_ number: String, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(number).font(.system(size: 12, weight: .semibold)).foregroundColor(mint)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.system(size: 13, weight: .medium))
                Text(detail).font(.system(size: 12)).foregroundColor(subtle)
            }.fixedSize(horizontal: false, vertical: true)
        }
    }
}
