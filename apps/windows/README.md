# MouseBoard for Windows

A native WPF setup companion for Windows 10/11. The phone is the Bluetooth HID
mouse and keyboard; Windows receives input directly. The companion reads paired
devices, opens Bluetooth settings, locates the bundled Android installer and
launches Windows Bluetooth File Transfer. It opens no network listener and
requires no account or administrator access.

## Use

1. Extract the complete Windows ZIP, then run `MouseBoard.exe`.
2. Pair the phone in **Bluetooth settings**.
3. Click **Open Bluetooth sender**. In the Windows wizard choose your phone,
   then browse to the APK path shown in MouseBoard. **Copy path** and **Show APK**
   make the file easy to select. The helper does not claim a transfer has
   completed; accept it on the phone.
4. Install the APK and open MouseBoard on the phone. Allow Nearby devices, tap
   Connect and choose the PC name shown in the helper.
5. Keep the phone app open. Use Recenter, the click/drag pads, middle scroll
   strip and phone keyboard. Set the Windows input language to **US** for typing.

The device list shows system pairing, not proof of an active mouse connection.
If upgrading from an old mouse-only descriptor, remove the old pairing on both
devices and pair again. You may close the Windows helper after installation.

## Build

Install the .NET 10 SDK and build `dist/MouseBoard-Android.apk` first.
From the repository root in PowerShell:

```powershell
./scripts/build-windows.ps1
```

This runs portable interop/path checks, publishes a self-contained x64 app, copies
the APK into `Assets`, verifies its hash, and produces
`dist/MouseBoard-Windows-win-x64.zip`. The output folder is a unit: extract all
files together. Users do not need a separate .NET runtime with the default build.

```powershell
./scripts/build-windows.ps1 -Runtime win-arm64
./scripts/build-windows.ps1 -FrameworkDependent
```

A framework-dependent build requires the .NET 10 Desktop Runtime on the target.

For a source compilation check on macOS/Linux with the .NET 10 SDK:

```sh
dotnet build apps/windows/MouseBoard.Windows.csproj -c Release
dotnet run --project apps/windows/tests/MouseBoard.Windows.Checks.csproj -c Release
```

`EnableWindowsTargeting` permits compilation on another OS; the WPF app and
native Bluetooth calls still require Windows to run. Physical Windows/phone
pairing, file transfer, cursor movement and IME behavior require device testing.
