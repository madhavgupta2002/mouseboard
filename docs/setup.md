# Installation and controls

## Android

Install `dist/MouseBoard-Android.apk` on the phone. Desktop companions can help
transfer it over Bluetooth. Accept the file, open it, and allow installation
from the source Android identifies if needed. Managed-device policy can prevent
installation of locally built APKs.

Open **MouseBoard**, grant **Nearby devices**, then tap **Connect** and select
the paired computer. Allow notifications for the ongoing connection controls.
The connection stays active when you switch apps or lock the phone. Disconnect
in the app or its notification when finished.

If upgrading from an older mouse-only build, the computer may have cached the
old HID descriptor. If new controls fail, remove the old pairing on both devices,
pair again, and reconnect with MouseBoard open.

If the pointer moves only vertically, this can be the same pairing mismatch:
the old three-byte format reads the new report-ID byte as buttons, the button
byte as X, and X movement as Y. Changing motion sensitivity or reinstalling the
APK does not clear the computer's cached format. The Mac companion detects this
old active profile and displays **Old mouse profile detected**.

To repair it, forget the phone in the Mac's Bluetooth settings and unpair the
Mac in the phone's Bluetooth settings. Pair again, then return to MouseBoard
on the phone and tap **Connect**.

## macOS

Open `dist/MouseBoard.app`, or use `Open MouseBoard.command` after building.
Select the paired phone and choose **Send APK over Bluetooth**. Accept the
incoming file on the phone.

If macOS asks whether MouseBoard may control Bluetooth File Exchange, allow it
to initiate the transfer. Alternatively, choose **Show APK in Finder** and use
Bluetooth File Exchange yourself.

The helper does not inject input and requires no Accessibility permission. It
can be closed after installation.

## Windows

Use the [Windows companion](../apps/windows/README.md) to open Bluetooth settings,
find the Android installer, and start Windows Bluetooth File Transfer. Mouse
and keyboard input are handled by Windows through the standard HID profile.

## Controls

| Control | Action |
| --- | --- |
| Tilt | Move the pointer |
| Recenter | Reset the phone's neutral grip and send the pointer to the host's mapped display midpoint |
| LEFT / RIGHT | Tap to click; keep pressed and tilt to drag |
| Centre scroll strip | Slide vertically to scroll |
| Touchpad / Buttons | Switch between a touchpad and tilt with click pads; choice is saved |
| Touchpad: one finger | Move the pointer; tap anywhere for left-click |
| Touchpad: hold, then move | Drag until the finger is lifted |
| Touchpad: two fingers | Swipe to scroll; tap for right-click |
| Back / Forward | Browser/navigation shortcuts for the selected desktop OS |
| Dim | Barely lit black touch surface; Exit dim returns to the normal screen |
| Keyboard | Open the phone's normal keyboard |
| Pause | Stop input and release held buttons and keys |
| ⋯ | Sensitivity, tilt/turn mode, Mac/Windows shortcuts and Bluetooth settings |

Turning the phone left/right controls horizontal movement by default; lifting
or lowering its top controls vertical movement. Version 0.4.0 also enables
this mode once when upgrading an existing installation. You can still change
the mode in settings, and later launches keep your choice.
Tilt input is disabled while Touchpad mode is selected.

**Back / Forward** defaults to Mac shortcuts (Command + bracket). Turn off
**Mac Back / Forward shortcuts** in Settings for Windows (Alt + arrow).
These controls work in applications that support the corresponding shortcuts.

In **Dim**, touchpad mode keeps the same gestures. In button mode, tap the left
or right half to click, hold and tilt to drag, or swipe to scroll. The screen is
still on at minimum requested brightness: Android does not deliver normal
touchscreen taps to an app with the display switched off. Use Dim for touch
controls, or lock the phone to use tilt motion alone in Buttons mode.
Sideways scrolling uses Shift + wheel and depends on the receiving application.

The foreground service owns Bluetooth and motion sensing. Its notification
provides Pause/Resume and Disconnect. In tilt mode a partial wake lock keeps
motion processing running while the screen sleeps; this uses battery. Android
or Samsung power restrictions may still stop the service. If that happens,
set MouseBoard's battery use to **Unrestricted** in Android app settings.

Click a text field on the computer before typing. The phone displays recent
typed text, but does not read or mirror the computer's document.

Typing currently supports English letters, numbers, US punctuation, Tab, Enter
and Backspace. Select **ABC / US** as the computer input source. Unsupported
characters are rejected with a message rather than partially typed.

## Troubleshooting

- Keep Bluetooth on. Open MouseBoard to start a connection; its service then
  maintains it in the background.
- Check the phone's Nearby devices permission.
- Close other phone apps that register as Bluetooth keyboards or mice; Android
  allows only one HID device app to register at a time.
- If the connection times out after an upgrade, forget and recreate the pairing
  while MouseBoard is open.
- If the phone reports that HID device mode is unavailable, its firmware does
  not expose the required profile.
- Large pastes are bounded. Wait for the typing queue to drain before retrying.

On a single display, Recenter targets its midpoint. Multi-display mapping is
controlled by the host OS. The absolute report format is tested, but actual
mapping must be checked on the physical phone/computer pair.

If Recenter only works once after connecting, update the Android app to 0.4.1
or later. It sends a fresh absolute position on every press, without changing
the Bluetooth profile or requiring re-pairing.
