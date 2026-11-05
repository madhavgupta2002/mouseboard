import Foundation
import IOKit

enum HidInputProfile: String {
    case notConnected, legacyMouse, mouseboard, other

    /// Parse HID short items, not raw byte matches: item data may contain 0x85.
    static func inspect(_ descriptor: [UInt8]) -> HidInputProfile {
        struct Globals {
            var size = 0, count = 0, report = 0, page = 0
        }
        var state = Globals()
        var stack: [Globals] = []
        var inputs: [Int: Int] = [:]
        var offset = 0
        var mouse = false
        var numbered = false
        while offset < descriptor.count {
            let prefix = Int(descriptor[offset])
            offset += 1
            if prefix == 0xfe {
                guard offset + 2 <= descriptor.count else { return .other }
                let length = Int(descriptor[offset])
                offset += 2
                guard offset + length <= descriptor.count else { return .other }
                offset += length
                continue
            }
            let length = prefix & 3 == 3 ? 4 : prefix & 3
            guard offset + length <= descriptor.count else { return .other }
            var value = 0
            for byte in 0..<length { value |= Int(descriptor[offset + byte]) << (8 * byte) }
            offset += length
            let type = (prefix >> 2) & 3, tag = prefix >> 4
            if type == 1 {
                switch tag {
                case 0: state.page = value
                case 7: state.size = value
                case 8:
                    guard value > 0 && value < 256 else { return .other }
                    state.report = value
                    numbered = true
                case 9: state.count = value
                case 10: stack.append(state)
                case 11:
                    guard let previous = stack.popLast() else { return .other }
                    state = previous
                default: break
                }
            } else if type == 2 && tag == 0 && state.page == 1 && value == 2 {
                mouse = true
            } else if type == 0 && tag == 8 {
                guard state.size <= 64 && state.count <= 1024 else { return .other }
                inputs[state.report, default: 0] += state.size * state.count
            }
        }
        guard mouse, stack.isEmpty else { return .other }
        if !numbered && inputs == [0: 24] { return .legacyMouse }
        if inputs == [1: 32, 2: 64, 3: 32] { return .mouseboard }
        return .other
    }

    /// Registry inspection does not capture input or require Input Monitoring.
    static func current(for address: String) -> HidInputProfile {
        let expected = normalized(address)
        guard !expected.isEmpty else { return .notConnected }
        var iterator: io_iterator_t = 0
        guard IOServiceGetMatchingServices(kIOMainPortDefault,
                IOServiceMatching("IOHIDDevice"), &iterator) == KERN_SUCCESS else { return .other }
        defer { IOObjectRelease(iterator) }
        while case let service = IOIteratorNext(iterator), service != 0 {
            defer { IOObjectRelease(service) }
            func property(_ name: String) -> AnyObject? {
                IORegistryEntryCreateCFProperty(service, name as CFString,
                                               kCFAllocatorDefault, 0)?.takeRetainedValue()
            }
            guard property("Transport") as? String == "Bluetooth",
                  let serial = property("SerialNumber") as? String,
                  normalized(serial) == expected else { continue }
            guard let data = property("ReportDescriptor") as? Data else { return .other }
            return inspect(Array(data))
        }
        return .notConnected
    }

    private static func normalized(_ address: String) -> String {
        address.uppercased().filter { $0.isHexDigit }
    }
}
