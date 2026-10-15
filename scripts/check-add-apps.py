import os
import re
import subprocess
import xml.etree.ElementTree as E
from pathlib import Path

adb = os.environ.get("ADB", "adb")
pkg = "com.olaflex.focuslock.verify"


def sh(*args):
    return subprocess.run(
        [adb, "shell", *args], capture_output=True, text=True, check=True
    ).stdout.strip()


def ui():
    sh("uiautomator", "dump", "/sdcard/focuslock-ui.xml")
    return E.fromstring(sh("cat", "/sdcard/focuslock-ui.xml"))


def find(root, label):
    return next(
        n for n in root.iter("node") if n.get("text", "").lower() == label.lower()
    )


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    sh("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def prefs():
    return E.fromstring(sh("run-as", pkg, "cat", "shared_prefs/focus.xml"))


def value(root, key):
    n = next(n for n in root if n.get("name") == key)
    return {c.text for c in n} if n.tag == "set" else n.get("value")


try:
    boot = int(sh("settings", "get", "global", "boot_count"))
    elapsed = int(float(sh("cat", "/proc/uptime").split()[0]) * 1000)
    wall = int(sh("date", "+%s")) * 1000
    fixture = f'<map><set name="apps"><string>com.linkedin.android</string></set><long name="wall" value="{wall + 900000}"/><long name="elapsed" value="{elapsed + 900000}"/><int name="boot" value="{boot}"/></map>'
    sh("run-as", pkg, "mkdir", "-p", "shared_prefs")
    subprocess.run(
        [adb, "shell", "run-as", pkg, "tee", "shared_prefs/focus.xml"],
        input=fixture,
        capture_output=True,
        text=True,
        check=True,
    )
    before = prefs()
    sh("am", "start", "-n", pkg + "/com.olaflex.focuslock.MainActivity")
    tap(find(ui(), "Add more apps to block"))
    root = ui()
    candidate = find(root, "Access More")
    assert candidate.get("enabled") == "true" and candidate.get("checked") == "false"
    tap(candidate)
    root = ui()
    locked = find(root, "Access More")
    assert locked.get("enabled") == "false" and locked.get("checked") == "true"
    after = prefs()
    assert (
        "com.linkedin.android" in value(after, "apps")
        and len(value(after, "apps")) == 2
    )
    for key in ["wall", "elapsed", "boot"]:
        assert value(after, key) == value(before, key)
    tap(locked)
    assert value(prefs(), "apps") == value(after, "apps")
    tap(find(ui(), "Done"))
    assert find(ui(), "2 apps locked") is not None
    print(
        "PASS: addition saved; existing block retained; added checkbox disabled; removal tap ineffective; timer preserved; count updated",
        flush=True,
    )
    Path(".build/add-apps-verification.txt").write_text(
        "Physical add-only UI and persistence checks passed; original timer preserved.\n"
    )
finally:
    subprocess.run([adb, "uninstall", pkg], capture_output=True)
    sh("input", "keyevent", "KEYCODE_HOME")
