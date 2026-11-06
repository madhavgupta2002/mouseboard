#!/usr/bin/env python3
"""Check the built APK's real permissions and Bluetooth-only package contract."""
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
AAPT = ROOT / ".tools/android/build-tools/35.0.0/aapt2"
APK = ROOT / "dist/MouseBoard-Android.apk"
permissions = subprocess.check_output([str(AAPT), "dump", "permissions", str(APK)], text=True)
badging = subprocess.check_output([str(AAPT), "dump", "badging", str(APK)], text=True)
names = set(re.findall(r"uses-permission: name='([^']+)'", permissions))
expected = {
    "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_ADMIN",
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
    "android.permission.WAKE_LOCK",
    "android.permission.POST_NOTIFICATIONS",
}
assert names == expected, f"Unexpected APK permissions: {names}"
assert "android.permission.INTERNET" not in permissions
print("PASS: APK requests Bluetooth and background-service permissions, with no Internet access")
assert "minSdkVersion:'28'" in badging
assert "versionCode='7'" in badging
assert "versionName='0.5.0'" in badging
print("PASS: Bluetooth APK version and Android 9 minimum are correct")
assert "uses-feature: name='android.hardware.bluetooth'" in badging
print("PASS: APK declares Bluetooth hardware support")
tree = subprocess.check_output([str(AAPT), "dump", "xmltree", str(APK), "--file", "AndroidManifest.xml"], text=True)
assert "MouseBoardService" in tree and "foregroundServiceType" in tree
service_type = tree[tree.index("foregroundServiceType"):].splitlines()[0]
assert int(service_type.rsplit("=", 1)[1], 16) == 0x10, "Expected connectedDevice service type"
print("PASS: APK declares a foreground connected-device service")
subprocess.run([str(ROOT / ".tools/android/build-tools/35.0.0/apksigner"), "verify", str(APK)], check=True)
print("PASS: APK signature verifies")
subprocess.run(["codesign", "--verify", "--deep", "--strict", str(ROOT / "dist/MouseBoard.app")], check=True)
print("PASS: Mac companion signature verifies")
