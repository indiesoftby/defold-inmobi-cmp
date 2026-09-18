import android.app.*;
import android.content.pm.PackageManager;
import com.defold.inmobicmp.*;
import com.inmobi.cmp.ChoiceCmp;
import java.util.ArrayList;

public final class BootstrapTest {
    public static void main(String[] args) {
        Application app = Application.APP;
        InMobiCmpInitializer dormant = new InMobiCmpInitializer();
        assert dormant.onCreate();
        assert app.callbacks.isEmpty() : "Empty p-code must not activate the SDK";

        PackageManager.INFO.metaData.put("com.defold.inmobicmp.P_CODE", "p-abcdefghijklm");
        InMobiCmpInitializer provider = new InMobiCmpInitializer();
        assert provider.onCreate();
        assert ChoiceCmp.starts == 0;
        Activity activity = new Activity();
        for (Application.ActivityLifecycleCallbacks cb : new ArrayList<>(app.callbacks))
            cb.onActivityCreated(activity, null);
        assert ChoiceCmp.starts == 1 : "SDK start must be synchronous during Activity creation";
        assert ChoiceCmp.code.equals("abcdefghijklm");
        for (Application.ActivityLifecycleCallbacks cb : new ArrayList<>(app.callbacks))
            cb.onActivityStarted(activity);
        assert ChoiceCmp.sawActivityStarted : "SDK missed the foreground event";

        InMobiCmpBridge bridge = InMobiCmpBridge.getInstance(activity);
        assert bridge.initialize("abcdefghijklm") == null;
        assert bridge.initialize("p-abcdefghijklm") == null;
        assert bridge.initialize("differentcode").equals("already_initialized");
        assert ChoiceCmp.starts == 1 : "Lua attachment must not initialize the SDK again";
        for (Application.ActivityLifecycleCallbacks cb : new ArrayList<>(app.callbacks))
            cb.onActivityCreated(new Activity(), null);
        assert ChoiceCmp.starts == 1 : "Provider must unregister its one-shot callback";
        bridge.shutdown();
        System.out.println("CMP_BOOTSTRAP_TESTS_PASSED");
    }
}
