using System.Runtime.InteropServices;
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
}
finally { Directory.Delete(temporary, recursive: true); }
