"""Build the standalone example with the current stable Defold release."""
import argparse
import json
import re
from pathlib import Path
import subprocess
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STABLE_INFO_URL = "https://d.defold.com/stable/info.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android", "windows"])
    parser.add_argument("--variant", choices=["debug", "release"], default="debug")
    parser.add_argument("--settings", type=Path)
    args = parser.parse_args()
    with urllib.request.urlopen(STABLE_INFO_URL, timeout=30) as response:
        info = json.load(response)
    version = info["version"]
    sha = info["sha1"]
    if not isinstance(version, str) or not version or not isinstance(sha, str) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid Defold stable release metadata")
    label = f"{args.platform}-{args.variant}"
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    bob = cache / f"bob-{sha}.jar"
    if not bob.exists():
        print(f"Downloading Defold {version}", flush=True)
        download = bob.with_suffix(".jar.part")
        urllib.request.urlretrieve(f"https://d.defold.com/archive/{sha}/bob/bob.jar", download)
        download.replace(bob)
    command = ["java", "--enable-native-access=ALL-UNNAMED",
               "-Dcom.google.protobuf.use_unsafe_pre22_gencode=true", "-jar", str(bob),
               "--platform", "arm64-android" if args.platform == "android" else "x86_64-win32",
               "--variant", args.variant, "--archive",
               "--output", f"build/{label}",
               "--bundle-output", f"bundles/{label}"]
    if args.platform == "android":
        command += ["--architectures", "arm64-android,armv7-android"]
    if args.settings:
        # Bob mounts the settings directory; using the project root again
        # produces duplicate resource paths. Stage overrides in ignored cache.
        settings = cache / "local-build.project"
        settings.write_bytes(args.settings.resolve().read_bytes())
        command += ["--settings", str(settings)]
    command += ["resolve", "build", "bundle"]
    log_path = cache / f"{label}.log"
    print(f"Building {label} with Defold {version}; log: {log_path}", flush=True)
    with log_path.open("w", encoding="utf-8") as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    report = {"defold": version, "sha": sha, "platform": args.platform,
              "variant": args.variant, "exit_code": result.returncode}
    (cache / f"{label}.json").write_text(json.dumps(report, indent=4) + "\n")
    print("Build succeeded" if result.returncode == 0 else "Build failed; see log and platform log.txt")
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
