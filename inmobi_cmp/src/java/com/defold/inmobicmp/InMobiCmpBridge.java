package com.defold.inmobicmp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import com.inmobi.cmp.ChoiceCmp;
import com.inmobi.cmp.ChoiceCmpCallback;
import com.inmobi.cmp.core.model.ACData;
import com.inmobi.cmp.core.model.GDPRData;
import com.inmobi.cmp.core.model.gbc.GoogleBasicConsents;
import com.inmobi.cmp.core.model.mspa.USRegulationData;
import com.inmobi.cmp.data.model.ChoiceStyle;
import com.inmobi.cmp.model.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.json.JSONArray;
import org.json.JSONObject;

/** Android UI work stays on the main looper; immutable JSON events are polled by Defold. */
public final class InMobiCmpBridge implements ChoiceCmpCallback {
    private static InMobiCmpBridge instance;
    private final WeakReference<Activity> activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
    private final SharedPreferences preferences;
    private volatile boolean active = true;
    private volatile boolean started;
    private volatile boolean loaded;
    private volatile boolean formRequested;
    private volatile boolean uiVisible;
    private volatile String pCode;
    private volatile String startupError;
    private boolean earlyStart;
    private volatile String status = "{\"supported\":true,\"initialized\":false,\"loaded\":false}";
    // Only modified on the UI thread. Serialized copies are published for the game thread.
    private final JSONObject state = new JSONObject();
    private final JSONObject consent = new JSONObject();
    private volatile String consentSnapshot = "{}";

    public InMobiCmpBridge(Activity activity) {
        this.activity = new WeakReference<>(activity);
        preferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
        put(state, "supported", true);
        put(state, "initialized", false);
        put(state, "loaded", false);
    }

    public static synchronized InMobiCmpBridge getInstance(Activity activity) {
        if (instance == null || !instance.active) instance = new InMobiCmpBridge(activity);
        return instance;
    }

    /** Called inside onActivityCreated, before InMobi's required onActivityStarted event. */
    static void startEarly(Activity activity, String code) {
        InMobiCmpBridge bridge = getInstance(activity);
        bridge.earlyStart = true;
        String error = bridge.initialize(code);
        if (error != null) bridge.startupError = error;
        bridge.earlyStart = false;
    }

    private static void put(JSONObject object, String key, Object value) {
        try { if (value != null && value != JSONObject.NULL) object.put(key, value); }
        catch (org.json.JSONException e) { throw new IllegalArgumentException(key, e); }
    }

    private static String snake(String name) {
        return name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    /** Public SDK getters avoid serializing the obfuscated private field names in the AAR. */
    private static Object encode(Object value, int depth) {
        if (value == null) return JSONObject.NULL;
        if (value instanceof String || value instanceof Boolean || value instanceof Number) return value;
        if (value instanceof Enum<?>) return ((Enum<?>) value).name();
        if (depth > 12) throw new IllegalArgumentException("SDK model exceeds serialization depth");
        if (value instanceof Map<?, ?>) {
            JSONObject result = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
                put(result, String.valueOf(entry.getKey()), encode(entry.getValue(), depth + 1));
            return result;
        }
        if (value instanceof Collection<?>) {
            JSONArray result = new JSONArray();
            for (Object item : (Collection<?>) value) result.put(encode(item, depth + 1));
            return result;
        }
        if (!value.getClass().getName().startsWith("com.inmobi.cmp."))
            throw new IllegalArgumentException("Unsupported SDK model " + value.getClass().getName());
        JSONObject result = new JSONObject();
        TreeMap<String, Method> getters = new TreeMap<>();
        for (Method method : value.getClass().getMethods()) {
            String name = method.getName();
            if (method.getParameterTypes().length != 0 || name.equals("getClass") || name.contains("$")) continue;
            if (name.startsWith("get") && name.length() > 3) getters.put(snake(name.substring(3)), method);
            else if (name.startsWith("is") && name.length() > 2) getters.put(snake(name.substring(2)), method);
        }
        for (Map.Entry<String, Method> getter : getters.entrySet()) {
            try { put(result, getter.getKey(), encode(getter.getValue().invoke(value), depth + 1)); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("SDK getter " + getter.getKey(), e); }
        }
        return result;
    }

    private void emit(String event, JSONObject data) {
        if (!active) return;
        JSONObject message = new JSONObject();
        put(message, "event", event);
        put(message, "data", data);
        events.add(message.toString());
    }

    private void error(String code, String message) {
        JSONObject data = new JSONObject();
        put(data, "code", code);
        put(data, "message", message);
        emit("error", data);
    }

    private void onMain(Runnable action) {
        Runnable guarded = () -> {
            if (!active) return;
            try { action.run(); }
            catch (RuntimeException e) { error("bridge_error", e.toString()); }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) guarded.run();
        else handler.post(guarded);
    }

    private void publishStatus() {
        put(state, "initialized", started);
        put(state, "loaded", loaded);
        status = state.toString();
    }

    /** Null means the asynchronous operation was accepted, otherwise a stable error code. */
    public synchronized String initialize(String code) {
        if (!active) return "finalized";
        if (code == null || code.trim().isEmpty()) return "invalid_p_code";
        code = code.trim();
        if (code.startsWith("p-")) code = code.substring(2);
        if (code.isEmpty()) return "invalid_p_code";
        if (started) return code.equals(pCode) ? null : "already_initialized";
        if (!earlyStart) return startupError != null ? startupError : "startup_configuration_required";
        final Activity current = activity.get();
        if (current == null || current.isFinishing()) return "activity_unavailable";
        pCode = code;
        started = true;
        onMain(() -> {
            publishStatus();
            try {
                ChoiceCmp.startChoice(current.getApplication(), current.getPackageName(), pCode, this, new ChoiceStyle());
            } catch (RuntimeException e) {
                started = false;
                startupError = "initialization_failed";
                publishStatus();
                error("initialization_failed", e.toString());
            }
        });
        return null;
    }

    public synchronized String show(String regulation) {
        if (!active) return "finalized";
        if (!loaded) return "not_ready";
        if (formRequested) return "busy";
        final Activity current = activity.get();
        if (current == null || current.isFinishing()) return "activity_unavailable";
        // SDK 2.4.3 can close CmpActivity without a HIDDEN callback. Once
        // the host regains focus, the last VISIBLE event must not block reopening.
        if (uiVisible && !current.hasWindowFocus()) return "busy";
        formRequested = true;
        onMain(() -> {
            try {
                if ("gdpr".equals(regulation)) ChoiceCmp.forceDisplayUI(current);
                else ChoiceCmp.showUSRegulationScreen(current);
            } catch (RuntimeException e) {
                error("show_failed", e.toString());
            } finally {
                formRequested = false;
            }
        });
        return null;
    }

    public String getStatus() { return status; }
    public String getSdkVersion() { return ChoiceCmp.getSDKVersion(); }

    /** Include only standard consent storage, never unrelated application preferences. */
    public String getConsent() {
        try {
            JSONObject result = new JSONObject(consentSnapshot);
            JSONObject iab = new JSONObject();
            for (Map.Entry<String, ?> item : preferences.getAll().entrySet()) {
                String key = item.getKey();
                if (key.startsWith("IABTCF_") || key.startsWith("IABGPP_") || key.equals("IABUSPrivacy_String"))
                    put(iab, key, encode(item.getValue(), 0));
            }
            put(result, "storage", iab);
            return result.toString();
        } catch (RuntimeException | org.json.JSONException e) {
            error("serialization_failed", e.toString());
            return "{}";
        }
    }

    public String poll() { return events.poll(); }

    public void shutdown() {
        active = false;
        events.clear();
        activity.clear();
        handler.removeCallbacksAndMessages(null);
    }

    private void modelEvent(String event, String key, Object model) {
        onMain(() -> {
            JSONObject data = (JSONObject) encode(model, 0);
            if (key != null) {
                put(consent, key, data);
                consentSnapshot = consent.toString();
            }
            emit(event, data);
        });
    }

    @Override public void onCmpLoaded(PingReturn info) {
        onMain(() -> {
            loaded = info.getCmpLoaded();
            JSONObject data = (JSONObject) encode(info, 0);
            put(state, "cmp", data);
            publishStatus();
            // Recover the SDK's persisted choice as well as later callback updates.
            if (loaded) {
                put(consent, "gdpr", encode(ChoiceCmp.getGDPRData(null), 0));
                put(consent, "non_iab", encode(ChoiceCmp.getNonIABData(null), 0));
                consentSnapshot = consent.toString();
            }
            emit("loaded", data);
        });
    }

    @Override public void onCMPUIStatusChanged(DisplayInfo info) {
        onMain(() -> {
            uiVisible = "VISIBLE".equals(info.getDisplayStatus().name());
            JSONObject data = (JSONObject) encode(info, 0);
            put(state, "ui", data);
            publishStatus();
            emit("ui_changed", data);
        });
    }
    @Override public void onIABVendorConsentGiven(GDPRData data) { modelEvent("gdpr_consent", "gdpr", data); }
    @Override public void onNonIABVendorConsentGiven(NonIABData data) { modelEvent("non_iab_consent", "non_iab", data); }
    @Override public void onGoogleVendorConsentGiven(ACData data) { modelEvent("additional_consent", "additional", data); }
    @Override public void onReceiveUSRegulationsConsent(USRegulationData data) { modelEvent("us_consent", "us", data); }
    @Override public void onGoogleBasicConsentChange(GoogleBasicConsents data) { modelEvent("google_basic_consent", "google_basic", data); }
    @Override public void onUserMovedToOtherState() { onMain(() -> emit("region_changed", new JSONObject())); }
    @Override public void onActionButtonClicked(ActionButton button) {
        onMain(() -> { JSONObject data = new JSONObject(); put(data, "action", button.name()); emit("action", data); });
    }
    @Override public void onCCPAConsentGiven(String value) {
        onMain(() -> { JSONObject data = new JSONObject(); put(data, "usp_string", value);
            put(consent, "legacy_ccpa", data); consentSnapshot = consent.toString(); emit("legacy_ccpa_consent", data); });
    }
    @Override public void onCmpError(ChoiceError value) {
        onMain(() -> {
            if (value == ChoiceError.INVALID_PCODE) {
                started = false;
                startupError = "invalid_p_code";
                publishStatus();
            }
            error(value.name(), value.getMessage());
        });
    }
}
