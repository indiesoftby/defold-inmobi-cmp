"""Run host-JVM behavior tests of the real Java bridge and local SDK models."""
from pathlib import Path
import os
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".cache/bridge-tests"

STUBS = {
    "android/content/SharedPreferences.java": """package android.content;
public interface SharedPreferences { java.util.Map<String, ?> getAll(); }
""",
    "android/content/Context.java": """package android.content;
public class Context {
    public Context getApplicationContext() { return this; }
    public String getPackageName() { return "com.example.test"; }
}
""",
    "android/app/Application.java": """package android.app;
public class Application extends android.content.Context {}
""",
    "android/app/Activity.java": """package android.app;
public class Activity extends android.content.Context {
    public boolean isFinishing() { return false; }
    public boolean hasWindowFocus() { return true; }
    public Application getApplication() { return new Application(); }
}
""",
    "android/os/Looper.java": """package android.os;
public class Looper {
    public static Looper getMainLooper() { return new Looper(); }
    public static Looper myLooper() { return null; }
}
""",
    "android/os/Handler.java": """package android.os;
public class Handler {
    private final java.util.Queue<Runnable> queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public Handler(Looper looper) {}
    public boolean post(Runnable runnable) { queue.add(runnable); return true; }
    public void removeCallbacksAndMessages(Object token) { queue.clear(); }
    public void drain() { Runnable runnable; while ((runnable = queue.poll()) != null) runnable.run(); }
}
""",
    "androidx/preference/PreferenceManager.java": """package androidx.preference;
public class PreferenceManager {
    public static final java.util.Map<String, Object> DATA = new java.util.HashMap<>();
    public static android.content.SharedPreferences getDefaultSharedPreferences(android.content.Context context) {
        return () -> new java.util.HashMap<>(DATA);
    }
}
""",
}


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    jars = [ROOT / "inmobi_cmp/lib/android/inmobicmp.jar"]
    for path in ["com/vaadin/external/google/android-json/0.0.20131108.vaadin1/android-json-0.0.20131108.vaadin1.jar",
                 "org/jetbrains/kotlin/kotlin-stdlib/1.8.22/kotlin-stdlib-1.8.22.jar"]:
        target = CACHE / Path(path).name
        if not target.exists():
            urllib.request.urlretrieve("https://repo.maven.apache.org/maven2/" + path, target)
        jars.append(target)
    sources = []
    for name, source in STUBS.items():
        target = CACHE / "src" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source, encoding="utf-8")
        sources.append(target)
    sources += [ROOT / "inmobi_cmp/src/java/com/defold/inmobicmp/InMobiCmpBridge.java",
                ROOT / "tests/java/BridgeTest.java"]
    classes = CACHE / "classes"
    classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join(map(str, jars))
    subprocess.run(["javac", "-encoding", "UTF-8", "--release", "8", "-cp", classpath,
                    "-d", str(classes), *map(str, sources)], check=True)
    subprocess.run(["java", "-ea", "-cp", str(classes) + os.pathsep + classpath, "BridgeTest"], check=True)


if __name__ == "__main__":
    main()
