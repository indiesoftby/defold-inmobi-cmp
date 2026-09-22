"""Check startup ordering using the production provider/bridge and lifecycle stand-ins."""
from pathlib import Path
import os
import subprocess
import test_bridge

ROOT = test_bridge.ROOT


def main():
    # Fetch the host test dependencies and first test the bridge with real SDK models.
    test_bridge.main()
    cache = ROOT / ".cache/bootstrap-tests"
    sources = dict(test_bridge.STUBS)
    sources.update({
        "android/os/Looper.java": """package android.os;
public class Looper {
    public static final Looper MAIN = new Looper();
    public static boolean mainThread = true;
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return mainThread ? MAIN : null; }
}
""",
        "android/content/Context.java": """package android.content;
public class Context {
    public Context getApplicationContext() { return android.app.Application.APP; }
    public String getPackageName() { return "com.example.test"; }
    public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
}
""",
        "android/app/Activity.java": """package android.app;
public class Activity extends android.content.Context {
    public boolean isFinishing() { return false; }
    public boolean hasWindowFocus() { return true; }
    public Application getApplication() { return Application.APP; }
}
""",
        "android/app/Application.java": """package android.app;
public class Application extends android.content.Context {
    public static final Application APP = new Application();
    public final java.util.List<ActivityLifecycleCallbacks> callbacks = new java.util.ArrayList<>();
    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks cb) { callbacks.add(cb); }
    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks cb) { callbacks.remove(cb); }
    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity a, android.os.Bundle b);
        void onActivityStarted(Activity a); void onActivityResumed(Activity a);
        void onActivityPaused(Activity a); void onActivityStopped(Activity a);
        void onActivitySaveInstanceState(Activity a, android.os.Bundle b);
        void onActivityDestroyed(Activity a);
    }
}
""",
        "android/os/Bundle.java": "package android.os; public class Bundle extends java.util.HashMap<String,Object> {}",
        "android/content/pm/ApplicationInfo.java": """package android.content.pm;
public class ApplicationInfo { public android.os.Bundle metaData = new android.os.Bundle(); }
""",
        "android/content/pm/PackageManager.java": """package android.content.pm;
public class PackageManager {
    public static final int GET_META_DATA = 128;
    public static final ApplicationInfo INFO = new ApplicationInfo();
    public ApplicationInfo getApplicationInfo(String name, int flags) throws NameNotFoundException { return INFO; }
    public static class NameNotFoundException extends Exception {}
}
""",
        "android/content/ContentProvider.java": """package android.content;
public abstract class ContentProvider {
    public Context getContext() { return android.app.Application.APP; }
    public abstract boolean onCreate();
    public abstract android.database.Cursor query(android.net.Uri u, String[] p, String s, String[] a, String o);
    public abstract String getType(android.net.Uri u);
    public abstract android.net.Uri insert(android.net.Uri u, ContentValues v);
    public abstract int delete(android.net.Uri u, String s, String[] a);
    public abstract int update(android.net.Uri u, ContentValues v, String s, String[] a);
}
""",
        "android/content/ContentValues.java": "package android.content; public class ContentValues {}",
        "android/database/Cursor.java": "package android.database; public interface Cursor {}",
        "android/net/Uri.java": "package android.net; public class Uri {}",
        "android/util/Log.java": "package android.util; public class Log { public static int e(String t,String m,Throwable e) { return 0; } }",
        # The real SDK models remain on the classpath. Only the external SDK startup
        # boundary is substituted so this test can simulate Android event ordering.
        "com/inmobi/cmp/ChoiceCmp.java": """package com.inmobi.cmp;
import android.app.*;
import com.inmobi.cmp.core.model.*;
import com.inmobi.cmp.model.*;
public class ChoiceCmp {
    public static int starts;
    public static boolean sawActivityStarted;
    public static String code;
    public static void startChoice(Application app, String pkg, String pCode, ChoiceCmpCallback cb,
                                   com.inmobi.cmp.data.model.ChoiceStyle style) {
        starts++; code = pCode;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, android.os.Bundle b) {}
            public void onActivityStarted(Activity a) { sawActivityStarted = true; }
            public void onActivityResumed(Activity a) {} public void onActivityPaused(Activity a) {}
            public void onActivityStopped(Activity a) {} public void onActivityDestroyed(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
        });
    }
    public static String getSDKVersion() { return "test-boundary"; }
    public static void forceDisplayUI(Activity a) {}
    public static void showUSRegulationScreen(Activity a) {}
    public static GDPRData getGDPRData(java.util.Set<Integer> ids) { return null; }
    public static NonIABData getNonIABData(java.util.Set<Integer> ids) { return null; }
}
""",
    })
    sources["com/inmobi/cmp/presentation/components/CmpActivity.java"] = (
        "package com.inmobi.cmp.presentation.components; "
        "public class CmpActivity extends android.app.Activity {}"
    )
    paths = []
    for name, source in sources.items():
        path = cache / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
        paths.append(path)
    paths += list((ROOT / "inmobi_cmp/src/java/com/defold/inmobicmp").glob("*.java"))
    paths.append(ROOT / "tests/java/BootstrapTest.java")
    paths.append(ROOT / "tests/java/CmpFlowTest.java")
    classes = cache / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    jars = [ROOT / "inmobi_cmp/lib/android/inmobicmp.jar",
            test_bridge.CACHE / "android-json-0.0.20131108.vaadin1.jar",
            test_bridge.CACHE / "kotlin-stdlib-1.8.22.jar",
            test_bridge.CACHE / "iabgpp-encoder-3.2.3.jar"]
    classpath = os.pathsep.join(map(str, jars))
    subprocess.run(["javac", "-encoding", "UTF-8", "--release", "8", "-cp", classpath,
                    "-d", str(classes), *map(str, paths)], check=True)
    subprocess.run(["java", "-ea", "-cp", str(classes) + os.pathsep + classpath, "BootstrapTest"], check=True)
    subprocess.run(["java", "-ea", "-cp", str(classes) + os.pathsep + classpath, "CmpFlowTest"], check=True)


if __name__ == "__main__":
    main()
