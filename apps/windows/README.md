# MouseBoard for Windows

A native WPF setup companion for Windows 10/11. The phone is the Bluetooth HID
mouse and keyboard; Windows receives input directly. The companion reads paired
devices, opens Bluetooth settings, locates the bundled Android installer and
launches Windows Bluetooth File Transfer. It opens no network listener and
requires no account or administrator access.

## Use

1. [Download MouseBoard-Windows-x64.exe](https://github.com/madhavgupta2002/mouseboard/raw/refs/heads/main/downloads/MouseBoard-Windows-x64.exe)
   and double-click it. No installer, ZIP extraction or .NET installation is needed.
2. Pair the phone in **Bluetooth settings**.
3. Click **Open Bluetooth sender**. In the Windows wizard choose your phone,
   then browse to the APK path shown in MouseBoard. **Copy path** and **Show APK**
   make the file easy to select. The helper does not claim a transfer has
   completed; accept it on the phone.
4. Install the APK and open MouseBoard on the phone. Allow Nearby devices, tap
   Connect and choose the PC name shown in the helper.
5. In the phone's Settings, turn off **Mac gesture shortcuts**. Use Recenter,
   the click/drag pads or Touchpad mode, and the phone keyboard. Set Windows
   input language to **US** for typing.

The EXE includes the Android APK and all required .NET runtime files. It
extracts a verified, versioned APK to `%LOCALAPPDATA%\MouseBoard\Installers`.
You can move the EXE anywhere without keeping other files next to it.

The device list shows system pairing, not proof of an active mouse connection.
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
