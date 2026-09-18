"""Build the standalone example with a pinned Defold version."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.13.1"
SHA = "574678c7d44be490d874fbed2d0ae6211feec4d9"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android", "windows"])
    parser.add_argument("--variant", choices=["debug", "release"], default="debug")
    parser.add_argument("--settings", type=Path)
    parser.add_argument("--r8", action="store_true", help="Validate R8 on pinned Defold 1.14.0 alpha (Android release only)")
    args = parser.parse_args()
    if args.r8 and (args.platform != "android" or args.variant != "release"):
        parser.error("--r8 requires android --variant release")
    version = "1.14.0-alpha" if args.r8 else VERSION
    sha = "9ca5465caa34c4872c3dcad260fbed3ea35f5c6a" if args.r8 else SHA
    label = f"{args.platform}-{args.variant}" + ("-r8" if args.r8 else "")
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    bob = cache / f"bob-{version}.jar"
    if not bob.exists():
        print(f"Downloading Defold {version}", flush=True)
        urllib.request.urlretrieve(f"https://d.defold.com/archive/{sha}/bob/bob.jar", bob)
    command = ["java", "--enable-native-access=ALL-UNNAMED",
               "-Dcom.google.protobuf.use_unsafe_pre22_gencode=true", "-jar", str(bob),
               "--platform", "arm64-android" if args.platform == "android" else "x86_64-win32",
               "--variant", args.variant, "--archive",
               "--output", f"build/{label}",
               "--bundle-output", f"bundles/{label}"]
    if args.platform == "android":
        command += ["--architectures", "arm64-android,armv7-android"]
        if args.r8:
            settings = cache / "release.project"
            settings.write_text("[android]\nr8_keep_rules = /builtins/manifests/android/dmengine.keep\n", encoding="utf-8")
            command += ["--settings", str(settings)]
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
              "variant": args.variant, "r8": args.r8, "exit_code": result.returncode}
    (cache / f"{label}.json").write_text(json.dumps(report, indent=4) + "\n")
    print("Build succeeded" if result.returncode == 0 else "Build failed; see log and platform log.txt")
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
