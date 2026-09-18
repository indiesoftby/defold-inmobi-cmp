import com.defold.inmobicmp.InMobiCmpBridge;
import com.inmobi.cmp.core.model.ACData;
import com.inmobi.cmp.core.model.gbc.GoogleBasicConsents;
import com.inmobi.cmp.core.model.portalconfig.GBCConsentValue;
import com.inmobi.cmp.core.model.mspa.USRegulationData;
import com.inmobi.cmp.core.cmpapi.status.*;
import com.inmobi.cmp.model.*;
import java.lang.reflect.Field;
import java.util.*;
import org.json.JSONObject;

/** Uses actual SDK models; stand-ins cover only Android scheduling and preference access. */
public final class BridgeTest {
    static void drain(InMobiCmpBridge bridge) throws Exception {
        Field field = InMobiCmpBridge.class.getDeclaredField("handler");
        field.setAccessible(true);
        ((android.os.Handler) field.get(bridge)).drain();
    }

    static JSONObject event(InMobiCmpBridge bridge, String expected) throws Exception {
        JSONObject message = new JSONObject(Objects.requireNonNull(bridge.poll(), "Missing " + expected));
        assert message.getString("event").equals(expected) : message;
        return message.getJSONObject("data");
    }

    public static void main(String[] args) throws Exception {
        InMobiCmpBridge bridge = new InMobiCmpBridge(new android.app.Activity());
        assert bridge.initialize("").equals("invalid_p_code");
        assert bridge.initialize(" p- ").equals("invalid_p_code");
        assert bridge.initialize("runtime-only").equals("startup_configuration_required");
        assert bridge.show("gdpr").equals("not_ready");
        assert !new JSONObject(bridge.getStatus()).getBoolean("loaded");

        // A regional state that is unknown must not be serialized as false.
        bridge.onCmpLoaded(new PingReturn(null, false, CmpStatus.LOADING, DisplayStatus.HIDDEN,
                "2.0", "1", 10, null, null, null));
        drain(bridge);
        JSONObject loaded = event(bridge, "loaded");
        assert !loaded.has("gdpr_applies") && !loaded.has("us_regulation_applies") : loaded;
        assert !loaded.getBoolean("cmp_loaded");

        // Partial/refused choices must survive serialization without coercion.
        Map<Integer, Boolean> vendors = new HashMap<>();
        vendors.put(4, false); vendors.put(9, true);
        bridge.onNonIABVendorConsentGiven(new NonIABData(true, false, false, "metadata", vendors));
        assert bridge.poll() == null : "Callback ran outside the scheduled UI work";
        drain(bridge);
        JSONObject nonIab = event(bridge, "non_iab_consent");
        assert !nonIab.getBoolean("has_global_consent");
        assert !nonIab.getJSONObject("non_iab_vendor_consents").getBoolean("4");
        assert nonIab.getJSONObject("non_iab_vendor_consents").getBoolean("9");

        bridge.onGoogleVendorConsentGiven(new ACData("2~1.42~dv."));
        USRegulationData us = new USRegulationData();
        us.setSaleOptOut(1); us.setSharingOptOut(2); us.setGppString("test-gpp");
        bridge.onReceiveUSRegulationsConsent(us);
        bridge.onGoogleBasicConsentChange(new GoogleBasicConsents(GBCConsentValue.DENIED,
                GBCConsentValue.GRANTED, GBCConsentValue.DENIED, GBCConsentValue.GRANTED));
        drain(bridge);
        assert event(bridge, "additional_consent").getString("ac_string").equals("2~1.42~dv.");
        JSONObject usData = event(bridge, "us_consent");
        assert usData.getInt("sale_opt_out") == 1 && usData.getInt("sharing_opt_out") == 2;
        assert event(bridge, "google_basic_consent").getString("ad_storage").equals("DENIED");

        androidx.preference.PreferenceManager.DATA.put("IABTCF_gdprApplies", 0);
        androidx.preference.PreferenceManager.DATA.put("IABTCF_TCString", "saved");
        androidx.preference.PreferenceManager.DATA.put("unrelated_private_value", "must not leak");
        JSONObject snapshot = new JSONObject(bridge.getConsent());
        JSONObject storage = snapshot.getJSONObject("storage");
        assert storage.getInt("IABTCF_gdprApplies") == 0;
        assert storage.getString("IABTCF_TCString").equals("saved");
        assert !storage.has("unrelated_private_value");
        assert snapshot.getJSONObject("us").getInt("sale_opt_out") == 1;
        assert !snapshot.getJSONObject("non_iab").getBoolean("has_global_consent");

        // Producers may call SDK callbacks from different threads; all events must arrive.
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 4; ++i) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 100; ++j) bridge.onUserMovedToOtherState();
            });
            threads.add(thread); thread.start();
        }
        for (Thread thread : threads) thread.join();
        drain(bridge);
        for (int i = 0; i < 400; ++i) event(bridge, "region_changed");
        assert bridge.poll() == null;

        bridge.onCmpError(ChoiceError.NO_CONNECTION);
        drain(bridge);
        assert event(bridge, "error").getString("code").equals("NO_CONNECTION");
        bridge.onUserMovedToOtherState();
        bridge.shutdown();
        bridge.onGoogleVendorConsentGiven(new ACData("late"));
        drain(bridge);
        assert bridge.poll() == null : "Event escaped after finalization";
        assert bridge.initialize("unused").equals("finalized");
        assert bridge.show("gdpr").equals("finalized");
        // Device regression: SDK can leave its last UI event at VISIBLE after closing.
        final boolean[] focused = {false};
        android.app.Activity host = new android.app.Activity() {
            @Override public boolean hasWindowFocus() { return focused[0]; }
        };
        InMobiCmpBridge reopening = new InMobiCmpBridge(host);
        for (String name : new String[]{"loaded", "uiVisible"}) {
            Field field = InMobiCmpBridge.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(reopening, true);
        }
        assert reopening.show("gdpr").equals("busy");
        focused[0] = true;
        assert reopening.show("gdpr") == null : "Stale VISIBLE blocked reopening";
        assert reopening.show("gdpr").equals("busy") : "Pending request was duplicated";
        reopening.shutdown();
        System.out.println("CMP_BRIDGE_TESTS_PASSED");
    }
}
