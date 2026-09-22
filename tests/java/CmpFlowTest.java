import android.app.*;
import com.defold.inmobicmp.*;
import com.inmobi.cmp.model.*;
import com.inmobi.cmp.core.cmpapi.status.*;
import com.inmobi.cmp.core.model.GDPRData;
import com.inmobi.cmp.presentation.components.CmpActivity;
import com.iab.gpp.encoder.GppModel;
import java.util.*;
import org.json.JSONObject;

public class CmpFlowTest {
    static JSONObject status(InMobiCmpBridge b) throws Exception { return new JSONObject(b.getStatus()); }
    public static void main(String[] args) throws Exception {
        Application app = Application.APP;
        InMobiCmpBridge b = new InMobiCmpBridge(new Activity());
        b.onCmpLoaded(new PingReturn(true, true, CmpStatus.LOADED, DisplayStatus.HIDDEN,
                "2.0", "2021", 10, 5, 177, false));
        assert !status(b).getBoolean("flow_ready") : "loaded isn't prompt completion";
        b.onCMPUIStatusChanged(new DisplayInfo(DisplayStatus.HIDDEN, "no need", Regulations.GDPR, false));
        assert status(b).getBoolean("flow_ready");
        CmpActivity form = new CmpActivity();
        for (Application.ActivityLifecycleCallbacks cb : new ArrayList<>(app.callbacks)) cb.onActivityCreated(form, null);
        assert !status(b).getBoolean("flow_ready");
        b.onReceiveUSRegulationsConsent(new com.inmobi.cmp.core.model.mspa.USRegulationData());
        assert !status(b).getBoolean("flow_ready") : "must wait until the form closes";
        for (Application.ActivityLifecycleCallbacks cb : new ArrayList<>(app.callbacks)) cb.onActivityDestroyed(form);
        assert status(b).getBoolean("flow_ready") : "missing HIDDEN must not deadlock";
        b.onCmpError(ChoiceError.FAILED_LOGO_DOWNLOAD);
        assert status(b).getBoolean("flow_ready");
        b.onUserMovedToOtherState();
        assert !status(b).getBoolean("flow_ready");
        GppModel gpp = new GppModel();
        gpp.setFieldValue(7, "SaleOptOut", 1);
        gpp.setFieldValue(7, "SharingOptOut", 2);
        gpp.setFieldValue(7, "TargetedAdvertisingOptOut", 2);
        androidx.preference.PreferenceManager.DATA.put("IABGPP_HDR_GppString", gpp.encode());
        androidx.preference.PreferenceManager.DATA.put("IABGPP_GppSID", "7");
        JSONObject us = new JSONObject(b.getConsent()).getJSONObject("us_privacy");
        assert us.getBoolean("known") && us.getBoolean("opt_out");
        gpp.setFieldValue(7, "SaleOptOut", 2);
        androidx.preference.PreferenceManager.DATA.put("IABGPP_HDR_GppString", gpp.encode());
        assert !new JSONObject(b.getConsent()).getJSONObject("us_privacy").getBoolean("opt_out");
        androidx.preference.PreferenceManager.DATA.put("IABGPP_GppSID", "-1");
        assert !new JSONObject(b.getConsent()).getJSONObject("us_privacy").getBoolean("known");
        b.onCmpError(ChoiceError.NO_CONNECTION);
        assert !status(b).getBoolean("flow_ready");
        b.shutdown();
        assert app.callbacks.isEmpty();
        System.out.println("CMP lifecycle and GPP tests passed");
    }
}
