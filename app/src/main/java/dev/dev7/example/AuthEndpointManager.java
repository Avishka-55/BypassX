package com.bypassx.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class AuthEndpointManager {

    private static final String PREF_NAME = "bypassx_prefs";
    private static final String KEY_AUTH_BASE_URL_OVERRIDE = "auth_base_url_override";

    private AuthEndpointManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setOverrideUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_AUTH_BASE_URL_OVERRIDE, sanitize(url)).apply();
    }

    public static void clearOverrideUrl(Context context) {
        prefs(context).edit().remove(KEY_AUTH_BASE_URL_OVERRIDE).apply();
    }

    public static String getOverrideUrl(Context context) {
        return sanitize(prefs(context).getString(KEY_AUTH_BASE_URL_OVERRIDE, ""));
    }

    public static String getEffectiveBaseUrl(Context context) {
        String override = getOverrideUrl(context);
        if (!override.isEmpty()) {
            return override;
        }
        return sanitize(BuildConfig.AUTH_BASE_URL);
    }

    private static String sanitize(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
