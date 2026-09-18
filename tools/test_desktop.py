"""Run the compiled Windows extension contract tests without leaving a process running."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    executable = ROOT / "bundles/windows-debug/InMobi CMP Example/InMobiCMPExample.exe"
    if not executable.is_file():
        raise SystemExit("Build first: python tools/build.py windows --variant debug")
    log_path = ROOT / ".cache/desktop-tests.log"
    command = [str(executable), "--config=inmobi_cmp.run_tests=1"]
    with log_path.open("w", encoding="utf-8") as log:
        try:
            result = subprocess.run(command, cwd=executable.parent, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=30,
                                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        except subprocess.TimeoutExpired:
            print(f"Test timed out; see {log_path}")
            return 1
    output = log_path.read_text(encoding="utf-8", errors="replace")
    passed = result.returncode == 0 and "CMP_DESKTOP_TESTS_PASSED" in output and "ERROR:SCRIPT" not in output
    print("CMP_DESKTOP_TESTS_PASSED" if passed else output)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
