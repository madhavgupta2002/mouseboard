using System.Diagnostics;
using System.IO;
using System.Text;

namespace MouseBoard.Windows;

/// <summary>Plain-text log in %LOCALAPPDATA%\MouseBoard\Logs that users can send for troubleshooting.</summary>
internal static class DiagnosticLog
{
    private static readonly object Gate = new();
    private static readonly Queue<string> Recent = new();
    public static string Folder { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MouseBoard", "Logs");
    public static string FilePath => Path.Combine(Folder, $"mouseboard-{DateTime.Now:yyyyMMdd}.log");

    public static void Write(string message)
    {
        string line = $"{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}  {message}";
        Debug.WriteLine(line);
        lock (Gate)
        {
            Recent.Enqueue(line);
            while (Recent.Count > 400) Recent.Dequeue();
            try
            {
                Directory.CreateDirectory(Folder);
                File.AppendAllText(FilePath, line + Environment.NewLine, Encoding.UTF8);
            }
            catch (Exception error) when (error is IOException or UnauthorizedAccessException) { }
        }
    }

    public static string RecentText()
    {
        lock (Gate) return string.Join(Environment.NewLine, Recent);
    }

    /// <summary>Bluetooth/HID device nodes and their driver status, via the built-in PnP cmdlet.</summary>
    public static string PnpDevices()
    {
        const string script =
            "Get-PnpDevice -ErrorAction SilentlyContinue | " +
            "Where-Object { $_.InstanceId -like 'BTH*' -or $_.InstanceId -like '*{00001124-0000-1000-8000-00805f9b34fb}*' } | " +
            "Sort-Object Class, FriendlyName | Format-Table -AutoSize Status, Class, FriendlyName, InstanceId | Out-String -Width 220";
        try
        {
            using Process process = Process.Start(new ProcessStartInfo("powershell.exe",
                $"-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command \"{script}\"")
            {
                RedirectStandardOutput = true, RedirectStandardError = true,
                UseShellExecute = false, CreateNoWindow = true
            })!;
            Task<string> output = process.StandardOutput.ReadToEndAsync();
            if (!process.WaitForExit(20000)) { process.Kill(true); return "(device query timed out)"; }
            string text = output.Result.Trim();
            return text.Length == 0 ? "(no Bluetooth device nodes reported)" : text;
        }
        catch (Exception error) { return $"(device query failed: {error.Message})"; }
    }
}
