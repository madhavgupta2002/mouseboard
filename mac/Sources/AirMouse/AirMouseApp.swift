import SwiftUI
import CoreImage.CIFilterBuiltins

@main
struct AirMouseApp: App {
    @StateObject private var receiver = Receiver()

    var body: some Scene {
        WindowGroup {
            ReceiverView(receiver: receiver)
                .onAppear {
                    receiver.start()
                    NSApp.activate(ignoringOtherApps: true)
                    captureIfRequested()
                }
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.willTerminateNotification)) { _ in receiver.stop() }
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 820, height: 760)
        .windowResizability(.contentSize)
        MenuBarExtra("AirMouse", systemImage: "cursorarrow.motionlines") {
            Text(receiver.status)
            Button(receiver.enabled ? "Pause pointer" : "Resume pointer") { receiver.toggleEnabled() }
            Button("Show AirMouse") {
                NSApp.activate(ignoringOtherApps: true)
                NSApp.windows.first(where: { $0.canBecomeMain })?.makeKeyAndOrderFront(nil)
            }
            Divider()
            Button("Quit AirMouse") { NSApp.terminate(nil) }
        }
    }

    /// Developer screenshot of this app's own view, without screen-recording access.
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

private struct ReceiverView: View {
    @ObservedObject var receiver: Receiver
    private let mint = Color(red: 0.53, green: 0.92, blue: 0.77)
    private let subtle = Color(red: 0.64, green: 0.70, blue: 0.77)

    var body: some View {
        VStack(alignment: .leading, spacing: 26) {
            HStack {
                Label("AIR MOUSE", systemImage: "cursorarrow.motionlines")
                    .font(.system(size: 13, weight: .bold, design: .rounded)).tracking(2).foregroundColor(mint)
                Spacer()
                Circle().fill(receiver.connected ? mint : Color.orange).frame(width: 7, height: 7)
                Text(receiver.status).font(.system(size: 12)).foregroundColor(subtle)
            }
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("A new way\nto point.").font(.system(size: 48, weight: .semibold, design: .rounded)).tracking(-2)
                    Text("Turn your Galaxy into a motion mouse.").foregroundColor(subtle)
                }
                Spacer()
                Image(systemName: "iphone.gen3.radiowaves.left.and.right")
                    .font(.system(size: 70, weight: .ultraLight)).foregroundColor(mint)
                    .rotationEffect(.degrees(-15)).padding(.trailing, 30).padding(.bottom, 12)
            }
            HStack(alignment: .top, spacing: 22) {
                VStack(alignment: .leading, spacing: 16) {
                    sectionLabel("01", "GET THE PHONE APP")
                    if receiver.selectedAddress.isEmpty {
                        Text("Connect your Mac to Wi-Fi or Ethernet to show the installation link.")
                            .foregroundColor(subtle)
                    } else if receiver.installReady {
                        HStack(alignment: .top, spacing: 18) {
                            if let image = qr(receiver.installURL) {
                                Image(nsImage: image).interpolation(.none).resizable()
                                    .frame(width: 130, height: 130).padding(8).background(.white).cornerRadius(12)
                                    .accessibilityLabel("QR code for the Android installation page")
                            }
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Scan with your\nSamsung camera")
                                    .font(.system(size: 16, weight: .medium))
                                    .fixedSize(horizontal: false, vertical: true)
                                Text("Download the app, then return to the page to pair.")
                                    .font(.system(size: 12)).foregroundColor(subtle)
                                    .fixedSize(horizontal: false, vertical: true)
                            }.padding(.top, 8)
                        }
                        Text(receiver.installURL).font(.system(size: 12, design: .monospaced)).textSelection(.enabled)
                        Button("Copy install link", systemImage: "link") { copy(receiver.installURL) }
                    } else {
                        Text(receiver.installStatus.isEmpty ? "Starting install page…" : receiver.installStatus)
                            .foregroundColor(subtle).font(.system(size: 13))
                    }
                    Text("Both devices must be on the same trusted network.").font(.system(size: 11)).foregroundColor(subtle)
                }.frame(maxWidth: .infinity, alignment: .leading)
                Rectangle().fill(Color.white.opacity(0.1)).frame(width: 1)
                VStack(alignment: .leading, spacing: 16) {
                    sectionLabel("02", "PAIR & POINT")
                    Text("MAC ADDRESS").font(.system(size: 10, weight: .bold)).tracking(1).foregroundColor(subtle)
                    if receiver.addresses.count > 1 {
                        Picker("Network address", selection: $receiver.selectedAddress) {
                            ForEach(receiver.addresses, id: \.self) {
                                Text(($0.contains(":") ? "IPv6 · " : "IPv4 · ") + $0).tag($0)
                            }
                        }.labelsHidden().pickerStyle(.menu).frame(maxWidth: 285)
                        Text(receiver.selectedAddress)
                            .font(.system(size: 11, design: .monospaced))
                            .fixedSize(horizontal: false, vertical: true).textSelection(.enabled)
                    } else {
                        Text(receiver.selectedAddress.isEmpty ? "No network" : receiver.selectedAddress)
                            .font(.system(size: 20, weight: .medium, design: .monospaced)).textSelection(.enabled)
                    }
                    Text("PAIRING CODE").font(.system(size: 10, weight: .bold)).tracking(1).foregroundColor(subtle)
                    Text(formattedCode).font(.system(size: 23, weight: .medium, design: .monospaced))
                        .foregroundColor(mint).textSelection(.enabled)
                    HStack {
                        Button("Copy code") { copy(receiver.code) }
                        Button("New code") { receiver.rotateCode() }
                    }
                    if receiver.addresses.contains(where: { $0.contains(":") }) {
                        Text("Link unreachable? Choose IPv6 above and scan the updated QR.")
                            .font(.system(size: 11)).foregroundColor(subtle)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Text("Hold the phone screen-up near horizontal.\nTap Recenter, then tilt gently to move.").font(.system(size: 12)).foregroundColor(subtle)
                    Button("Refresh network", systemImage: "arrow.clockwise") { receiver.refreshAddresses() }
                        .font(.system(size: 11))
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(22).background(Color.white.opacity(0.035)).cornerRadius(20)
            HStack(spacing: 14) {
                Image(systemName: receiver.hasPermission ? "checkmark.shield" : "hand.raised")
                    .font(.system(size: 24)).foregroundColor(receiver.hasPermission ? mint : .orange)
                VStack(alignment: .leading, spacing: 4) {
                    Text(receiver.hasPermission ? "Ready for pointer control" : "Allow pointer control")
                        .font(.system(size: 14, weight: .semibold))
                    Text(receiver.hasPermission ? "Left and right taps click. Pause whenever you need." :
                         "Enable AirMouse in System Settings → Privacy & Security → Accessibility.")
                        .font(.system(size: 11)).foregroundColor(subtle)
                }
                Spacer()
                if !receiver.hasPermission {
                    Button("Open Settings") { receiver.mouse.requestPermission() }
                } else {
                    Button(receiver.enabled ? "Pause" : "Resume",
                           systemImage: receiver.enabled ? "pause.fill" : "play.fill") { receiver.toggleEnabled() }
                        .keyboardShortcut("p", modifiers: [.command, .shift])
                }
            }
            HStack {
                Text("LOCAL CONNECTION  ·  NO ACCOUNT  ·  MVP").font(.system(size: 9, weight: .medium)).tracking(1).foregroundColor(subtle)
                Spacer()
                if receiver.connected { Button("Disconnect phone") { receiver.disconnect() }.font(.system(size: 11)) }
            }
        }
        .buttonStyle(.bordered)
        .padding(.horizontal, 36).padding(.vertical, 30)
        .frame(width: 820)
        .background(Color(red: 0.047, green: 0.075, blue: 0.11))
        .foregroundColor(Color(red: 0.95, green: 0.97, blue: 0.99))
        .preferredColorScheme(.dark)
    }

    private var formattedCode: String {
        stride(from: 0, to: receiver.code.count, by: 4).map { start in
            String(receiver.code.dropFirst(start).prefix(4))
        }.joined(separator: " ")
    }

    private func sectionLabel(_ number: String, _ title: String) -> some View {
        HStack(spacing: 8) {
            Text(number).foregroundColor(mint)
            Text(title).foregroundColor(subtle)
        }.font(.system(size: 10, weight: .bold)).tracking(1)
    }

    private func copy(_ text: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
    }

    private func qr(_ text: String) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage,
              let image = CIContext().createCGImage(output.transformed(by: CGAffineTransform(scaleX: 8, y: 8)),
                                                    from: output.extent.applying(CGAffineTransform(scaleX: 8, y: 8))) else { return nil }
        return NSImage(cgImage: image, size: NSSize(width: image.width, height: image.height))
    }
}
