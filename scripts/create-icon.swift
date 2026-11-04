import AppKit
import Foundation

let destination = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
let sizes = [(16, 1), (16, 2), (32, 1), (32, 2), (128, 1), (128, 2), (256, 1), (256, 2), (512, 1), (512, 2)]
for (size, scale) in sizes {
    let pixels = size * scale
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: pixels, pixelsHigh: pixels,
                                  bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true,
                                  isPlanar: false, colorSpaceName: .deviceRGB,
                                  bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    let factor = CGFloat(pixels) / 1024
    let transform = NSAffineTransform()
    transform.scale(by: factor)
    transform.concat()
    NSColor(red: 0.047, green: 0.075, blue: 0.11, alpha: 1).setFill()
    NSBezierPath(roundedRect: NSRect(x: 32, y: 32, width: 960, height: 960), xRadius: 220, yRadius: 220).fill()
    NSColor(red: 0.53, green: 0.92, blue: 0.77, alpha: 1).setFill()
    let arrow = NSBezierPath()
    arrow.move(to: NSPoint(x: 296, y: 782))
    arrow.line(to: NSPoint(x: 760, y: 340))
    arrow.line(to: NSPoint(x: 547, y: 339))
    arrow.line(to: NSPoint(x: 457, y: 126))
    arrow.close()
    arrow.fill()
    NSColor(red: 0.047, green: 0.075, blue: 0.11, alpha: 1).setFill()
    let inside = NSBezierPath()
    inside.move(to: NSPoint(x: 385, y: 608))
    inside.line(to: NSPoint(x: 590, y: 410))
    inside.line(to: NSPoint(x: 495, y: 406))
    inside.line(to: NSPoint(x: 455, y: 310))
    inside.close()
    inside.fill()
    NSGraphicsContext.restoreGraphicsState()
    let filename = "icon_\(size)x\(size)" + (scale == 2 ? "@2x" : "") + ".png"
    try bitmap.representation(using: .png, properties: [:])!.write(to: destination.appendingPathComponent(filename))
}
