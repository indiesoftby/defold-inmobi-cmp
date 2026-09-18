"""Reproduce the Defold Android library layout from the supplied SDK archive."""
from pathlib import Path
import hashlib
import io
import json
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
VERSION = "2.4.3"


def prepare():
    archive = ROOT / "sdk/InMobiCMPSDKs.zip"
    with zipfile.ZipFile(archive) as outer:
        with zipfile.ZipFile(io.BytesIO(outer.read("InMobiCMP-Android.zip"))) as android:
            prefix = f"InMobiCMP-Android-v{VERSION}/"
            aar_bytes = android.read(prefix + f"libs/inmobicmp-{VERSION}.aar")
            (ROOT / f"sdk/inmobicmp-{VERSION}.aar").write_bytes(aar_bytes)
            for name in ("changeLog.md", "KotlinLicense.txt"):
                (ROOT / "sdk" / name).write_bytes(android.read(prefix + name))
    with zipfile.ZipFile(io.BytesIO(aar_bytes)) as aar:
        (ROOT / "inmobi_cmp/lib/android/inmobicmp.jar").write_bytes(aar.read("classes.jar"))
        for name in aar.namelist():
            if name.startswith("res/") and not name.endswith("/"):
                path = ROOT / "inmobi_cmp/res/android/res/inmobicmp" / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(aar.read(name))
        manifest = aar.read("AndroidManifest.xml")
        (ROOT / "inmobi_cmp/res/android/res/inmobicmp/AndroidManifest.xml").write_bytes(manifest)
        ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
        ns = "{http://schemas.android.com/apk/res/android}"
        tree = ET.fromstring(manifest)
        app = tree.find("application")
        ET.SubElement(app, "meta-data", {ns + "name": "com.defold.inmobicmp.P_CODE",
                                       ns + "value": "{{inmobi_cmp.p_code}}"})
        ET.SubElement(app, "provider", {ns + "name": "com.defold.inmobicmp.InMobiCmpInitializer",
                                       ns + "authorities": "{{android.package}}.inmobi_cmp_init",
                                       ns + "exported": "false"})
        ET.indent(tree, space="    ")
        (ROOT / "inmobi_cmp/manifests/android/AndroidManifest.xml").write_bytes(
            ET.tostring(tree, encoding="utf-8", xml_declaration=True))
        for name in ("R.txt", "proguard.txt"):
            if name in aar.namelist():
                (ROOT / "sdk" / name).write_bytes(aar.read(name))
    checksums = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                 for p in (archive, ROOT / f"sdk/inmobicmp-{VERSION}.aar")}
    (ROOT / "sdk/checksums.json").write_text(json.dumps(checksums, indent=4) + "\n", encoding="utf-8")
    print(f"Prepared local InMobi CMP {VERSION}")


if __name__ == "__main__":
    prepare()
