import Foundation
import CoreGraphics

public struct WireMessage: Decodable {
    public let type: String
    public let version: Int?
    public let code: String?
    public let dx: Double?
    public let dy: Double?
    public let button: String?

    public static func decode(_ data: Data) throws -> WireMessage {
        try JSONDecoder().decode(WireMessage.self, from: data)
    }

    public var movement: (Double, Double)? {
        guard type == "move", let x = dx, let y = dy,
              x.isFinite, y.isFinite, abs(x) <= 250, abs(y) <= 250 else { return nil }
        return (x, y)
    }
}

/// Bounds every line, including a partial line from a misbehaving peer.
public struct LineBuffer {
    private var pending = Data()
    public init() {}
    public mutating func append(_ data: Data) throws -> [Data] {
        pending.append(data)
        var result: [Data] = []
        while let index = pending.firstIndex(of: 10) {
            guard index <= 4096 else { throw ProtocolError.oversized }
            result.append(pending.prefix(upTo: index))
            pending.removeSubrange(...index)
        }
        guard pending.count <= 4096 else { throw ProtocolError.oversized }
        return result
    }
}

public enum ProtocolError: Error { case oversized }

public enum LocalAddress {
    public static func httpHost(_ address: String) -> String {
        address.contains(":") ? "[\(address)]" : address
    }
}

public enum PointerMath {
    /// Screen bounds use Quartz's global, top-left coordinate space.
    /// Choose the nearest display when a proposed position falls into a gap.
    public static func constrain(_ point: CGPoint, to displays: [CGRect]) -> CGPoint {
        if displays.contains(where: { $0.contains(point) }) { return point }
        return displays.map { rect -> CGPoint in
            CGPoint(x: min(max(point.x, rect.minX), rect.maxX - 1),
                    y: min(max(point.y, rect.minY), rect.maxY - 1))
        }.min {
            hypot($0.x - point.x, $0.y - point.y) < hypot($1.x - point.x, $1.y - point.y)
        } ?? point
    }
}
