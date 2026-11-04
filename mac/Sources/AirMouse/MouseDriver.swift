import AppKit
import ApplicationServices
import AirMouseCore

final class MouseDriver {
    private let source = CGEventSource(stateID: .hidSystemState)
    private var lastClick: (button: String, time: TimeInterval, point: CGPoint)?
    let diagnostic: Bool

    init(diagnostic: Bool) { self.diagnostic = diagnostic }

    var hasPermission: Bool { diagnostic || AXIsProcessTrusted() }

    func requestPermission() {
        let key = kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String
        _ = AXIsProcessTrustedWithOptions([key: true] as CFDictionary)
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility") {
            NSWorkspace.shared.open(url)
        }
    }

    func move(dx: Double, dy: Double) {
        guard hasPermission else { return }
        if diagnostic {
            print("EVENT move \(dx) \(dy)")
            fflush(stdout)
            return
        }
        guard let current = CGEvent(source: nil)?.location else { return }
        var displays = [CGDirectDisplayID](repeating: 0, count: 32)
        var count: UInt32 = 0
        guard CGGetActiveDisplayList(UInt32(displays.count), &displays, &count) == .success else { return }
        let bounds = displays.prefix(Int(count)).map { CGDisplayBounds($0) }
        let target = PointerMath.constrain(CGPoint(x: current.x + dx, y: current.y + dy), to: bounds)
        CGEvent(mouseEventSource: source, mouseType: .mouseMoved, mouseCursorPosition: target,
                mouseButton: .left)?.post(tap: .cghidEventTap)
    }

    func click(button: String) {
        guard hasPermission, button == "left" || button == "right" else { return }
        if diagnostic {
            print("EVENT click \(button)")
            fflush(stdout)
            return
        }
        guard let point = CGEvent(source: nil)?.location else { return }
        let right = button == "right"
        let mouseButton: CGMouseButton = right ? .right : .left
        let now = ProcessInfo.processInfo.systemUptime
        let isDouble: Bool
        if let last = lastClick {
            isDouble = last.button == button && now-last.time <= NSEvent.doubleClickInterval &&
                hypot(last.point.x-point.x, last.point.y-point.y) < 5
        } else { isDouble = false }
        let count: Int64 = isDouble ? 2 : 1
        for kind: CGEventType in [right ? .rightMouseDown : .leftMouseDown,
                                 right ? .rightMouseUp : .leftMouseUp] {
            let event = CGEvent(mouseEventSource: source, mouseType: kind,
                                mouseCursorPosition: point, mouseButton: mouseButton)
            event?.setIntegerValueField(.mouseEventClickState, value: count)
            event?.post(tap: .cghidEventTap)
        }
        lastClick = isDouble ? nil : (button, now, point)
    }
}
