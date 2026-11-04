// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AirMouse",
    platforms: [.macOS(.v14)],
    products: [.executable(name: "AirMouse", targets: ["AirMouse"])],
    targets: [
        .target(name: "AirMouseCore"),
        .executableTarget(name: "AirMouse", dependencies: ["AirMouseCore"],
                          linkerSettings: [.linkedFramework("AppKit"),
                                           .linkedFramework("ApplicationServices")]),
        .executableTarget(name: "AirMouseCoreChecks", dependencies: ["AirMouseCore"],
                          path: "Tests/AirMouseCoreChecks")
    ]
)
