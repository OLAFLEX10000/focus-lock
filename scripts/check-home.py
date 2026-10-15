import os
import subprocess
import time
import xml.etree.ElementTree as E
from pathlib import Path

adb = os.environ.get("ADB", "adb")
pkg = "com.olaflex.focuslock.verify"


def sh(*args):
    return subprocess.run(
        [adb, "shell", *args], capture_output=True, text=True, check=True
    ).stdout.strip()


def labels():
    sh("uiautomator", "dump", "/sdcard/focuslock-ui.xml")
    root = E.fromstring(sh("cat", "/sdcard/focuslock-ui.xml"))
    return [n.get("text") for n in root.iter("node") if n.get("text")]


def scan():
    result = set()
    last = None
    for _ in range(10):
        page = labels()
        result.update(page)
        if page == last:
            break
        last = page
        sh("input", "swipe", "540", "1700", "540", "900", "700")
    return result


try:
    boot = int(sh("settings", "get", "global", "boot_count"))
    elapsed = int(float(sh("cat", "/proc/uptime").split()[0]) * 1000)
    wall = int(sh("date", "+%s")) * 1000
    duration = 60000
    prefs = f'''<map><set name="apps"><string>com.linkedin.android</string><string>com.android.chrome</string></set><long name="wall" value="{wall + duration}"/><long name="elapsed" value="{elapsed + duration}"/><int name="boot" value="{boot}"/></map>'''
    sh("run-as", pkg, "mkdir", "-p", "shared_prefs")
    subprocess.run(
        [adb, "shell", "run-as", pkg, "tee", "shared_prefs/focus.xml"],
        input=prefs,
        text=True,
        capture_output=True,
        check=True,
    )
    sh("am", "start", "-n", pkg + "/com.olaflex.focuslock.FocusHomeActivity")
    active = scan()
    Path(".build/home-active-labels.txt").write_text("\n".join(sorted(active)))
    assert "LinkedIn" not in active and "Chrome" not in active, "Blocked app appeared"
    assert "Calculator" in active, "Unblocked app missing"
    print(
        "PASS: LinkedIn and Chrome absent from active home; Calculator visible",
        flush=True,
    )
    remaining = (
        elapsed + duration - int(float(sh("cat", "/proc/uptime").split()[0]) * 1000)
    )
    time.sleep(max(0, remaining / 1000) + 2)
    sh("am", "start", "-n", pkg + "/com.olaflex.focuslock.FocusHomeActivity")
    # Ensure rendering on resume returns the list to its start.
    expired = scan()
    Path(".build/home-expired-labels.txt").write_text("\n".join(sorted(expired)))
    assert "LinkedIn" in expired and "Chrome" in expired, (
        "Expired apps did not reappear"
    )
    print("PASS: LinkedIn and Chrome reappeared after expiry", flush=True)
    Path(".build/home-verification.txt").write_text(
        "Active: LinkedIn and Chrome absent; Calculator visible. Expired: LinkedIn and Chrome reappeared.\n"
    )
finally:
    subprocess.run([adb, "uninstall", pkg], capture_output=True)
    sh("input", "keyevent", "KEYCODE_HOME")
