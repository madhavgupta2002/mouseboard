using System.IO;

namespace MouseBoard.Windows;

internal static class InstallerPaths
{
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
