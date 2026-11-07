using System.Runtime.InteropServices;
using System.Reflection.Metadata;
using System.Reflection.PortableExecutable;
using MouseBoard.Windows;

static void Check(bool condition, string message)
{
    if (!condition) throw new InvalidOperationException(message);
    Console.WriteLine("PASS: " + message);
}

Check(Marshal.SizeOf<BluetoothDevices.DeviceInfo>() == 560,
    "Bluetooth device structure matches the Windows Unicode ABI");
Check(Marshal.OffsetOf<BluetoothDevices.DeviceInfo>(nameof(BluetoothDevices.DeviceInfo.Name)).ToInt32() == 64,
    "Bluetooth device name starts at the Windows ABI offset");
Check(Marshal.SizeOf<BluetoothDevices.SearchParameters>() == (IntPtr.Size == 8 ? 40 : 32),
    "Bluetooth search structure matches pointer alignment");
Check(new PairedDevice("Phone", 1, true, 0x0200).IsPhone &&
      !new PairedDevice("Headphones", 2, false, 0x0400).IsPhone,
    "Phone sorting distinguishes Bluetooth device classes");

string temporary = Path.Combine(Path.GetTempPath(), "mouseboard-check-" + Guid.NewGuid().ToString("N"));
try
{
    Directory.CreateDirectory(temporary);
    Check(InstallerPaths.Find(temporary) is null, "Missing installer is reported");
    File.WriteAllText(Path.Combine(temporary, "Other.apk"), "unrelated");
    Check(InstallerPaths.Find(temporary) is null, "Unrelated APKs are never selected");
    string adjacent = Path.Combine(temporary, "MouseBoard-Android.apk");
    File.WriteAllText(adjacent, "adjacent");
    Check(InstallerPaths.Find(temporary) == adjacent, "Adjacent installer is supported");
    string assets = Path.Combine(temporary, "Assets");
    Directory.CreateDirectory(assets);
    string bundled = Path.Combine(assets, "MouseBoard-Android.apk");
    File.WriteAllText(bundled, "bundled");
    Check(InstallerPaths.Find(temporary) == bundled, "Bundled installer takes precedence");
    byte[] apk = [0x50, 0x4b, 0x03, 0x04, 1, 2, 3];
    string installer = InstallerPaths.Extract(new MemoryStream(apk), Path.Combine(temporary, "per-user"));
    Check(File.ReadAllBytes(installer).SequenceEqual(apk), "Embedded installer extracts intact into per-user storage");
    Check(InstallerPaths.Extract(new MemoryStream(apk), Path.Combine(temporary, "per-user")) == installer,
        "Repeated launches reuse the same versioned installer");
    File.WriteAllText(installer, "corrupted");
    InstallerPaths.Extract(new MemoryStream(apk), Path.Combine(temporary, "per-user"));
    Check(File.ReadAllBytes(installer).SequenceEqual(apk), "Corrupted extracted installer is repaired");
    string updated = InstallerPaths.Extract(new MemoryStream([9, 8, 7]), Path.Combine(temporary, "per-user"));
    Check(updated != installer && File.ReadAllBytes(installer).SequenceEqual(apk),
        "App updates use distinct installer paths without overwriting the previous version");
    Check(!Directory.EnumerateFiles(temporary, "*.tmp", SearchOption.AllDirectories).Any(),
        "Extraction leaves no temporary files");
}
finally { Directory.Delete(temporary, recursive: true); }

if (args.Length == 2)
{
    using var file = File.OpenRead(args[0]);
    using var companion = new PEReader(file);
    MetadataReader metadata = companion.GetMetadataReader();
    ManifestResource resource = metadata.ManifestResources.Select(metadata.GetManifestResource)
        .Single(item => metadata.GetString(item.Name) == "MouseBoard.Android.apk");
    Check(resource.Implementation.IsNil, "Android installer is embedded, not an external assembly reference");
    BlobReader contents = companion.GetSectionData(companion.PEHeaders.CorHeader!.ResourcesDirectory.RelativeVirtualAddress)
        .GetReader();
    contents.Offset = checked((int)resource.Offset);
    byte[] embedded = contents.ReadBytes(contents.ReadInt32());
    Check(embedded.SequenceEqual(File.ReadAllBytes(args[1])),
        "Published Windows companion embeds the exact current Android installer");
}
