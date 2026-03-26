package com.bypassx.app;

import static android.content.Context.RECEIVER_EXPORTED;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CONFIG_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Base64;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.dev7.lib.v2ray.V2rayController;
import dev.dev7.lib.v2ray.services.V2rayVPNService;
import dev.dev7.lib.v2ray.utils.Utilities;
import dev.dev7.lib.v2ray.utils.V2rayConfigs;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class BypassXTileService extends TileService {

    private static final String PREF_NAME = "bypassx_prefs";
    private static final String PREF_SUBSCRIPTION_CACHE = "subscription_cache";
    private static final String PREF_SELECTED_PACKAGE_KEY = "selected_package_key";
    private static final String PREF_PROXY_TETHERING_ENABLED = "proxy_tethering_enabled";
    private static final String PREF_SPLIT_TUNNEL_ENABLED = "split_tunnel_enabled";
    private static final String PREF_SPLIT_TUNNEL_APPS = "split_tunnel_apps";

    private final Map<String, String> androidPackageNames = new HashMap<>();
    private BroadcastReceiver stateReceiver;
    private boolean isReceiverRegistered;
    private V2rayConstants.CONNECTION_STATES lastKnownState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeAndroidPackageNames();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onStartListening() {
        super.onStartListening();
        registerStateReceiver();
        updateTileState(getCurrentStateForTile());
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        unregisterStateReceiver();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isVpnServiceRunning()) {
            V2rayController.stopV2ray(this);
            updateTileState(V2rayConstants.CONNECTION_STATES.DISCONNECTED);
            return;
        }
        startVpnFromTile();
    }

    private void startVpnFromTile() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String selectedPackageKey = prefs.getString(PREF_SELECTED_PACKAGE_KEY, null);
        if (selectedPackageKey == null || selectedPackageKey.trim().isEmpty()) {
            Toast.makeText(this, R.string.qs_tile_select_package_required, Toast.LENGTH_SHORT).show();
            openMainActivityAndCollapse();
            updateTileState(V2rayConstants.CONNECTION_STATES.DISCONNECTED);
            return;
        }

        String rawCache = prefs.getString(PREF_SUBSCRIPTION_CACHE, null);
        String selectedConfig = parseConfigs(rawCache).get(selectedPackageKey);
        if (selectedConfig == null || selectedConfig.trim().isEmpty()) {
            Toast.makeText(this, R.string.qs_tile_config_missing, Toast.LENGTH_SHORT).show();
            openMainActivityAndCollapse();
            updateTileState(V2rayConstants.CONNECTION_STATES.DISCONNECTED);
            return;
        }

        if (!V2rayController.isPreparedForConnection(this)) {
            Toast.makeText(this, R.string.qs_tile_permission_required, Toast.LENGTH_SHORT).show();
            openMainActivityAndCollapse();
            updateTileState(V2rayConstants.CONNECTION_STATES.DISCONNECTED);
            return;
        }

        try {
            String startConfig = selectedConfig;
            if (prefs.getBoolean(PREF_PROXY_TETHERING_ENABLED, false)) {
                startConfig = makeProxyTetheringReadyConfig(selectedConfig);
            }

            ArrayList<String> blockedApps = null;
            if (prefs.getBoolean(PREF_SPLIT_TUNNEL_ENABLED, false)) {
                blockedApps = getBlockedApplicationsList(prefs);
            }

            if (V2rayConfigs.serviceMode != V2rayConstants.SERVICE_MODES.VPN_MODE) {
                V2rayController.toggleConnectionMode();
            }

            Utilities.copyAssets(this);
            V2rayConfigs.currentConfig.applicationIcon = R.drawable.ic_launcher;
            V2rayConfigs.currentConfig.applicationName = getString(R.string.app_name);

            if (!Utilities.refillV2rayConfig(selectedPackageKey, startConfig, blockedApps)) {
                Toast.makeText(this, R.string.qs_tile_failed_to_start, Toast.LENGTH_SHORT).show();
                updateTileState(V2rayConstants.CONNECTION_STATES.DISCONNECTED);
                return;
            }

            Intent startIntent = new Intent(this, V2rayVPNService.class);
            startIntent.setPackage(getPackageName());
            startIntent.putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.START_SERVICE);
            startIntent.putExtra(V2RAY_SERVICE_CONFIG_EXTRA, V2rayConfigs.currentConfig);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                startForegroundService(startIntent);
            } else {
                startService(startIntent);
            }

            updateTileState(V2rayConstants.CONNECTION_STATES.CONNECTED);
        } catch (Exception ignored) {
            Toast.makeText(this, R.string.qs_tile_failed_to_start, Toast.LENGTH_SHORT).show();
            updateTileState(V2rayConstants.CONNECTION_STATES.DISCONNECTED);
        }
    }

    private void openMainActivityAndCollapse() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
            return;
        }

        startActivityAndCollapse(intent);
    }

    private void updateTileState(V2rayConstants.CONNECTION_STATES state) {
        lastKnownState = state;
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        tile.setLabel(getString(R.string.qs_tile_label));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(getSubtitleForState(state));
        }

        switch (state) {
            case CONNECTED:
                tile.setState(Tile.STATE_ACTIVE);
                break;
            case CONNECTING:
                tile.setState(Tile.STATE_ACTIVE);
                break;
            case DISCONNECTED:
            default:
                tile.setState(Tile.STATE_INACTIVE);
                break;
        }
        tile.updateTile();
    }

    private V2rayConstants.CONNECTION_STATES getCurrentStateForTile() {
        if (isVpnServiceRunning()) {
            return V2rayConstants.CONNECTION_STATES.CONNECTED;
        }
        return V2rayConstants.CONNECTION_STATES.DISCONNECTED;
    }

    private boolean isVpnServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }
        List<ActivityManager.RunningServiceInfo> runningServices = activityManager.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo serviceInfo : runningServices) {
            String className = serviceInfo.service.getClassName();
            if (V2rayVPNService.class.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private String getSubtitleForState(V2rayConstants.CONNECTION_STATES state) {
        switch (state) {
            case CONNECTED:
                return getString(R.string.qs_tile_state_connected);
            case CONNECTING:
                return getString(R.string.qs_tile_state_connecting);
            case DISCONNECTED:
            default:
                return getString(R.string.qs_tile_state_disconnected);
        }
    }

    private void initializeAndroidPackageNames() {
        androidPackageNames.put("youtube", "com.google.android.youtube");
        androidPackageNames.put("youtube_revanced", "app.revanced.android.youtube");
        androidPackageNames.put("zoom", "us.zoom.videomeetings");
        androidPackageNames.put("whatsapp", "com.whatsapp");
        androidPackageNames.put("viber", "com.viber.voip");
        androidPackageNames.put("netflix", "com.netflix.mediaclient");
        androidPackageNames.put("instagram", "com.instagram.android");
    }

    private ArrayList<String> getBlockedApplicationsList(SharedPreferences prefs) {
        ArrayList<String> blockedApps = new ArrayList<>();
        String saved = prefs.getString(PREF_SPLIT_TUNNEL_APPS, "");
        if (saved == null || saved.isEmpty()) {
            return blockedApps;
        }

        String[] parts = saved.split("\\|");
        for (String part : parts) {
            String[] kv = part.split(":");
            if (kv.length != 2) {
                continue;
            }
            String androidPkg = androidPackageNames.get(kv[0]);
            if (androidPkg != null) {
                blockedApps.add(androidPkg);
            }
        }
        return blockedApps;
    }

    private String makeProxyTetheringReadyConfig(String config) {
        try {
            String normalized = Utilities.normalizeV2rayFullConfig(config);
            return normalized.replace("\"listen\": \"127.0.0.1\"", "\"listen\": \"0.0.0.0\"");
        } catch (Exception ignored) {
            return config;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStateReceiver() {
        if (isReceiverRegistered) {
            return;
        }

        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Object stateObject = intent.getSerializableExtra(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA);
                if (stateObject instanceof V2rayConstants.CONNECTION_STATES) {
                    updateTileState((V2rayConstants.CONNECTION_STATES) stateObject);
                }
            }
        };

        IntentFilter filter = new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }

        isReceiverRegistered = true;
    }

    private void unregisterStateReceiver() {
        if (!isReceiverRegistered || stateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {
        }
        isReceiverRegistered = false;
    }

    private Map<String, String> parseConfigs(String rawSubscription) {
        Map<String, String> parsedConfigs = new HashMap<>();
        if (rawSubscription == null || rawSubscription.trim().isEmpty()) {
            return parsedConfigs;
        }

        String[] lines = rawSubscription.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            appendVlessLinksFromText(trimmed, parsedConfigs);
            String decoded = decodeBase64(trimmed);
            if (decoded != null) {
                appendVlessLinksFromText(decoded, parsedConfigs);
            }
        }
        return parsedConfigs;
    }

    private void appendVlessLinksFromText(String text, Map<String, String> target) {
        String[] parts = text.split("\\r?\\n");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (!trimmedPart.startsWith("vless://") || !trimmedPart.contains("#")) {
                continue;
            }

            int packageNameIndex = trimmedPart.lastIndexOf('#');
            if (packageNameIndex == -1 || packageNameIndex == trimmedPart.length() - 1) {
                continue;
            }

            String packageName = trimmedPart.substring(packageNameIndex + 1).trim();
            String packageKey = normalizePackageName(packageName);
            target.put(packageKey, trimmedPart);
        }
    }

    private String decodeBase64(String encoded) {
        try {
            return new String(Base64.decode(encoded, Base64.DEFAULT));
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return new String(Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP));
        } catch (IllegalArgumentException ignored) {
        }

        try {
            String normalized = encoded.replace('-', '+').replace('_', '/').trim();
            int padding = normalized.length() % 4;
            if (padding > 0) {
                normalized = normalized + "====".substring(padding);
            }
            return new String(Base64.decode(normalized, Base64.NO_WRAP));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizePackageName(String packageName) {
        return packageName.toLowerCase(Locale.US)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
    }
}
