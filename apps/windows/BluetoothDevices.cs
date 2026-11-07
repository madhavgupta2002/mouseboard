using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace MouseBoard.Windows;

internal sealed record PairedDevice(string Name, ulong Address, bool Connected, uint ClassOfDevice,
    bool Authenticated = true, IReadOnlyList<Guid>? Services = null)
{
    public bool IsPhone => ((ClassOfDevice >> 8) & 0x1f) == 2;
    /// <summary>Windows installed the HID (mouse/keyboard) service for this device.</summary>
    public bool HasMouseService => Services?.Contains(BluetoothDevices.HidService) == true;
    public string AddressText => string.Join(":", BitConverter.GetBytes(Address).Take(6).Reverse().Select(b => b.ToString("X2")));
    public string DisplayName => $"{Name} · {(Connected ? "connected" : "paired")}" +
        (Services is null ? "" : HasMouseService ? " · mouse service ✓" : " · no mouse service");
}

internal static class BluetoothDevices
{
    public static readonly Guid HidService = new("00001124-0000-1000-8000-00805f9b34fb");

    private static readonly Dictionary<Guid, string> ServiceNames = new()
    {
        [HidService] = "HID mouse/keyboard",
        [new("0000110a-0000-1000-8000-00805f9b34fb")] = "Audio source",
        [new("0000110b-0000-1000-8000-00805f9b34fb")] = "Audio sink",
        [new("0000110c-0000-1000-8000-00805f9b34fb")] = "Remote control target",
        [new("0000110e-0000-1000-8000-00805f9b34fb")] = "Remote control",
        [new("0000111f-0000-1000-8000-00805f9b34fb")] = "Hands-free gateway",
        [new("00001112-0000-1000-8000-00805f9b34fb")] = "Headset gateway",
        [new("00001105-0000-1000-8000-00805f9b34fb")] = "File transfer (OBEX push)",
        [new("00001116-0000-1000-8000-00805f9b34fb")] = "Network access point",
        [new("00001115-0000-1000-8000-00805f9b34fb")] = "PANU",
        [new("00001132-0000-1000-8000-00805f9b34fb")] = "Message access",
        [new("0000112f-0000-1000-8000-00805f9b34fb")] = "Phonebook access",
    };

    public static string ServiceName(Guid service) => ServiceNames.TryGetValue(service, out string? name) ? name : service.ToString();

    public static bool HasRadio()
    {
        var parameters = new RadioSearch { Size = (uint)Marshal.SizeOf<RadioSearch>() };
        IntPtr find = BluetoothFindFirstRadio(ref parameters, out IntPtr radio);
        if (find == IntPtr.Zero) return false;
        CloseHandle(radio);
        BluetoothFindRadioClose(find);
        return true;
    }

    /// <summary>Services Windows enabled for the device (empty if none, null if the query failed).</summary>
    public static IReadOnlyList<Guid>? InstalledServices(ulong address)
    {
        var info = new DeviceInfo { Size = (uint)Marshal.SizeOf<DeviceInfo>(), Address = address };
        var services = new Guid[32];
        uint count = (uint)services.Length;
        uint result = BluetoothEnumerateInstalledServices(IntPtr.Zero, ref info, ref count, services);
        if (result is not (0 or 234)) return null;
        return services.Take((int)Math.Min(count, (uint)services.Length)).ToArray();
    }

    /// <summary>Removes the Windows pairing so the phone can be paired again with its mouse service.</summary>
    public static uint Remove(ulong address) => BluetoothRemoveDevice(ref address);

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
            devices[info.Address] = new PairedDevice(name, info.Address, info.Connected, info.ClassOfDevice,
                info.Authenticated, InstalledServices(info.Address));
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
    internal struct RadioSearch { public uint Size; }

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

    [DllImport("bthprops.cpl", SetLastError = true)]
    private static extern uint BluetoothEnumerateInstalledServices(IntPtr radio, ref DeviceInfo info,
        ref uint count, [Out] Guid[] services);

    [DllImport("bthprops.cpl", SetLastError = true)]
    private static extern uint BluetoothRemoveDevice(ref ulong address);

    [DllImport("bthprops.cpl", SetLastError = true)]
    private static extern IntPtr BluetoothFindFirstRadio(ref RadioSearch parameters, out IntPtr radio);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool BluetoothFindRadioClose(IntPtr find);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseHandle(IntPtr handle);
}
