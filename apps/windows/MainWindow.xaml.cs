using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace MouseBoard.Windows;

public partial class MainWindow : Window
{
    private readonly string? installer = InstallerPaths.Find(AppContext.BaseDirectory);
    private readonly DispatcherTimer refreshTimer = new() { Interval = TimeSpan.FromSeconds(10) };
    private bool readingDevices;

    public MainWindow()
    {
        InitializeComponent();
        PcNameText.Text = Environment.MachineName;
        InstallerPathBox.Text = installer ?? "Installer missing. Use a complete MouseBoard Windows bundle.";
        SendButton.IsEnabled = ShowButton.IsEnabled = CopyButton.IsEnabled = installer is not null;
        refreshTimer.Tick += async (_, _) => await RefreshDevices();
        Loaded += async (_, _) => { await RefreshDevices(); refreshTimer.Start(); };
        Closed += (_, _) => refreshTimer.Stop();
    }

    private async Task RefreshDevices()
    {
        if (readingDevices) return;
        readingDevices = true;
        RefreshButton.IsEnabled = false;
        ulong? selected = (Devices.SelectedItem as PairedDevice)?.Address;
        try
        {
            IReadOnlyList<PairedDevice> paired = await Task.Run(BluetoothDevices.Read);
            Devices.ItemsSource = paired;
            Devices.SelectedItem = paired.FirstOrDefault(device => device.Address == selected)
                ?? paired.FirstOrDefault();
            DeviceStatus.Text = paired.Count == 0
                ? "No paired devices found. Pair your phone in Bluetooth settings."
                : "System pairing shown here; connect the mouse from the phone app.";
        }
        catch (Exception error) when (error is Win32Exception or DllNotFoundException or EntryPointNotFoundException)
        {
            DeviceStatus.Text = "Could not read Bluetooth devices. Use Bluetooth settings to check pairing.";
        }
        finally
        {
            readingDevices = false;
            RefreshButton.IsEnabled = true;
        }
    }

    private async void RefreshClicked(object sender, RoutedEventArgs e) => await RefreshDevices();

    private void SettingsClicked(object sender, RoutedEventArgs e) =>
        Run(() => Process.Start(new ProcessStartInfo("ms-settings:bluetooth") { UseShellExecute = true }),
            "Bluetooth settings opened. Pair your phone, then return here.");

    private void SendClicked(object sender, RoutedEventArgs e)
    {
        if (installer is null) return;
        string senderPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "fsquirt.exe");
        Run(() => Process.Start(new ProcessStartInfo(senderPath, "-send") { UseShellExecute = true }),
            "Bluetooth sender opened. Choose your phone, browse to the APK path above, and accept the file on your phone.");
    }

    private void ShowClicked(object sender, RoutedEventArgs e)
    {
        if (installer is null) return;
        Run(() => Process.Start(new ProcessStartInfo("explorer.exe", $"/select,\"{installer}\"")
            { UseShellExecute = true }), "Installer selected in Explorer.");
    }

    private void CopyClicked(object sender, RoutedEventArgs e)
    {
        if (installer is null) return;
        try
        {
            Clipboard.SetText(installer);
            ActionStatus.Text = "APK path copied. Paste it into the sender's file picker.";
        }
        catch (System.Runtime.InteropServices.ExternalException)
        {
            ActionStatus.Text = "Clipboard is busy. Select and copy the installer path above.";
        }
    }

    private void Run(Func<Process?> action, string success)
    {
        try { using Process? process = action(); ActionStatus.Text = success; }
        catch (Exception error) when (error is Win32Exception or InvalidOperationException or FileNotFoundException)
        {
            ActionStatus.Text = $"Could not open the Windows tool: {error.Message}";
        }
    }
}
