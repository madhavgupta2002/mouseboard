namespace MouseBoard.Windows;

/// <summary>Turns Bluetooth state into one plain-language next step, with a quotable code.</summary>
internal static class Diagnosis
{
    internal static string Describe(PairedDevice device) =>
        $"\"{device.Name}\" {device.AddressText} class=0x{device.ClassOfDevice:X6}{(device.IsPhone ? " (phone)" : "")} " +
        $"connected={device.Connected} authenticated={device.Authenticated} services=" +
        (device.Services is null ? "query failed" : device.Services.Count == 0 ? "none"
            : string.Join(", ", device.Services.Select(BluetoothDevices.ServiceName)));

    /// <summary>Explains the most likely problem in plain words, with a code users can quote.</summary>
    internal static string Diagnose(bool radio, IReadOnlyList<PairedDevice> paired, string pcName)
    {
        if (!radio) return "No Bluetooth adapter found or Bluetooth is off. Turn on Bluetooth in Windows Settings. [W2]";
        if (paired.Count == 0)
            return "No paired devices. On the phone open MouseBoard → Connect → Pair new computer, then on this PC choose Add device → Bluetooth. [W3]";
        PairedDevice? mouse = paired.FirstOrDefault(device => device.HasMouseService && device.Connected);
        if (mouse is not null) return $"\"{mouse.Name}\" is connected as a mouse and keyboard. Ready. [W0]";
        mouse = paired.FirstOrDefault(device => device.HasMouseService);
        if (mouse is not null)
            return $"\"{mouse.Name}\" is paired with the mouse service but not connected. Open MouseBoard on the phone, tap Connect and choose {pcName}. [W4]";
        PairedDevice? phone = paired.FirstOrDefault(device => device.IsPhone);
        if (phone is not null)
            return $"\"{phone.Name}\" is paired as a phone only — Windows has no mouse service for it, so the phone cannot control this PC. " +
                   "Fix: click Remove pairing below, then on the phone tap Connect → Pair new computer and add it from Add device → Bluetooth. [W5]";
        return "No phone with the MouseBoard mouse service is paired. On the phone tap Connect → Pair new computer, then Add device → Bluetooth here. [W6]";
    }
}
