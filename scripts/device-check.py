import os
import subprocess
import time
from pathlib import Path

adb = os.environ.get("ADB", "adb")
pkg = "com.olaflex.focuslock.verify"


def sh(*args):
    return subprocess.run(
        [adb, "shell", *args], capture_output=True, text=True, check=True
    ).stdout.strip()


original = sh("settings", "get", "secure", "enabled_accessibility_services")
Path(".build/original-services.txt").write_text(original)
sh("am", "force-stop", pkg)
boot = int(sh("settings", "get", "global", "boot_count"))
wall = int(sh("date", "+%s")) * 1000
elapsed = int(float(sh("cat", "/proc/uptime").split()[0]) * 1000)
duration = 45000
prefs = f'''<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><set name="apps"><string>com.linkedin.android</string></set><long name="wall" value="{wall + duration}"/><long name="elapsed" value="{elapsed + duration}"/><int name="boot" value="{boot}"/></map>'''
sh("run-as", pkg, "mkdir", "-p", "shared_prefs")
subprocess.run(
    [adb, "shell", "run-as", pkg, "tee", "shared_prefs/focus.xml"],
    input=prefs,
    text=True,
    capture_output=True,
    check=True,
)
sh("am", "start", "-n", pkg + "/com.olaflex.focuslock.MainActivity")
services = [x for x in original.split(":") if x and x != "null"]
services.append(pkg + "/com.olaflex.focuslock.BlockService")
results = []
try:
    sh(
        "settings",
        "put",
        "secure",
        "enabled_accessibility_services",
        ":".join(services),
    )
    time.sleep(5)
    for i in range(12):
        sh(
            "am",
            "start",
            "-n",
            "com.linkedin.android/.authenticator.LaunchActivityDefault",
        )
        time.sleep(1.5)
        windows = sh("dumpsys", "window")
        activity = sh("dumpsys", "activity", "activities")
        tops = [l.strip() for l in activity.splitlines() if "topResumedActivity" in l]
        shield = pkg in windows and "FocusLock" in windows
        row = {"attempt": i + 1, "top": tops, "blocking_window_present": shield}
        results.append(row)
        print(row, flush=True)
        assert tops and all("com.linkedin.android/" not in line for line in tops), (
            "LinkedIn remained foreground"
        )
    # Wait for test session expiry (real elapsed-time session; no clock change).
    time.sleep(23)
    sh("am", "start", "-n", "com.linkedin.android/.authenticator.LaunchActivityDefault")
    time.sleep(1)
    expired_tops = [
        l.strip()
        for l in sh("dumpsys", "activity", "activities").splitlines()
        if "topResumedActivity" in l
    ]
    print("AFTER EXPIRY:", "\n".join(expired_tops), flush=True)
    assert any("com.linkedin.android/" in line for line in expired_tops), (
        "LinkedIn did not unlock"
    )
    Path(".build/repeated-launch-results.txt").write_text(str(results))
finally:
    sh("settings", "put", "secure", "enabled_accessibility_services", original)
    subprocess.run([adb, "uninstall", pkg], capture_output=True)
    sh("input", "keyevent", "KEYCODE_HOME")
