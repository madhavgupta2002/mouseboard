# MouseBoard for Windows

A native WPF setup companion for Windows 10/11. The phone is the Bluetooth HID
mouse and keyboard; Windows receives input directly. The companion reads paired
devices, opens Bluetooth settings, locates the bundled Android installer and
launches Windows Bluetooth File Transfer. It opens no network listener and
requires no account or administrator access.

## Use

1. [Download MouseBoard-Windows-x64.exe](https://github.com/madhavgupta2002/mouseboard/raw/refs/heads/main/downloads/MouseBoard-Windows-x64.exe)
   and double-click it. No installer, ZIP extraction or .NET installation is needed.
2. Install the APK on the phone: click **Open Bluetooth sender**, choose your
   phone, then browse to the APK path shown in MouseBoard (**Copy path** and
   **Show APK** help). Accept the file on the phone and install it.
3. If the phone is already paired with this PC, click **Remove pairing** (and
   remove the PC on the phone). An earlier pairing has no mouse service.
4. On the phone open MouseBoard, allow Nearby devices and tap **Connect → Pair
   new computer → Make visible**. On the PC click **Add device** → Bluetooth and
   choose the phone. Confirm the code; the phone connects automatically. The
   **Troubleshoot** panel should show **[W0] connected as a mouse and keyboard**.
5. In the phone's Settings, turn off **Mac gesture shortcuts**. Use Recenter,
   the click/drag pads or Touchpad mode, and the phone keyboard. Set Windows
   input language to **US** for typing.

The EXE includes the Android APK and all required .NET runtime files. It
extracts a verified, versioned APK to `%LOCALAPPDATA%\MouseBoard\Installers`.
You can move the EXE anywhere without keeping other files next to it.

The device list shows each pairing and whether Windows installed the mouse
service (**mouse service ✓**). **Copy diagnostics** copies a report with services,
Bluetooth/HID device nodes and recent events; logs are kept in
`%LOCALAPPDATA%\MouseBoard\Logs`.
If upgrading from an old mouse-only descriptor, remove the old pairing on both
devices and pair again. You may close the Windows helper after installation.

## Build

Only developers need this section. Install the .NET 10 SDK and Python 3.9+,
then build `dist/MouseBoard-Android.apk` first.
From the repository root in PowerShell:

```powershell
./scripts/build-windows.ps1
```

This publishes a compressed, self-contained x64 EXE, verifies the embedded APK
and Windows executable architecture, and runs portable interop/extraction
checks. The result is `dist/MouseBoard-Windows-x64.exe`.

```powershell
./scripts/build-windows.ps1 -Runtime win-arm64
```

To build on macOS/Linux with the .NET 10 SDK and Python:

```sh
python3 scripts/package-windows.py
```

`EnableWindowsTargeting` permits compilation on another OS; the WPF app and
native Bluetooth calls still require Windows to run. Physical Windows/phone
pairing, file transfer, cursor movement and IME behavior require device testing.
