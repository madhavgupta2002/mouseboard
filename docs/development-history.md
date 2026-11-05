# Development history and reconstruction

MouseBoard began as AirMouse, an Android phone controller for a desktop mouse. Its early development happened before Git was initialized. The historical stages below were reconstructed from successful source-edit payloads in the recorded development session, the recorded conversation, and the source and build artifacts that remained in the workspace.

These are not original, contemporaneous Git commits. Where historical author dates are used in reconstructed commits, they refer to recorded milestone deliveries. Their precision reflects the conversation timestamp, not an independently recorded source commit. Reconstruction itself happened on November 5, 2026.

All times below use **Asia/Kolkata (UTC+05:30)**.

## Recorded milestones

| Date and time | Milestone | Evidence and limits |
| --- | --- | --- |
| 2026-11-04 16:14:55.692 | Project requested: phone tilt controls desktop pointer, with left and right touch controls. | Recorded user request. |
| 2026-11-04 16:20:52.821 | Initial Wi-Fi MVP delivered: native Android controller and macOS receiver, installation page, motion control, and clicks. | Recorded delivery. The session reported successful app builds and 32 automated checks. |
| 2026-11-04 16:23:57.632 | Local address and QR handling repaired after the phone could not reach the installation page. | Recorded delivery and a saved network-check screenshot. |
| 2026-11-04 16:33:27.077 | IPv4/IPv6 connection support delivered. | Recorded delivery reported 55 automated checks. A complete source archive preserves this final Wi-Fi edition. End-to-end phone connectivity on the company network remained unverified. |
| 2026-11-05 14:53:24.148 | Bluetooth-only edition, version 0.2.0, delivered. | Recorded delivery. Android became a Bluetooth HID mouse; the macOS app became a pairing and installation helper. A later preserved 0.2.0 APK and motion-math source provide partial artifact evidence. |
| 2026-11-05 17:38:35.830 | Pointer jitter reduction, version 0.2.1, delivered. | Recorded delivery and a preserved 0.2.1 APK. The session reported motion-filter checks for stationary noise, slow movement, and fast movement. |
| 2026-11-05 17:47:29.003 | Version 0.3.0 delivered with a black interface, large click controls, center scrolling, keyboard entry, hold-to-drag, and recentering. | Recorded delivery, current source at the start of repository organization, and a preserved 0.3.0 APK. Recenter resets the phone's reference position and requests desktop pointer centering through HID. Physical-device behavior still requires testing. |
| 2026-11-05 17:47:33.117 | MouseBoard rename, Windows helper, repository organization, and Git history reconstruction requested. | Recorded user request. Subsequent commits document this work as it was performed. |

## Source recovery

Six source trees were recovered: the initial Wi-Fi MVP, its address repair, its IPv6 update, Bluetooth 0.2.0, jitter reduction 0.2.1, and the UI and input changes in 0.3.0. Recovery replayed the successful recorded patches in order. Two patches that had failed during development were excluded. The two README edits originally made by Python were recovered from their recorded literal text; historical shell commands were not executed.

The pinned Android SDK manifest was recovered from the preserved Wi-Fi archive. Every source file in that archive matches the recovered IPv6-stage file byte for byte. Executable script permissions were recovered from the recorded `chmod` operation. These recovered trees contain source, scripts, and documentation; generated build outputs and local SDK installations are excluded.

No missing source implementation was approximated for these six stages. Their Git commits are reconstructed records of recovered source states, rather than evidence that Git was used at the time.

## Preserved artifacts

The paths in this table are workspace paths relative to the repository root. Build outputs and old source archives may be ignored by Git and therefore absent from a fresh clone.

| Artifact | What it preserves | Filesystem evidence |
| --- | --- | --- |
| `dist/AirMouse-WiFi-source-backup.tar.gz` | Complete final Wi-Fi edition: Android and macOS sources, build scripts, and checks. Paths inside the archive use the original `android/` and `mac/` layout. | Archive saved 2026-11-05 14:46:40.623. Its source-file modification times range through 2026-11-04 16:32:16.254. |
| `apps/android/build/jitter-baseline/MotionMath.java` | Motion calculation before the adaptive jitter reduction. This is one source file, not a complete 0.2.0 source tree. | Preserved copy saved 2026-11-05 17:36:48.396. |
| `apps/android/build/jitter-baseline/AirMouse-Android-0.2.0.apk` | Android package `dev.airmouse`, version 0.2.0, version code 2. | Preserved copy saved 2026-11-05 17:36:48.402. |
| `apps/android/build/previous/AirMouse-Android-0.2.1.apk` | Android package `dev.airmouse`, version 0.2.1, version code 3. | Preserved copy saved 2026-11-05 17:37:38.650. |
| `dist/AirMouse-Android.apk` | Android package `dev.airmouse`, version 0.3.0, version code 4, at the time of the audit. This output path can be replaced by later builds. | File saved 2026-11-05 17:46:53.693. |
| `dist/smoke-test.log` | A Wi-Fi receiver smoke test that reported ready, movement, left click, and right click events. | File saved 2026-11-04 16:32:43.094. |

The Wi-Fi archive's SHA-256 at the time of the audit was:

```text
bbd1beec2dda4ad0f93ea665fd464ef1355e6a61b6d56ff69453cef17855775b
```

Saved preview screenshots also support the order of the Wi-Fi, Bluetooth, and 0.3.0 UI work. Screenshots are not source snapshots and can include device or host information, so they are not needed for the public source history.

## Reconstruction boundaries

- A file's modification time records the last save or copy visible to the filesystem. In particular, the 0.2.0 and 0.2.1 APK timestamps above are preserved-copy times, not proof of the original build time.
- Moving and renaming the source during repository organization changes filesystem timestamps. Those newer timestamps do not date the original feature work.
- The complete Wi-Fi archive and recovered edit payloads provide source evidence independently of the intermediate APKs. An APK alone does not recover the exact original source tree.
- The six recovered source stages remain reconstructions and should be described as such in their commit messages. Their recorded delivery times are used as historical author dates; reconstruction does not establish an original Git commit time.
- Historical AirMouse names in archives, package identifiers, or reconstructed stages reflect the project's earlier name. They do not indicate a separate project.
- Reported test results describe checks performed during the recorded session. They do not establish Bluetooth compatibility with every phone, macOS version, or Windows system.

The intent of this history is to preserve the actual progression of the project while making the evidence and its limits explicit.
