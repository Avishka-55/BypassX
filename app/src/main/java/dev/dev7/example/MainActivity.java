package com.bypassx.app;

import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import dev.dev7.lib.v2ray.V2rayController;
import dev.dev7.lib.v2ray.utils.V2rayConfigs;
import dev.dev7.lib.v2ray.utils.V2rayConstants;
import dev.dev7.lib.v2ray.utils.Utilities;

public class MainActivity extends AppCompatActivity {

    private static final String SUBSCRIPTION_URL = BuildConfig.SUBSCRIPTION_URL;
    private static final String PREF_NAME = "bypassx_prefs";
    private static final String PREF_SUBSCRIPTION_CACHE = "subscription_cache";
    private static final String PREF_SELECTED_PACKAGE_KEY = "selected_package_key";
    private static final String PREF_LAST_SYNC_TIME = "last_sync_time";
    private static final String PREF_PROXY_TETHERING_ENABLED = "proxy_tethering_enabled";
    private static final String PREF_SPLIT_TUNNEL_ENABLED = "split_tunnel_enabled";
    private static final String PREF_SPLIT_TUNNEL_APPS = "split_tunnel_apps";
    private static final String DELAY_TEST_URL_PRIMARY = "https://www.gstatic.com/generate_204";
    private static final String DELAY_TEST_URL_FALLBACK = "https://www.google.com/generate_204";
    private static final String IP_INFO_URL = "https://api.ip.sb/geoip";
    private static final String LOCAL_PROXY_HOST = "127.0.0.1";
    private static final int LOCAL_SOCKS_PORT = 10808;
    private static final int NETWORK_TIMEOUT_MS = 6000;

    private final Map<String, String> packageConfigs = new HashMap<>();
    private final Map<String, MaterialCardView> packageCards = new HashMap<>();
    private final Map<String, String> splitTunnelApps = new HashMap<>();  // packageKey -> display name
    private final Map<String, String> androidPackageNames = new HashMap<>();  // packageKey -> android package name
    private String selectedPackageKey;

    private SharedPreferences sharedPreferences;
    private ExecutorService executorService;
    private BroadcastReceiver v2rayBroadcastReceiver;
    private boolean isV2rayReceiverRegistered = false;
    private V2rayConstants.CONNECTION_STATES lastObservedState = null;

    private MaterialButton connectButton;
    private MaterialButton pingButton;
    private TextView pingIpStatus;
    private TextView connectionStatus;
    private TextView subscriptionStatus;
    private TextView parsedPackagesDebug;
    private TextView lastSyncStatus;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar topToolbar;
    private ActionBarDrawerToggle drawerToggle;
    private LinearLayout proxyTetheringDrawer;
    private SwitchMaterial proxyTetheringSwitch;
    private MaterialButton proxyHotspotSettingsButton;
    private MaterialButton proxyGuideButton;
    private SwitchMaterial splitTunnelSwitch;
    private LinearLayout splitTunnelAppsContainer;
    private TextView splitTunnelStatus;
    private TextView proxyLogText;
    private final Handler proxyPanelHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder proxyEventLog = new StringBuilder();
    private final Runnable proxyPanelRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
                refreshProxyPanelLog();
                proxyPanelHandler.postDelayed(this, 1000);
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        V2rayController.init(this, R.drawable.ic_launcher, getString(R.string.app_name));
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        executorService = Executors.newSingleThreadExecutor();

        connectButton = findViewById(R.id.btn_connection);
        pingButton = findViewById(R.id.btn_ping);
        pingIpStatus = findViewById(R.id.ping_ip_status);
        connectionStatus = findViewById(R.id.connection_status);
        subscriptionStatus = findViewById(R.id.subscription_status);
        parsedPackagesDebug = findViewById(R.id.parsed_packages_debug);
        lastSyncStatus = findViewById(R.id.last_sync_status);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        topToolbar = findViewById(R.id.top_toolbar);
        proxyTetheringDrawer = findViewById(R.id.proxy_tethering_drawer);
        proxyTetheringSwitch = findViewById(R.id.proxy_tethering_switch);
        proxyHotspotSettingsButton = findViewById(R.id.proxy_hotspot_settings_button);
        proxyGuideButton = findViewById(R.id.proxy_guide_button);
        proxyLogText = findViewById(R.id.proxy_log_text);
        splitTunnelSwitch = findViewById(R.id.split_tunnel_switch);
        splitTunnelAppsContainer = findViewById(R.id.split_tunnel_apps_container);
        splitTunnelStatus = findViewById(R.id.split_tunnel_status);

        initializeAndroidPackageNames();
        setupNavigationDrawer();
        setupProxyTetheringPanel();
        setupSplitTunnelPanel();
        setupPackageCards();
        selectedPackageKey = sharedPreferences.getString(PREF_SELECTED_PACKAGE_KEY, null);
        refreshPackageSelectionUi();
        updateLastSyncUi();
        connectButton.setOnClickListener(v -> onConnectButtonClick());
        pingButton.setOnClickListener(v -> onPingButtonClick());

        registerV2rayReceiver();
        updateConnectionUi(V2rayController.getConnectionState());
        fetchSubscriptionConfigs();
    }

    private void setupNavigationDrawer() {
        setSupportActionBar(topToolbar);
        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                topToolbar,
                R.string.menu_home,
                R.string.menu_home
        );
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(android.view.View drawerView) {
                if (drawerView.getId() == R.id.proxy_tethering_drawer) {
                    startProxyPanelLiveRefresh();
                }
            }

            @Override
            public void onDrawerClosed(android.view.View drawerView) {
                if (drawerView.getId() == R.id.proxy_tethering_drawer) {
                    stopProxyPanelLiveRefresh();
                }
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                drawerLayout.closeDrawers();
                return true;
            }
            if (itemId == R.id.nav_about) {
                showAboutDialog();
                drawerLayout.closeDrawers();
                return true;
            }
            if (itemId == R.id.nav_contact) {
                openWhatsappContact();
                drawerLayout.closeDrawers();
                return true;
            }
            if (itemId == R.id.nav_proxy_tethering) {
                drawerLayout.closeDrawer(GravityCompat.START);
                drawerLayout.openDrawer(GravityCompat.END);
                return true;
            }
            if (itemId == R.id.nav_split_tunnel) {
                drawerLayout.closeDrawer(GravityCompat.START);
                drawerLayout.openDrawer(GravityCompat.END);
                return true;
            }
            if (itemId == R.id.nav_share) {
                Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
                drawerLayout.closeDrawers();
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_proxy_tethering) {
            drawerLayout.openDrawer(GravityCompat.END);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        startActivity(new Intent(this, AboutActivity.class));
    }

    private void openWhatsappContact() {
        Uri uri = Uri.parse("https://wa.me/94759106855");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.whatsapp");
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignore) {
                Toast.makeText(this, R.string.whatsapp_not_found, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupProxyTetheringPanel() {
        proxyTetheringSwitch.setChecked(sharedPreferences.getBoolean(PREF_PROXY_TETHERING_ENABLED, false));
        ensureVpnMode();

        appendProxyEvent(getString(R.string.proxy_tethering_hotspot_note));

        proxyTetheringSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isConnectionConfigLocked()) {
                proxyTetheringSwitch.setChecked(sharedPreferences.getBoolean(PREF_PROXY_TETHERING_ENABLED, false));
                return;
            }
            sharedPreferences.edit().putBoolean(PREF_PROXY_TETHERING_ENABLED, isChecked).apply();
            if (isChecked) {
                ensureVpnMode();
                appendProxyEvent(getString(R.string.proxy_tethering_enabled));
                appendProxyEvent(getString(R.string.proxy_tethering_mode_vpn));
            } else {
                ensureVpnMode();
                appendProxyEvent(getString(R.string.proxy_tethering_disabled));
            }
            refreshProxyPanelLog();
        });

        proxyHotspotSettingsButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                appendProxyEvent(getString(R.string.proxy_tethering_open_hotspot_settings));
                refreshProxyPanelLog();
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.proxy_tethering_settings_unavailable, Toast.LENGTH_SHORT).show();
            }
        });

        proxyGuideButton.setOnClickListener(v -> {
            appendProxyEvent(getString(R.string.proxy_tethering_guide));
            refreshProxyPanelLog();
        });

        refreshProxyPanelLog();
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

    private void setupSplitTunnelPanel() {
        boolean splitTunnelEnabled = sharedPreferences.getBoolean(PREF_SPLIT_TUNNEL_ENABLED, false);
        splitTunnelSwitch.setChecked(splitTunnelEnabled);
        loadSplitTunnelAppsFromPrefs();
        refreshSplitTunnelUi();

        splitTunnelSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isConnectionConfigLocked()) {
                splitTunnelSwitch.setChecked(sharedPreferences.getBoolean(PREF_SPLIT_TUNNEL_ENABLED, false));
                return;
            }
            sharedPreferences.edit().putBoolean(PREF_SPLIT_TUNNEL_ENABLED, isChecked).apply();
            if (isChecked) {
                refreshSplitTunnelUi();
            } else {
                splitTunnelApps.clear();
                saveSplitTunnelAppsToPrefs();
                refreshSplitTunnelUi();
            }
        });
    }

    private void loadSplitTunnelAppsFromPrefs() {
        String saved = sharedPreferences.getString(PREF_SPLIT_TUNNEL_APPS, "");
        splitTunnelApps.clear();
        if (!saved.isEmpty()) {
            String[] parts = saved.split("\\|");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    splitTunnelApps.put(kv[0], kv[1]);
                }
            }
        }
    }

    private void saveSplitTunnelAppsToPrefs() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : splitTunnelApps.entrySet()) {
            if (sb.length() > 0) sb.append("|");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        sharedPreferences.edit().putString(PREF_SPLIT_TUNNEL_APPS, sb.toString()).apply();
    }

    private void refreshSplitTunnelUi() {
        splitTunnelAppsContainer.removeAllViews();

        if (!splitTunnelSwitch.isChecked()) {
            splitTunnelStatus.setText(R.string.split_tunnel_no_apps);
            return;
        }

        Map<String, String> availableApps = new LinkedHashMap<>();
        availableApps.put("youtube", "YouTube");
        availableApps.put("youtube_revanced", "YouTube Revanced");
        availableApps.put("zoom", "Zoom");
        availableApps.put("whatsapp", "WhatsApp");
        availableApps.put("viber", "Viber");
        availableApps.put("netflix", "Netflix");
        availableApps.put("instagram", "Instagram");

        for (Map.Entry<String, String> app : availableApps.entrySet()) {
            boolean isSelected = splitTunnelApps.containsKey(app.getKey());
            android.widget.CheckBox checkBox = new android.widget.CheckBox(this);
            checkBox.setText(app.getValue());
            checkBox.setChecked(isSelected);
            checkBox.setEnabled(!isConnectionConfigLocked());
            checkBox.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isConnectionConfigLocked()) {
                    buttonView.setChecked(splitTunnelApps.containsKey(app.getKey()));
                    return;
                }
                if (isChecked) {
                    splitTunnelApps.put(app.getKey(), app.getValue());
                } else {
                    splitTunnelApps.remove(app.getKey());
                }
                saveSplitTunnelAppsToPrefs();
                updateSplitTunnelStatus();
            });
            splitTunnelAppsContainer.addView(checkBox);
        }

        updateSplitTunnelStatus();
    }

    private void updateSplitTunnelStatus() {
        if (splitTunnelApps.isEmpty()) {
            splitTunnelStatus.setText(R.string.split_tunnel_no_apps);
        } else {
            splitTunnelStatus.setText(getString(R.string.split_tunnel_applied, splitTunnelApps.size()));
        }
    }

    private void ensureProxyMode() {
        if (V2rayConfigs.serviceMode != V2rayConstants.SERVICE_MODES.PROXY_MODE) {
            V2rayController.toggleConnectionMode();
        }
    }

    private void ensureVpnMode() {
        if (V2rayConfigs.serviceMode != V2rayConstants.SERVICE_MODES.VPN_MODE) {
            V2rayController.toggleConnectionMode();
        }
    }

    private void appendProxyConfigHints(StringBuilder builder) {
        String hostIp = getLocalProxyHost();
        builder.append(getString(R.string.proxy_tethering_log_host, hostIp)).append('\n');
        builder.append(getString(R.string.proxy_tethering_log_socks, V2rayConfigs.currentConfig.localSocksPort)).append('\n');
        builder.append(getString(R.string.proxy_tethering_log_http, V2rayConfigs.currentConfig.localHttpPort)).append('\n');
        builder.append(getString(R.string.proxy_tethering_log_start_hint)).append('\n');
        if (V2rayConfigs.serviceMode == V2rayConstants.SERVICE_MODES.PROXY_MODE) {
            builder.append(getString(R.string.proxy_tethering_mode_proxy)).append('\n');
        } else {
            builder.append(getString(R.string.proxy_tethering_mode_vpn)).append('\n');
        }
    }

    private String getLocalProxyHost() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "192.168.43.1";
    }

    private void appendProxyEvent(String line) {
        if (proxyEventLog.length() > 6000) {
            proxyEventLog.delete(0, 3000);
        }
        proxyEventLog.append(line).append('\n');
    }

    private String makeProxyTetheringReadyConfig(String config) {
        try {
            String normalizedConfig = Utilities.normalizeV2rayFullConfig(config);
            return normalizedConfig.replace("\"listen\": \"127.0.0.1\"", "\"listen\": \"0.0.0.0\"");
        } catch (Exception ignored) {
            return config;
        }
    }

    private ArrayList<String> getBlockedApplicationsList() {
        ArrayList<String> blockedApps = new ArrayList<>();
        for (String packageKey : splitTunnelApps.keySet()) {
            String androidPkgName = androidPackageNames.get(packageKey);
            if (androidPkgName != null) {
                blockedApps.add(androidPkgName);
            }
        }
        return blockedApps;
    }

    private void refreshProxyPanelLog() {
        StringBuilder builder = new StringBuilder();
        String timeText = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date());
        builder.append(getString(R.string.proxy_tethering_log_header)).append('\n');
        builder.append(getString(R.string.proxy_tethering_live_title)).append('\n');
        builder.append(getString(R.string.proxy_tethering_log_time, timeText)).append('\n');
        builder.append(getString(R.string.proxy_tethering_log_state, V2rayController.getConnectionState().name())).append('\n');
        builder.append(getString(R.string.proxy_tethering_log_mode, V2rayConfigs.serviceMode.name())).append('\n');
        appendProxyConfigHints(builder);
        builder.append('\n').append(getString(R.string.proxy_tethering_events_title)).append('\n');
        builder.append(proxyEventLog);
        proxyLogText.setText(builder.toString());
    }

    private void startProxyPanelLiveRefresh() {
        proxyPanelHandler.removeCallbacks(proxyPanelRefreshRunnable);
        proxyPanelHandler.post(proxyPanelRefreshRunnable);
    }

    private void stopProxyPanelLiveRefresh() {
        proxyPanelHandler.removeCallbacks(proxyPanelRefreshRunnable);
    }

    private void onPingButtonClick() {
        if (V2rayController.getConnectionState() != V2rayConstants.CONNECTION_STATES.CONNECTED) {
            pingButton.setText(R.string.ping_connect_first);
            pingIpStatus.setText(R.string.ping_ip_connect_first);
            return;
        }

        pingButton.setEnabled(false);
        pingButton.setText(R.string.ping_loading);
        pingIpStatus.setText(R.string.ping_ip_loading);
        executorService.execute(() -> {
            PingResult result = runPingCheck();
            runOnUiThread(() -> {
                pingButton.setEnabled(true);
                if (result.success) {
                    pingButton.setText(getString(R.string.ping_success, result.latencyMs));
                    pingIpStatus.setText(getString(R.string.ping_ip_value, result.publicIp));
                } else {
                    pingButton.setText(R.string.ping_failed);
                    pingIpStatus.setText(R.string.ping_ip_failed);
                }
            });
        });
    }

    private PingResult runPingCheck() {
        long latencyMs = measureProxyHttpDelay(DELAY_TEST_URL_PRIMARY);
        if (latencyMs < 0) {
            latencyMs = measureProxyHttpDelay(DELAY_TEST_URL_FALLBACK);
        }
        if (latencyMs < 0) {
            return new PingResult(false, "0", "-");
        }

        String publicIp = fetchPublicIpThroughProxy();
        if (publicIp == null || publicIp.trim().isEmpty()) {
            publicIp = "-";
        }
        return new PingResult(true, String.valueOf(latencyMs), publicIp);
    }

    private long measureProxyHttpDelay(String testUrl) {
        HttpURLConnection connection = null;
        try {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(LOCAL_PROXY_HOST, LOCAL_SOCKS_PORT));
            URL url = new URL(testUrl);
            connection = (HttpURLConnection) url.openConnection(proxy);
            connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
            connection.setReadTimeout(NETWORK_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "close");

            long start = SystemClock.elapsedRealtime();
            int code = connection.getResponseCode();
            long elapsed = Math.max(SystemClock.elapsedRealtime() - start, 1L);

            if (code == 204 || (code == 200 && connection.getContentLengthLong() == 0L)) {
                return elapsed;
            }
            return -1L;
        } catch (Exception ignored) {
            return -1L;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String fetchPublicIpThroughProxy() {
        HttpURLConnection connection = null;
        try {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(LOCAL_PROXY_HOST, LOCAL_SOCKS_PORT));
            URL url = new URL(IP_INFO_URL);
            connection = (HttpURLConnection) url.openConnection(proxy);
            connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
            connection.setReadTimeout(NETWORK_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }

            try (InputStream inputStream = connection.getInputStream()) {
                byte[] bytes = readFully(inputStream);
                String json = new String(bytes);
                JSONObject jsonObject = new JSONObject(json);
                String ip = firstNonEmpty(
                        jsonObject.optString("ip", ""),
                        jsonObject.optString("client_ip", ""),
                        jsonObject.optString("ip_addr", ""),
                        jsonObject.optString("query", "")
                );
                return ip == null ? null : ip.trim();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private byte[] readFully(InputStream inputStream) throws Exception {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void setupPackageCards() {
        bindPackageCard("youtube", findViewById(R.id.card_youtube));
        bindPackageCard("zoom", findViewById(R.id.card_zoom));
        bindPackageCard("whatsapp", findViewById(R.id.card_whatsapp));
        bindPackageCard("viber", findViewById(R.id.card_viber));
        bindPackageCard("netflix", findViewById(R.id.card_netflix));
        bindPackageCard("instagram", findViewById(R.id.card_instagram));
    }

    private void bindPackageCard(String packageKey, MaterialCardView cardView) {
        packageCards.put(packageKey, cardView);
        cardView.setOnClickListener(v -> {
            if (isConnectionConfigLocked()) {
                return;
            }
            selectedPackageKey = packageKey;
            sharedPreferences.edit().putString(PREF_SELECTED_PACKAGE_KEY, packageKey).apply();
            refreshPackageSelectionUi();
        });
    }

    private void refreshPackageSelectionUi() {
        boolean lockConfig = isConnectionConfigLocked();
        for (Map.Entry<String, MaterialCardView> entry : packageCards.entrySet()) {
            boolean isSelected = Objects.equals(selectedPackageKey, entry.getKey());
            entry.getValue().setEnabled(!lockConfig);
            entry.getValue().setStrokeWidth(isSelected ? dpToPx(2) : 0);
            entry.getValue().setStrokeColor(ContextCompat.getColor(this, R.color.accent_cyan));
            entry.getValue().setCardBackgroundColor(ContextCompat.getColor(this, isSelected ? R.color.card_selected_background : R.color.card_background));
        }
    }

    private boolean isConnectionConfigLocked() {
        V2rayConstants.CONNECTION_STATES state = V2rayController.getConnectionState();
        return state == V2rayConstants.CONNECTION_STATES.CONNECTED
                || state == V2rayConstants.CONNECTION_STATES.CONNECTING;
    }

    private void refreshConfigControlLockState() {
        boolean isLocked = isConnectionConfigLocked();
        proxyTetheringSwitch.setEnabled(!isLocked);
        splitTunnelSwitch.setEnabled(!isLocked);
        refreshPackageSelectionUi();
        refreshSplitTunnelUi();
    }

    private void onConnectButtonClick() {
        if (V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
            if (selectedPackageKey == null) {
                Toast.makeText(this, R.string.error_select_package_first, Toast.LENGTH_SHORT).show();
                return;
            }

            String selectedConfig = packageConfigs.get(selectedPackageKey);
            if (selectedConfig == null || selectedConfig.trim().isEmpty()) {
                Toast.makeText(this, R.string.error_load_packages, Toast.LENGTH_SHORT).show();
                return;
            }

            String startConfig = selectedConfig;
            if (sharedPreferences.getBoolean(PREF_PROXY_TETHERING_ENABLED, false)) {
                startConfig = makeProxyTetheringReadyConfig(selectedConfig);
                appendProxyEvent(getString(R.string.proxy_tethering_bind_all_interfaces));
            }

            ArrayList<String> blockedApps = null;
            if (sharedPreferences.getBoolean(PREF_SPLIT_TUNNEL_ENABLED, false) && !splitTunnelApps.isEmpty()) {
                blockedApps = getBlockedApplicationsList();
            }

            updateConnectionUi(V2rayConstants.CONNECTION_STATES.CONNECTING);
            V2rayController.startV2ray(this, selectedPackageKey, startConfig, blockedApps);
            return;
        }

        V2rayController.stopV2ray(this);
    }

    private void fetchSubscriptionConfigs() {
        String cachedRaw = sharedPreferences.getString(PREF_SUBSCRIPTION_CACHE, null);
        Map<String, String> cachedConfigs = parseConfigs(cachedRaw);

        if (!cachedConfigs.isEmpty()) {
            applyConfigs(cachedConfigs);
            subscriptionStatus.setText(getString(R.string.packages_loaded_cached, cachedConfigs.size()));
            parsedPackagesDebug.setText(getParsedPackagesDebugText(cachedConfigs));
            updateLastSyncUi();
        } else {
            subscriptionStatus.setText(R.string.loading_packages);
            parsedPackagesDebug.setText(getString(R.string.parsed_packages_none));
            updateLastSyncUi();
        }

        executorService.execute(() -> {
            String remoteRaw = downloadSubscriptionRaw();
            if (remoteRaw != null) {
                Map<String, String> parsed = parseConfigs(remoteRaw);
                if (!parsed.isEmpty()) {
                    applyConfigs(parsed);
                    sharedPreferences.edit()
                            .putString(PREF_SUBSCRIPTION_CACHE, remoteRaw)
                            .putLong(PREF_LAST_SYNC_TIME, System.currentTimeMillis())
                            .apply();
                    runOnUiThread(() -> {
                        subscriptionStatus.setText(getString(R.string.packages_loaded, parsed.size()));
                        parsedPackagesDebug.setText(getParsedPackagesDebugText(parsed));
                        updateLastSyncUi();
                    });
                    return;
                }
            }

            runOnUiThread(() -> {
                if (!cachedConfigs.isEmpty()) {
                    subscriptionStatus.setText(getString(R.string.packages_loaded_cached, cachedConfigs.size()));
                    parsedPackagesDebug.setText(getParsedPackagesDebugText(cachedConfigs));
                    updateLastSyncUi();
                } else {
                    subscriptionStatus.setText(R.string.error_load_packages);
                    parsedPackagesDebug.setText(getString(R.string.parsed_packages_none));
                    updateLastSyncUi();
                }
            });
        });
    }

    private void updateLastSyncUi() {
        long lastSyncTime = sharedPreferences.getLong(PREF_LAST_SYNC_TIME, 0L);
        if (lastSyncTime <= 0L) {
            lastSyncStatus.setText(R.string.last_sync_never);
            return;
        }

        String timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(lastSyncTime));
        lastSyncStatus.setText(getString(R.string.last_sync_format, timeText));
    }

    private void applyConfigs(Map<String, String> configs) {
        packageConfigs.clear();
        packageConfigs.putAll(configs);

        if (selectedPackageKey != null && !packageConfigs.containsKey(selectedPackageKey)) {
            selectedPackageKey = null;
            sharedPreferences.edit().remove(PREF_SELECTED_PACKAGE_KEY).apply();
        }

        runOnUiThread(this::refreshPackageSelectionUi);
    }

    private String downloadSubscriptionRaw() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(SUBSCRIPTION_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            try (InputStream inputStream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder rawText = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    rawText.append(line).append('\n');
                }
                return rawText.toString();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Map<String, String> parseConfigs(String rawSubscription) {
        Map<String, String> parsedConfigs = new HashMap<>();
        if (rawSubscription == null || rawSubscription.trim().isEmpty()) {
            return parsedConfigs;
        }

        List<String> candidateVlessLinks = new ArrayList<>();
        appendVlessLinksFromText(rawSubscription, candidateVlessLinks);

        String[] lines = rawSubscription.split("\\r?\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            appendVlessLinksFromText(trimmedLine, candidateVlessLinks);

            String decodedText = decodeBase64(trimmedLine);
            if (decodedText != null) {
                appendVlessLinksFromText(decodedText, candidateVlessLinks);
            }
        }

        for (String vlessLink : candidateVlessLinks) {
            int packageNameIndex = vlessLink.lastIndexOf('#');
            if (packageNameIndex == -1 || packageNameIndex == vlessLink.length() - 1) {
                continue;
            }

            String packageName = vlessLink.substring(packageNameIndex + 1).trim();
            String packageKey = normalizePackageName(packageName);
            if (packageCards.containsKey(packageKey)) {
                parsedConfigs.put(packageKey, vlessLink.trim());
            }
        }

        return parsedConfigs;
    }

    private void appendVlessLinksFromText(String text, List<String> target) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String[] parts = text.split("\\r?\\n");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith("vless://") && trimmedPart.contains("#")) {
                target.add(trimmedPart);
            }
        }
    }

    private String decodeBase64(String encoded) {
        try {
            return new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return new String(Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            byte[] bytes = decodePaddedBase64(encoded);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private byte[] decodePaddedBase64(String encoded) {
        String normalized = encoded.replace('-', '+').replace('_', '/').trim();
        int padding = normalized.length() % 4;
        if (padding > 0) {
            normalized = normalized + "====".substring(padding);
        }
        return Base64.decode(normalized, Base64.NO_WRAP);
    }

    private String normalizePackageName(String packageName) {
        return packageName.toLowerCase(Locale.US)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
    }

    private String getParsedPackagesDebugText(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return getString(R.string.parsed_packages_none);
        }

        Map<String, String> displayNames = new LinkedHashMap<>();
        displayNames.put("youtube", "YouTube");
        displayNames.put("zoom", "Zoom");
        displayNames.put("whatsapp", "WhatsApp");
        displayNames.put("viber", "Viber");
        displayNames.put("netflix", "Netflix");
        displayNames.put("instagram", "Instagram");

        List<String> loaded = new ArrayList<>();
        for (Map.Entry<String, String> entry : displayNames.entrySet()) {
            if (configs.containsKey(entry.getKey())) {
                loaded.add(entry.getValue());
            }
        }

        if (loaded.isEmpty()) {
            return getString(R.string.parsed_packages_none);
        }
        return getString(R.string.parsed_packages_format, String.join(", ", loaded));
    }

    private void registerV2rayReceiver() {
        if (isV2rayReceiverRegistered) {
            return;
        }
        v2rayBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }

                Object stateObject = extras.getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA);
                if (stateObject instanceof V2rayConstants.CONNECTION_STATES) {
                    V2rayConstants.CONNECTION_STATES state = (V2rayConstants.CONNECTION_STATES) stateObject;
                    if (state != lastObservedState) {
                        updateConnectionUi(state);
                    }
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(v2rayBroadcastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT), RECEIVER_EXPORTED);
        } else {
            registerReceiver(v2rayBroadcastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT));
        }
        isV2rayReceiverRegistered = true;
    }

    private void unregisterV2rayReceiver() {
        if (!isV2rayReceiverRegistered || v2rayBroadcastReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(v2rayBroadcastReceiver);
        } catch (Exception ignored) {
        }
        isV2rayReceiverRegistered = false;
    }

    private void updateConnectionUi(V2rayConstants.CONNECTION_STATES state) {
        lastObservedState = state;
        switch (state) {
            case CONNECTED:
                connectButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.connect_connected));
                connectionStatus.setText(R.string.connected);
                connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.connect_connected));
                break;
            case CONNECTING:
                connectButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.connect_connecting));
                connectionStatus.setText(R.string.connecting);
                connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.connect_connecting));
                break;
            case DISCONNECTED:
            default:
                connectButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.connect_disconnected));
                connectionStatus.setText(R.string.not_connected);
                connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                pingButton.setText(R.string.ping);
                pingIpStatus.setText(R.string.ping_ip_idle);
                pingButton.setEnabled(true);
                break;
        }
            refreshConfigControlLockState();
    }

    private int dpToPx(int dpValue) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dpValue * density);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
            return;
        }
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterV2rayReceiver();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        stopProxyPanelLiveRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterV2rayReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerV2rayReceiver();
        updateConnectionUi(V2rayController.getConnectionState());
    }

    private static final class PingResult {
        private final boolean success;
        private final String latencyMs;
        private final String publicIp;

        private PingResult(boolean success, String latencyMs, String publicIp) {
            this.success = success;
            this.latencyMs = latencyMs;
            this.publicIp = publicIp;
        }
    }
}