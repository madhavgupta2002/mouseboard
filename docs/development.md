# Development

## Components

`apps/android` is a native Java controller with no Gradle dependency. The build
uses pinned official Android SDK packages, compiles the sources with `javac`,
converts them with D8, and signs the APK with a local development key.

`apps/macos` is a Swift package. The build produces a SwiftUI companion, bundles
the Android APK, generates the icon, and ad-hoc signs the `.app`.

`apps/windows` contains the Windows companion and its own build instructions.
The Windows application has the same setup role as the Mac companion.

## Commands

| Command | Purpose |
| --- | --- |
| `bash scripts/build-android.sh` | Build `dist/MouseBoard-Android.apk` |
| `bash scripts/build-mac.sh` | Bundle the APK into `dist/MouseBoard.app` |
| `bash scripts/build-all.sh` | Build Android and macOS |
| `bash scripts/test.sh --unit-only` | Run platform-independent Java logic tests |
| `bash scripts/test.sh` | Run logic tests and verify built APK/Mac artifacts |
| `powershell -File scripts/build-windows.ps1` | Build/package the Windows companion |

The macOS build needs Apple's Command Line Tools. Android builds currently use
the macOS SDK tools and need Java 17+ and Python 3.9+. `.tools` is a project-local SDK/cache directory, not a global
SDK installation.

## Signing and update compatibility

Do not commit local signing keys.

The development APK key remains `.tools/airmouse-debug.keystore`. Keep it to
update an existing local installation. A fresh clone generates a new key, which
cannot update an APK signed by a different developer's key.

The visible product and artifact names are MouseBoard. Android's `dev.airmouse`
application ID and macOS's `dev.airmouse.mac` bundle identifier intentionally
remain unchanged for installation compatibility.

## Bluetooth reports

MouseBoard registers a composite mouse/keyboard descriptor in report mode:

| Report ID | Input bytes | Purpose |
| --- | --- | --- |
| 1 | Buttons, signed X, signed Y, signed wheel | Relative mouse, drag and scroll |
| 2 | Modifier, reserved, six key usages | Keyboard |
| 3 | Unsigned 16-bit X and Y, range 0–32767 | Absolute centering |

The host can send keyboard LED state as a one-byte output report on ID 2.
Mouse button transitions and key-down/key-up pairs use bounded queues.
Pause/disconnect sends neutral mouse and keyboard reports.

Recenter sends two absolute positions: `(16383, 16383)`, then the exact midpoint
`(16384, 16384)`. macOS caches absolute HID values separately from relative
movement and can ignore an unchanged absolute report. Sending the midpoint
alone therefore worked once but could fail after moving the cursor again.
The one-unit lead-in changes both cached axes, so every press finishes with a
fresh center event. Version 0.4.1 changes only the report sequence, not the HID
descriptor; this update does not require a new Bluetooth pairing.

The regression test models unchanged-value filtering in Apple's
[`IOHIDElementPrivate::processReport`](https://github.com/apple-oss-distributions/IOHIDFamily/blob/main/IOHIDFamily/IOHIDElementPrivate.cpp)
and repeats the move/recenter cycle five times.

The original mouse-only edition used three bytes and no report ID. A host that
keeps that old descriptor while receiving a numbered mouse report reads the
button byte as X and horizontal movement as Y. This was observed in the affected
Mac's live IORegistry after the UI/keyboard upgrade. Re-pairing both devices
refreshes the descriptor. The Mac helper inspects the selected phone's active
HID profile and warns about the legacy format; regression checks reproduce the
byte shift and validate the current descriptor exported by the Android tests.

The keyboard bridge uses the phone's normal input method and applies suffix
edits for English composition and autocorrection. It does not read the
computer's text or inject commands through a desktop receiver.

## Motion

The default horizontal mapping uses left/right turning (relative yaw). Pitch
controls vertical movement. Version 0.4.0 migrates existing preferences to this
default once, then preserves subsequent explicit mode changes.

The game rotation vector supplies calibrated orientation; the standard rotation
vector is the fallback. Independent per-axis adaptive filters smooth motion near
rest and respond faster during a deliberate sweep. A small angular hysteresis
suppresses tiny reversals while allowing slow motion to accumulate.

Sensor gaps and discontinuities reset the filters rather than producing delayed
jumps. The approach follows Casiez, Roussel and Vogel's *1 € Filter*, with
angular wrapping and position hysteresis.

## Verification limits

The Java tests cover motion noise, slow aiming, fast sweeps, sample-rate
consistency, drag persistence, cancellation, scrolling, repeated recentering, report descriptors,
keyboard mapping, queue bounds and IME suffix edits.

Full verification also runs Swift checks for stale HID profiles and report-ID
alignment. Reading the active profile does not capture keyboard or mouse input.

Artifact checks inspect the built APK's permissions/version and verify APK/Mac
signatures. They do not prove a physical HID connection, native phone layout,
default-keyboard behavior or the host's display mapping. Test those after
installing on a real Android phone and pairing it with each target OS.

Generated artifacts, SDKs, caches and machine-specific screenshots remain
ignored. Source, tests and documentation are the reproducible repository.

GitHub Actions builds the Android installer and both desktop companions. The
Windows job packages the macOS job's APK, and the macOS job verifies its
bundled installer. CI generates a temporary development signing key on each
runner; those APKs do not replace an installation signed with your local key.
