using System.IO;
using System.Security.Cryptography;

namespace MouseBoard.Windows;

internal static class InstallerPaths
{
    public static string? FindOrExtract(string baseDirectory)
    {
        using Stream? resource = typeof(InstallerPaths).Assembly.GetManifestResourceStream("MouseBoard.Android.apk");
        // A published EXE always uses its own APK, even if an older download
        // happens to be beside it. Source-only builds retain the file fallback.
        if (resource is null) return Find(baseDirectory);
        try
        {
            return Extract(resource, Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "MouseBoard", "Installers"));
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            return null;
        }
    }

    // A versioned per-user copy gives Windows Bluetooth File Transfer a real
    // file to send. No administrator permission or writable EXE folder needed.
    internal static string Extract(Stream resource, string directory)
    {
        using var memory = new MemoryStream();
        resource.CopyTo(memory);
        byte[] payload = memory.ToArray();
        string hash = Convert.ToHexString(SHA256.HashData(payload)).ToLowerInvariant();
        string folder = Path.Combine(directory, hash);
        string destination = Path.Combine(folder, "MouseBoard-Android.apk");
        Directory.CreateDirectory(folder);
        if (File.Exists(destination) &&
            CryptographicOperations.FixedTimeEquals(SHA256.HashData(File.ReadAllBytes(destination)),
                SHA256.HashData(payload)))
            return destination;
        string temporary = destination + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            File.WriteAllBytes(temporary, payload);
            File.Move(temporary, destination, overwrite: true);
        }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
        return destination;
    }

    public static string? Find(string baseDirectory)
    {
        // Published bundle first; adjacent APK also supports manual installation.
        foreach (string candidate in new[]
        {
            Path.Combine(baseDirectory, "Assets", "MouseBoard-Android.apk"),
            Path.Combine(baseDirectory, "MouseBoard-Android.apk")
        })
        {
            if (File.Exists(candidate))
                return Path.GetFullPath(candidate);
        }
        return null;
    }
}
