using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace MouseBoard.Windows;

internal sealed record PairedDevice(string Name, ulong Address, bool Connected, uint ClassOfDevice)
{
    public bool IsPhone => ((ClassOfDevice >> 8) & 0x1f) == 2;
    public string DisplayName => $"{Name} · {(Connected ? "connected" : "paired")}";
}

internal static class BluetoothDevices
{
    public static IReadOnlyList<PairedDevice> Read()
    {
        var search = new SearchParameters
        {
            Size = (uint)Marshal.SizeOf<SearchParameters>(),
            ReturnAuthenticated = true,
            ReturnRemembered = true,
            ReturnConnected = true,
            IssueInquiry = false
        };
        var info = new DeviceInfo { Size = (uint)Marshal.SizeOf<DeviceInfo>() };
        using FindHandle handle = BluetoothFindFirstDevice(ref search, ref info);
        if (handle.IsInvalid)
        {
            int error = Marshal.GetLastWin32Error();
            if (error is 0 or 259) return Array.Empty<PairedDevice>();
            throw new Win32Exception(error);
        }

        var devices = new Dictionary<ulong, PairedDevice>();
        do
        {
            string name = string.IsNullOrWhiteSpace(info.Name) ? "Bluetooth device" : info.Name.Trim();
            devices[info.Address] = new PairedDevice(name, info.Address, info.Connected, info.ClassOfDevice);
            info.Size = (uint)Marshal.SizeOf<DeviceInfo>();
        } while (BluetoothFindNextDevice(handle, ref info));

        int finalError = Marshal.GetLastWin32Error();
        if (finalError is not (0 or 259)) throw new Win32Exception(finalError);
        return devices.Values.OrderByDescending(device => device.IsPhone)
            .ThenByDescending(device => device.Connected)
            .ThenBy(device => device.Name, StringComparer.OrdinalIgnoreCase).ToArray();
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct SearchParameters
    {
        public uint Size;
        [MarshalAs(UnmanagedType.Bool)] public bool ReturnAuthenticated;
        [MarshalAs(UnmanagedType.Bool)] public bool ReturnRemembered;
        [MarshalAs(UnmanagedType.Bool)] public bool ReturnUnknown;
        [MarshalAs(UnmanagedType.Bool)] public bool ReturnConnected;
        [MarshalAs(UnmanagedType.Bool)] public bool IssueInquiry;
        public byte TimeoutMultiplier;
        public IntPtr Radio;
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct SystemTime
    {
        public ushort Year, Month, DayOfWeek, Day, Hour, Minute, Second, Milliseconds;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    internal struct DeviceInfo
    {
        public uint Size;
        public ulong Address;
        public uint ClassOfDevice;
        [MarshalAs(UnmanagedType.Bool)] public bool Connected;
        [MarshalAs(UnmanagedType.Bool)] public bool Remembered;
        [MarshalAs(UnmanagedType.Bool)] public bool Authenticated;
        public SystemTime LastSeen;
        public SystemTime LastUsed;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 248)] public string Name;
    }

    private sealed class FindHandle : SafeHandleZeroOrMinusOneIsInvalid
    {
        private FindHandle() : base(true) { }
        protected override bool ReleaseHandle() => BluetoothFindDeviceClose(handle);
    }

    [DllImport("bthprops.cpl", SetLastError = true)]
    private static extern FindHandle BluetoothFindFirstDevice(ref SearchParameters parameters, ref DeviceInfo info);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool BluetoothFindNextDevice(FindHandle handle, ref DeviceInfo info);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool BluetoothFindDeviceClose(IntPtr handle);
}
