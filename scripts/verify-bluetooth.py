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
}
assert names == expected, f"Unexpected APK permissions: {names}"
assert "android.permission.INTERNET" not in permissions
print("PASS: APK requests Bluetooth permissions only, with no Internet access")
assert "minSdkVersion:'28'" in badging
assert "versionCode='5'" in badging
assert "versionName='0.4.0'" in badging
print("PASS: Bluetooth APK version and Android 9 minimum are correct")
assert "uses-feature: name='android.hardware.bluetooth'" in badging
print("PASS: APK declares Bluetooth hardware support")
subprocess.run([str(ROOT / ".tools/android/build-tools/35.0.0/apksigner"), "verify", str(APK)], check=True)
print("PASS: APK signature verifies")
subprocess.run(["codesign", "--verify", "--deep", "--strict", str(ROOT / "dist/MouseBoard.app")], check=True)
print("PASS: Mac companion signature verifies")
