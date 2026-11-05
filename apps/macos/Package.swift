// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MouseBoard",
    platforms: [.macOS(.v14)],
    products: [.executable(name: "MouseBoard", targets: ["MouseBoard"])],
    targets: [
        .executableTarget(name: "MouseBoard",
                          linkerSettings: [.linkedFramework("AppKit"),
                                           .linkedFramework("IOBluetooth")])
    ]
)
