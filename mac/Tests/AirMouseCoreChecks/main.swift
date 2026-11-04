import Foundation
import CoreGraphics
import AirMouseCore

var checks = 0
func check(_ condition: @autoclosure () throws -> Bool, _ label: String) throws {
    guard try condition() else { fatalError("FAIL: \(label)") }
    checks += 1
    print("PASS: \(label)")
}
func mustThrow(_ label: String, _ action: () throws -> Void) {
    do { try action() } catch { checks += 1; print("PASS: \(label)"); return }
    fatalError("FAIL: \(label)")
}

var buffer = LineBuffer()
try check(try buffer.append(Data("{\"type\":".utf8)).isEmpty, "partial frame waits for newline")
let lines = try buffer.append(Data("\"heartbeat\"}\n{\"type\":\"click\",\"button\":\"left\"}\n".utf8))
try check(lines.count == 2, "batched frames are separated")
try check(try WireMessage.decode(lines[1]).button == "left", "click payload decoded")
mustThrow("oversized partial frame rejected") {
    var input = LineBuffer()
    _ = try input.append(Data(repeating: 65, count: 4097))
}
mustThrow("oversized complete frame rejected") {
    var input = LineBuffer()
    _ = try input.append(Data((String(repeating: "a", count: 4097) + "\n").utf8))
}
try check(try WireMessage.decode(Data(#"{"type":"move","dx":-2.5,"dy":3}"#.utf8)).movement != nil, "normal movement accepted")
try check(try WireMessage.decode(Data(#"{"type":"move","dx":251,"dy":3}"#.utf8)).movement == nil, "extreme movement rejected")
try check(try WireMessage.decode(Data(#"{"type":"move","dx":1}"#.utf8)).movement == nil, "incomplete movement rejected")
try check(try WireMessage.decode(Data(#"{"type":"click","dx":1,"dy":1}"#.utf8)).movement == nil, "non-movement command rejected")
mustThrow("malformed JSON rejected") { _ = try WireMessage.decode(Data("bad".utf8)) }
let displays = [CGRect(x: 0, y: 0, width: 100, height: 100),
                CGRect(x: -100, y: -40, width: 100, height: 80)]
try check(PointerMath.constrain(CGPoint(x: -20, y: 20), to: displays) == CGPoint(x: -20, y: 20), "negative display origin supported")
try check(PointerMath.constrain(CGPoint(x: 110, y: 120), to: displays) == CGPoint(x: 99, y: 99), "pointer clamped to display edge")
try check(PointerMath.constrain(CGPoint(x: -5, y: 60), to: displays) == CGPoint(x: 0, y: 60), "gap projects to nearest display")
try check(LocalAddress.httpHost("192.168.1.2") == "192.168.1.2", "IPv4 URL host unchanged")
try check(LocalAddress.httpHost("2403::1") == "[2403::1]", "IPv6 URL host bracketed")
print("\(checks) core checks passed.")
