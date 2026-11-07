using System.Windows;

namespace MouseBoard.Windows;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        DispatcherUnhandledException += (_, args) =>
        {
            DiagnosticLog.Write("UNHANDLED " + args.Exception);
            MessageBox.Show($"MouseBoard hit an unexpected error and logged it:\n{args.Exception.Message}\n\nLog: {DiagnosticLog.Folder}",
                "MouseBoard", MessageBoxButton.OK, MessageBoxImage.Warning);
            args.Handled = true;
        };
        AppDomain.CurrentDomain.UnhandledException += (_, args) => DiagnosticLog.Write("FATAL " + args.ExceptionObject);
        base.OnStartup(e);
    }
}
