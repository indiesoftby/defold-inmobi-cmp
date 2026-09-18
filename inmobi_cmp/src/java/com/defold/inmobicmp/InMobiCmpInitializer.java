package com.defold.inmobicmp;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** Installs the SDK before the first host Activity starts, without replacing Application. */
public final class InMobiCmpInitializer extends ContentProvider {
    @Override public boolean onCreate() {
        if (getContext() == null) return false;
        final Application app = (Application) getContext().getApplicationContext();
        final String code;
        try {
            Bundle metadata = app.getPackageManager().getApplicationInfo(
                    app.getPackageName(), PackageManager.GET_META_DATA).metaData;
            Object value = metadata == null ? null : metadata.get("com.defold.inmobicmp.P_CODE");
            code = value == null ? "" : value.toString().trim();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("inmobi_cmp", "Cannot read CMP startup configuration", e);
            return false;
        }
        if (code.isEmpty()) return true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedState) {
                // SDK callbacks are installed before Android dispatches onActivityStarted.
                InMobiCmpBridge.startEarly(activity, code);
                app.unregisterActivityLifecycleCallbacks(this);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
        return true;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
