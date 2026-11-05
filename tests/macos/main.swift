import Foundation

var checks = 0
func check(_ condition: Bool, _ message: String) {
    precondition(condition, message)
    checks += 1
    print("PASS: \(message)")
}
func hex(_ text: String) -> [UInt8] {
    stride(from: 0, to: text.count, by: 2).map { offset in
        let start = text.index(text.startIndex, offsetBy: offset)
        return UInt8(text[start..<text.index(start, offsetBy: 2)], radix: 16)!
    }
}

// Captured on the affected Mac; matches the original mouse-only descriptor.
let legacy = hex("05010902a1010901a100050919012903150025019503750181029501750581030501093009311581257f750895028106c0c0")
let current = Array(try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
check(HidInputProfile.inspect(legacy) == .legacyMouse,
      "Detect the obsolete profile captured on the affected host")
check(HidInputProfile.inspect(current) == .mouseboard,
      "Accept the descriptor exported by the current Android controller")

// A numbered report interpreted through the old descriptor reads the button
// byte as X and horizontal movement as Y: the reported vertical-only failure.
let packet: [UInt8] = [1, 0, 40, UInt8(bitPattern: -20), 0]
check(Int8(bitPattern: packet[1]) == 0 && Int8(bitPattern: packet[2]) == 40,
      "Reproduce zero horizontal movement and shifted vertical movement")
check(Int8(bitPattern: packet[2]) == 40 && Int8(bitPattern: packet[3]) == -20,
      "The matching numbered profile decodes both axes correctly")
check(HidInputProfile.inspect(legacy + [0x26, 0x85, 0]) == .legacyMouse,
      "A report-ID byte inside item data cannot hide a legacy profile")
check(HidInputProfile.inspect([0x75]) == .other,
      "Reject truncated HID items")
check(HidInputProfile.inspect([]) == .other,
      "Do not classify an empty descriptor as a mouse")
check(HidInputProfile.inspect([0x85, 0]) == .other,
      "Reject an invalid explicit report ID")
check(HidInputProfile.inspect([0x05, 0x07, 0x75, 8, 0x95, 3, 0x81, 2]) == .other,
      "An unrelated three-byte input is not mistaken for the old mouse")
print("\(checks) HID profile regression checks passed.")
