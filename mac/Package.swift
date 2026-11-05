// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AirMouse",
    platforms: [.macOS(.v14)],
    products: [.executable(name: "AirMouse", targets: ["AirMouse"])],
    targets: [
        .executableTarget(name: "AirMouse",
                          linkerSettings: [.linkedFramework("AppKit"),
                                           .linkedFramework("IOBluetooth")])
    ]
)
