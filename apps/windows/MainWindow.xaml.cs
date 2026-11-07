using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Windows;
using System.Windows.Threading;

namespace MouseBoard.Windows;

public partial class MainWindow : Window
{
    private readonly string? installer = InstallerPaths.FindOrExtract(AppContext.BaseDirectory);
    private readonly DispatcherTimer refreshTimer = new() { Interval = TimeSpan.FromSeconds(10) };
    private bool readingDevices;

    public MainWindow()
    {
        InitializeComponent();
        DiagnosticLog.Write($"MouseBoard for Windows {typeof(MainWindow).Assembly.GetName().Version} started on " +
            $"{Environment.OSVersion.VersionString} ({System.Runtime.InteropServices.RuntimeInformation.OSArchitecture}), PC \"{Environment.MachineName}\"");
        LogPathText.Text = DiagnosticLog.Folder;
        PcNameText.Text = Environment.MachineName;
        InstallerPathBox.Text = installer ?? "Could not prepare the phone installer. Download the Android APK from the repository.";
        SendButton.IsEnabled = ShowButton.IsEnabled = CopyButton.IsEnabled = installer is not null;
        refreshTimer.Tick += async (_, _) => await RefreshDevices();
        Loaded += async (_, _) => { await RefreshDevices(); refreshTimer.Start(); };
        Closed += (_, _) => refreshTimer.Stop();
    }

    private IReadOnlyList<PairedDevice> lastDevices = Array.Empty<PairedDevice>();
    private string lastSummary = "";

    private async Task RefreshDevices()
    {
        if (readingDevices) return;
        readingDevices = true;
        RefreshButton.IsEnabled = false;
        ulong? selected = (Devices.SelectedItem as PairedDevice)?.Address;
        try
        {
            bool radio = await Task.Run(BluetoothDevices.HasRadio);
            IReadOnlyList<PairedDevice> paired = await Task.Run(BluetoothDevices.Read);
            Devices.ItemsSource = paired;
            Devices.SelectedItem = paired.FirstOrDefault(device => device.Address == selected)
                ?? paired.FirstOrDefault();
            string summary = Diagnosis.Diagnose(radio, paired, Environment.MachineName);
            if (summary != lastSummary || !SameDevices(paired, lastDevices))
            {
                DiagnosticLog.Write($"Bluetooth radio={(radio ? "present" : "MISSING")}, {paired.Count} paired device(s)");
                foreach (PairedDevice device in paired) DiagnosticLog.Write("  " + Diagnosis.Describe(device));
                DiagnosticLog.Write("Status: " + summary);
            }
            lastDevices = paired;
            lastSummary = summary;
            DeviceStatus.Text = summary;
            TroubleshootStatus.Text = summary;
        }
        catch (Exception error) when (error is Win32Exception or DllNotFoundException or EntryPointNotFoundException)
        {
            DiagnosticLog.Write($"Reading Bluetooth devices failed: {error.GetType().Name}: {error.Message}");
            DeviceStatus.Text = TroubleshootStatus.Text =
                $"Could not read Bluetooth devices ({error.Message}). Check that Bluetooth is on in Windows Settings. [W1]";
        }
        finally
        {
            readingDevices = false;
            RefreshButton.IsEnabled = true;
        }
    }

    private static bool SameDevices(IReadOnlyList<PairedDevice> a, IReadOnlyList<PairedDevice> b) =>
        a.Count == b.Count && a.Zip(b).All(pair => pair.First.Address == pair.Second.Address &&
            pair.First.Connected == pair.Second.Connected && pair.First.HasMouseService == pair.Second.HasMouseService);

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
            DiagnosticLog.Write($"Opening Windows tool failed: {error.Message}");
            ActionStatus.Text = $"Could not open the Windows tool: {error.Message}";
        }
    }

    private void RemoveClicked(object sender, RoutedEventArgs e)
    {
        if (Devices.SelectedItem is not PairedDevice device) { ActionStatus.Text = "Select the phone in the list first."; return; }
        if (MessageBox.Show(this, $"Remove the Windows Bluetooth pairing for \"{device.Name}\"?\n\n" +
                "Afterwards, on the phone open MouseBoard → Connect → Pair new computer, then on this PC choose Add device → Bluetooth.",
                "Remove pairing", MessageBoxButton.OKCancel, MessageBoxImage.Question) != MessageBoxResult.OK) return;
        uint result = BluetoothDevices.Remove(device.Address);
        DiagnosticLog.Write($"Remove pairing {Diagnosis.Describe(device)} → result {result}");
        ActionStatus.Text = result == 0
            ? $"Removed \"{device.Name}\". Also remove this PC from the phone's Bluetooth list, then use Pair new computer on the phone."
            : $"Windows could not remove the pairing (error {result}). Remove it in Bluetooth settings instead. [W7]";
        _ = RefreshDevices();
    }

    private void AddDeviceClicked(object sender, RoutedEventArgs e) =>
        Run(() => Process.Start(new ProcessStartInfo("ms-settings:connecteddevices") { UseShellExecute = true }),
            "Choose Add device → Bluetooth, then pick your phone while MouseBoard on the phone shows Pairing mode.");

    private async void DiagnosticsClicked(object sender, RoutedEventArgs e)
    {
        DiagnosticsButton.IsEnabled = false;
        ActionStatus.Text = "Collecting diagnostics…";
        try
        {
            await RefreshDevices();
            string pnp = await Task.Run(DiagnosticLog.PnpDevices);
            var report = new StringBuilder();
            report.AppendLine("MouseBoard Windows diagnostics");
            report.AppendLine($"App {typeof(MainWindow).Assembly.GetName().Version} · {Environment.OSVersion.VersionString} · " +
                $"{System.Runtime.InteropServices.RuntimeInformation.OSArchitecture} · PC \"{Environment.MachineName}\"");
            report.AppendLine("Status: " + lastSummary);
            report.AppendLine("Paired devices:");
            foreach (PairedDevice device in lastDevices) report.AppendLine("  • " + Diagnosis.Describe(device));
            report.AppendLine();
            report.AppendLine("Bluetooth / HID device nodes:");
            report.AppendLine(pnp);
            report.AppendLine();
            report.AppendLine("Recent events:");
            report.AppendLine(DiagnosticLog.RecentText());
            DiagnosticLog.Write("Diagnostics collected");
            DiagnosticsBox.Text = report.ToString();
            DiagnosticsBox.Visibility = Visibility.Visible;
            try
            {
                Clipboard.SetText(DiagnosticsBox.Text);
                ActionStatus.Text = "Diagnostics copied to the clipboard. Paste them to whoever is helping you.";
            }
            catch (System.Runtime.InteropServices.ExternalException)
            {
                ActionStatus.Text = "Diagnostics shown below; select all and copy them.";
            }
        }
        finally { DiagnosticsButton.IsEnabled = true; }
    }

    private void LogFolderClicked(object sender, RoutedEventArgs e)
    {
        Directory.CreateDirectory(DiagnosticLog.Folder);
        Run(() => Process.Start(new ProcessStartInfo("explorer.exe", $"\"{DiagnosticLog.Folder}\"") { UseShellExecute = true }),
            "Log folder opened.");
    }
}
