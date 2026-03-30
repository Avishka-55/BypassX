package com.bypassx.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class AuthSessionManager {

    private static final String PREF_NAME = "bypassx_prefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_AUTH_USER_NAME = "auth_user_name";
    private static final String KEY_AUTH_USER_EMAIL = "auth_user_email";

    private AuthSessionManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveSession(Context context, String token, String name, String email) {
        prefs(context).edit()
                .putString(KEY_AUTH_TOKEN, token)
                .putString(KEY_AUTH_USER_NAME, name)
                .putString(KEY_AUTH_USER_EMAIL, email)
                .apply();
    }

    public static boolean hasSession(Context context) {
        String token = getToken(context);
        return token != null && !token.trim().isEmpty();
    }

    public static String getToken(Context context) {
        return prefs(context).getString(KEY_AUTH_TOKEN, "");
    }

    public static String getUserName(Context context) {
        return prefs(context).getString(KEY_AUTH_USER_NAME, "");
    }

    public static String getUserEmail(Context context) {
        return prefs(context).getString(KEY_AUTH_USER_EMAIL, "");
    }

    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_AUTH_USER_NAME)
                .remove(KEY_AUTH_USER_EMAIL)
                .apply();
    }
}
