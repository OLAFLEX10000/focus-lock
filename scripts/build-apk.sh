#!/bin/zsh
set -euo pipefail
cd "${0:A:h}/.."
if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17)
fi
export JAVA_HOME
sdk="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
bt="$sdk/build-tools/35.0.0"
work="$PWD/.build"
mkdir -p "$work/classes" "$work/dex" builds "$HOME/.local/share/focuslock"
export FOCUS_PACKAGE="${FOCUS_PACKAGE:-com.olaflex.focuslock.v2}"
python3 - <<'PY'
import os
import xml.etree.ElementTree as ET

namespace = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', namespace)
android = '{' + namespace + '}'
manifest = ET.parse('FocusLock/app/src/main/AndroidManifest.xml')
root = manifest.getroot()
root.set('package', os.environ['FOCUS_PACKAGE'])
root.set(android + 'versionCode', '5')
root.set(android + 'versionName', '0.5')
root.find('application').set(android + 'debuggable', 'true')
for component in root.iter():
    name = component.get(android + 'name', '')
    if name.startswith('.'):
        component.set(android + 'name', 'com.olaflex.focuslock' + name)
manifest.write('.build/AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
PY
"$JAVA_HOME/bin/javac" -cp "$sdk/platforms/android-35/android.jar" -d "$work/classes" FocusLock/app/src/main/java/com/olaflex/focuslock/*.java
./scripts/test.sh
"$bt/aapt2" compile --dir FocusLock/app/src/main/res -o "$work/resources.zip"
"$bt/aapt2" link -o "$work/unsigned.apk" -I "$sdk/platforms/android-35/android.jar" --manifest "$work/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 35 "$work/resources.zip"
"$JAVA_HOME/bin/jar" cf "$work/classes.jar" -C "$work/classes" .
"$bt/d8" --lib "$sdk/platforms/android-35/android.jar" --min-api 26 --output "$work/dex" "$work/classes.jar"
python3 - <<'PY'
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile('.build/unsigned.apk', 'a', ZIP_DEFLATED) as archive:
    archive.write('.build/dex/classes.dex', 'classes.dex')
PY
"$bt/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
key="$HOME/.local/share/focuslock/test-signing.jks"
if [[ ! -f "$key" ]]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$key" -storepass android -keypass android -alias focuslock -dname 'CN=FocusLock personal testing' -keyalg RSA -keysize 2048 -validity 3650
    chmod 600 "$key"
fi
"$bt/apksigner" sign --ks "$key" --ks-pass pass:android --out "builds/${FOCUS_PACKAGE}.apk" "$work/aligned.apk"
"$bt/apksigner" verify "builds/${FOCUS_PACKAGE}.apk"
if [[ "$FOCUS_PACKAGE" == "com.olaflex.focuslock.v2" ]]; then
    cp "builds/${FOCUS_PACKAGE}.apk" builds/FocusLock-2.apk
fi
