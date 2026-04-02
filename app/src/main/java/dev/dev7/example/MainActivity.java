package com.bypassx.app;

import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;

import android.annotation.SuppressLint;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.text.InputType;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final String PREF_CUSTOM_SNI = "custom_sni";
    private static final String PREF_LAST_SYNC_TIME = "last_sync_time";
    private static final String PREF_STATUS_CACHE_AVAILABLE = "status_cache_available";
    private static final String PREF_STATUS_CACHE_UNLIMITED = "status_cache_unlimited";
    private static final String PREF_STATUS_CACHE_REMAINING = "status_cache_remaining";
    private static final String PREF_STATUS_CACHE_EXPIRY = "status_cache_expiry";
    private static final String PREF_STATUS_CACHE_EXPIRED = "status_cache_expired";
    private static final String PREF_PROXY_TETHERING_ENABLED = "proxy_tethering_enabled";
    private static final String PREF_SPLIT_TUNNEL_ENABLED = "split_tunnel_enabled";
    private static final String PREF_SPLIT_TUNNEL_APPS = "split_tunnel_apps";
    private static final String DELAY_TEST_URL_PRIMARY = "https://www.gstatic.com/generate_204";
    private static final String DELAY_TEST_URL_FALLBACK = "https://www.google.com/generate_204";
    private static final String IP_INFO_URL = "https://api.ip.sb/geoip";
    private static final String LOCAL_PROXY_HOST = "127.0.0.1";
    private static final int LOCAL_SOCKS_PORT = 10808;
    private static final int NETWORK_TIMEOUT_MS = 6000;
    private static final int UPDATE_TIMEOUT_MS = 10000;
    private static final int AUTH_TIMEOUT_MS = 15000;
    private static final long STATUS_CHECK_INTERVAL_MS = 30000L;
    private static final String CUSTOM_PACKAGE_KEY = "customsni";
    private static final Map<String, String> PACKAGE_SNI_BY_KEY = createPackageSniMap();
    private static final Map<String, String> PACKAGE_DISPLAY_NAMES = createPackageDisplayNames();

    private final Map<String, String> packageConfigs = new HashMap<>();
    private final Map<String, String> splitTunnelApps = new HashMap<>();  // packageKey -> display name
    private final Map<String, String> androidPackageNames = new HashMap<>();  // packageKey -> android package name
    private final Map<String, String> splitTunnelAvailableApps = new LinkedHashMap<>();  // android package name -> display name
    private String selectedPackageKey;

    private SharedPreferences sharedPreferences;
    private ExecutorService executorService;
    private BroadcastReceiver v2rayBroadcastReceiver;
    private boolean isV2rayReceiverRegistered = false;
    private V2rayConstants.CONNECTION_STATES lastObservedState = null;
    private long lastStatusCheckAt = 0L;
    private boolean isStatusCheckInProgress = false;

    private MaterialButton connectButton;
    private MaterialButton pingButton;
    private TextView pingIpStatus;
    private TextView connectionStatus;
    private TextView subscriptionStatus;
    private TextView parsedPackagesDebug;
    private TextView lastSyncStatus;
    private LinearLayout packageIconContainer;
    private MaterialButton customSniButton;
    private ImageView selectedPackageIcon;
    private TextView selectedPackageValue;
    private TextView remainingDataValue;
    private TextView expiryDateValue;
    private NestedScrollView mainScrollContainer;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar topToolbar;
    private ActionBarDrawerToggle drawerToggle;
    private ObjectAnimator connectGlowAnimator;
    private LinearLayout proxyTetheringDrawer;
    private SwitchMaterial proxyTetheringSwitch;
    private MaterialButton proxyHotspotSettingsButton;
    private MaterialButton proxyGuideButton;
    private SwitchMaterial splitTunnelSwitch;
    private MaterialButton splitTunnelChooseAppsButton;
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

        if (!AuthSessionManager.hasSession(this)) {
            redirectToAuthAndFinish(getString(R.string.auth_login_required));
            return;
        }

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
        packageIconContainer = findViewById(R.id.package_icon_container);
        customSniButton = findViewById(R.id.custom_sni_button);
        selectedPackageIcon = findViewById(R.id.selected_package_icon);
        selectedPackageValue = findViewById(R.id.selected_package_value);
        remainingDataValue = findViewById(R.id.remaining_data_value);
        expiryDateValue = findViewById(R.id.expiry_date_value);
        mainScrollContainer = findViewById(R.id.main_scroll_container);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        topToolbar = findViewById(R.id.top_toolbar);
        proxyTetheringDrawer = findViewById(R.id.proxy_tethering_drawer);
        proxyTetheringSwitch = findViewById(R.id.proxy_tethering_switch);
        proxyHotspotSettingsButton = findViewById(R.id.proxy_hotspot_settings_button);
        proxyGuideButton = findViewById(R.id.proxy_guide_button);
        proxyLogText = findViewById(R.id.proxy_log_text);
        splitTunnelSwitch = findViewById(R.id.split_tunnel_switch);
        splitTunnelChooseAppsButton = findViewById(R.id.split_tunnel_choose_apps_button);
        splitTunnelStatus = findViewById(R.id.split_tunnel_status);

        initializeAndroidPackageNames();
        setupGreeting();
        configureMainScrollBehavior();
        setupConnectButtonGlow();
        setupNavigationDrawer();
        setupProxyTetheringPanel();
        setupSplitTunnelPanel();
        selectedPackageKey = sharedPreferences.getString(PREF_SELECTED_PACKAGE_KEY, null);
        refreshPackageSelectionUi();
        updateLastSyncUi();
        customSniButton.setOnClickListener(v -> showCustomSniDialog());
        connectButton.setOnClickListener(v -> onConnectButtonClick());
        pingButton.setOnClickListener(v -> onPingButtonClick());

        registerV2rayReceiver();
        updateConnectionUi(V2rayController.getConnectionState());
        fetchSubscriptionConfigs();
        renderCachedSubscriptionStatus();
        fetchSubscriptionStatus();
        validateAccountStatus(true);
    }

    private void setupGreeting() {
        String name = AuthSessionManager.getUserName(this);
        if (name == null || name.trim().isEmpty()) {
            topToolbar.setSubtitle(getString(R.string.greeting_default));
            return;
        }
        topToolbar.setSubtitle(getString(R.string.greeting_format, name.trim()));
    }

    private void setupConnectButtonGlow() {
        connectGlowAnimator = ObjectAnimator.ofFloat(connectButton, "alpha", 1f, 0.92f, 1f);
        connectGlowAnimator.setDuration(1600);
        connectGlowAnimator.setInterpolator(new LinearInterpolator());
        connectGlowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        connectGlowAnimator.setRepeatMode(ValueAnimator.RESTART);
    }

    private void configureMainScrollBehavior() {
        if (mainScrollContainer == null) {
            return;
        }
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        mainScrollContainer.setFillViewport(!isLandscape);
        mainScrollContainer.setNestedScrollingEnabled(true);
        if (isLandscape) {
            mainScrollContainer.post(() -> mainScrollContainer.scrollTo(0, 0));
        }
    }

    private void updateConnectButtonGlow(V2rayConstants.CONNECTION_STATES state) {
        if (connectGlowAnimator == null) {
            return;
        }
        boolean shouldGlow = state == V2rayConstants.CONNECTION_STATES.DISCONNECTED;
        if (shouldGlow) {
            if (!connectGlowAnimator.isStarted()) {
                connectGlowAnimator.start();
            }
            return;
        }
        if (connectGlowAnimator.isStarted()) {
            connectGlowAnimator.cancel();
        }
        connectButton.setAlpha(1f);
    }

    private void validateAccountStatus(boolean force) {
        if (executorService == null || isStatusCheckInProgress) {
            return;
        }

        if (!AuthSessionManager.hasSession(this)) {
            redirectToAuthAndFinish(getString(R.string.auth_login_required));
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && (now - lastStatusCheckAt) < STATUS_CHECK_INTERVAL_MS) {
            return;
        }
        lastStatusCheckAt = now;
        isStatusCheckInProgress = true;

        final String email = AuthSessionManager.getUserEmail(this);
        if (email == null || email.trim().isEmpty()) {
            isStatusCheckInProgress = false;
            redirectToAuthAndFinish(getString(R.string.auth_session_invalid));
            return;
        }

        executorService.execute(() -> {
            AuthApiClient.AuthResponse response = AuthApiClient.checkStatus(email);
            runOnUiThread(() -> {
                isStatusCheckInProgress = false;
                String status = response.status == null ? "" : response.status;
                if ("active".equalsIgnoreCase(status)) {
                    return;
                }
                if ("pending".equalsIgnoreCase(status)
                        || "rejected".equalsIgnoreCase(status)
                        || "not_found".equalsIgnoreCase(status)
                        || "invalid".equalsIgnoreCase(status)) {
                    AuthSessionManager.clear(this);
                    redirectToAuthAndFinish(response.message);
                    return;
                }
            });
        });
    }

    private void redirectToAuthAndFinish(String reason) {
        V2rayConstants.CONNECTION_STATES state = V2rayController.getConnectionState();
        if (state == V2rayConstants.CONNECTION_STATES.CONNECTED
                || state == V2rayConstants.CONNECTION_STATES.CONNECTING) {
            V2rayController.stopV2ray(this);
        }

        if (reason != null && !reason.trim().isEmpty()) {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupNavigationDrawer() {
        setSupportActionBar(topToolbar);
        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                topToolbar,
            R.string.drawer_open,
            R.string.drawer_close
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
            if (itemId == R.id.nav_contact) {
                openWhatsappContact();
                drawerLayout.closeDrawers();
                return true;
            }
            if (itemId == R.id.nav_settings) {
                if (isConnectionConfigLocked()) {
                    Toast.makeText(this, R.string.disconnect_vpn_first, Toast.LENGTH_SHORT).show();
                }
                openSettingsPanel();
                return true;
            }
            if (itemId == R.id.nav_share) {
                shareApp();
                drawerLayout.closeDrawers();
                return true;
            }
            if (itemId == R.id.nav_check_update) {
                checkForAppUpdate();
                drawerLayout.closeDrawers();
                return true;
            }
            if (itemId == R.id.nav_privacy_policy) {
                openPrivacyPolicy();
                drawerLayout.closeDrawers();
                return true;
            }
            if (itemId == R.id.nav_about) {
                showAboutDialog();
                drawerLayout.closeDrawers();
                return true;
            }
            if (itemId == R.id.nav_logout) {
                performLogout();
                return true;
            }
            if (itemId == R.id.nav_exit) {
                exitApplication();
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

    private void openSettingsPanel() {
        drawerLayout.closeDrawer(GravityCompat.START);
        drawerLayout.openDrawer(GravityCompat.END);
    }

    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)));
    }

    private void openPrivacyPolicy() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.privacy_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkForAppUpdate() {
        String metadataUrl = resolveUpdateMetadataUrl();
        if (metadataUrl == null || metadataUrl.isEmpty()) {
            Toast.makeText(this, R.string.update_url_missing, Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            UpdateCheckResult result = fetchUpdateCheckResult(metadataUrl);
            runOnUiThread(() -> handleUpdateCheckResult(result));
        });
    }

    private String resolveUpdateMetadataUrl() {
        String configured = getString(R.string.update_metadata_url);
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }

        String authBase = sanitizeAuthBaseUrl(BuildConfig.AUTH_BASE_URL);
        if (authBase.isEmpty()) {
            return null;
        }
        return authBase + "/latest.json";
    }

    private UpdateCheckResult fetchUpdateCheckResult(String metadataUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(metadataUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(UPDATE_TIMEOUT_MS);
            connection.setReadTimeout(UPDATE_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return UpdateCheckResult.error(getString(R.string.update_check_failed));
            }

            try (InputStream stream = connection.getInputStream()) {
                String payload = readString(stream);
                if (payload == null || payload.trim().isEmpty()) {
                    return UpdateCheckResult.error(getString(R.string.update_invalid_response));
                }

                JSONObject json = new JSONObject(payload);
                long remoteVersionCode = json.optLong("versionCode", 0L);
                String remoteVersionName = json.optString("versionName", "").trim();
                String downloadUrl = firstNonEmpty(
                        json.optString("latestUrl", "").trim(),
                        json.optString("downloadUrl", "").trim()
                );

                if (remoteVersionCode <= 0 || downloadUrl == null || downloadUrl.trim().isEmpty()) {
                    return UpdateCheckResult.error(getString(R.string.update_invalid_response));
                }

                long currentVersionCode = getInstalledVersionCode();
                String currentVersionName = getInstalledVersionName();
                boolean hasUpdate = remoteVersionCode > currentVersionCode;

                return UpdateCheckResult.success(
                        new UpdateInfo(
                                remoteVersionCode,
                                remoteVersionName,
                                downloadUrl,
                                currentVersionCode,
                                currentVersionName,
                                hasUpdate
                        )
                );
            }
        } catch (Exception ignored) {
            return UpdateCheckResult.error(getString(R.string.update_check_failed));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private long getInstalledVersionCode() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return packageInfo.getLongVersionCode();
            }
            return packageInfo.versionCode;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String getInstalledVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName == null ? "" : packageInfo.versionName.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void handleUpdateCheckResult(UpdateCheckResult result) {
        if (result == null || !result.success || result.info == null) {
            String message = result == null ? getString(R.string.update_check_failed) : result.errorMessage;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        UpdateInfo info = result.info;
        if (!info.hasUpdate) {
            Toast.makeText(this, R.string.update_no_new_version, Toast.LENGTH_SHORT).show();
            return;
        }

        String currentLabel = info.currentVersionName == null || info.currentVersionName.isEmpty()
                ? String.valueOf(info.currentVersionCode)
                : info.currentVersionName + " (" + info.currentVersionCode + ")";

        String latestLabel = info.remoteVersionName == null || info.remoteVersionName.isEmpty()
                ? String.valueOf(info.remoteVersionCode)
                : info.remoteVersionName + " (" + info.remoteVersionCode + ")";

        String message = getString(R.string.update_available_message, currentLabel, latestLabel);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setPositiveButton(R.string.update_download_button, (dialog, which) -> openUpdateDownload(info.downloadUrl))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openUpdateDownload(String downloadUrl) {
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl.trim()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void performLogout() {
        sharedPreferences.edit().clear().apply();
        AuthSessionManager.clear(this);
        stopVpnIfRunning();
        stopProxyPanelLiveRefresh();
        drawerLayout.closeDrawers();
        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void exitApplication() {
        stopVpnIfRunning();
        stopProxyPanelLiveRefresh();
        drawerLayout.closeDrawers();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
            return;
        }
        finishAffinity();
    }

    private void stopVpnIfRunning() {
        V2rayConstants.CONNECTION_STATES state = V2rayController.getConnectionState();
        if (state == V2rayConstants.CONNECTION_STATES.CONNECTED
                || state == V2rayConstants.CONNECTION_STATES.CONNECTING) {
            V2rayController.stopV2ray(this);
        }
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
                Toast.makeText(this, R.string.disconnect_vpn_first, Toast.LENGTH_SHORT).show();
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
        androidPackageNames.put("facebook", "com.facebook.katana");
        androidPackageNames.put("youtube", "com.google.android.youtube");
        androidPackageNames.put("youtube_revanced", "app.revanced.android.youtube");
        androidPackageNames.put("zoom", "us.zoom.videomeetings"); // backward compatibility key
        androidPackageNames.put("zoomnormal", "us.zoom.videomeetings");
        androidPackageNames.put("zoomdialog", "us.zoom.videomeetings");
        androidPackageNames.put("whatsapp", "com.whatsapp");
        androidPackageNames.put("viber", "com.viber.voip");
        androidPackageNames.put("netflix", "com.netflix.mediaclient");
        androidPackageNames.put("instagram", "com.instagram.android");
        androidPackageNames.put("telegram", "org.telegram.messenger");
        androidPackageNames.put("spotify", "com.spotify.music");
        androidPackageNames.put("linkedin", "com.linkedin.android");
        androidPackageNames.put("xtwitter", "com.twitter.android");
        androidPackageNames.put("tiktok", "com.zhiliaoapp.musically");
    }

    private void setupSplitTunnelPanel() {
        boolean splitTunnelEnabled = sharedPreferences.getBoolean(PREF_SPLIT_TUNNEL_ENABLED, false);
        splitTunnelSwitch.setChecked(splitTunnelEnabled);
        loadInstalledAppsForSplitTunnel();
        loadSplitTunnelAppsFromPrefs();

        splitTunnelChooseAppsButton.setOnClickListener(v -> showSplitTunnelAppPickerDialog());
        refreshSplitTunnelUi();

        splitTunnelSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isConnectionConfigLocked()) {
                Toast.makeText(this, R.string.disconnect_vpn_first, Toast.LENGTH_SHORT).show();
                splitTunnelSwitch.setChecked(sharedPreferences.getBoolean(PREF_SPLIT_TUNNEL_ENABLED, false));
                return;
            }
            sharedPreferences.edit().putBoolean(PREF_SPLIT_TUNNEL_ENABLED, isChecked).apply();
            if (isChecked) {
                loadInstalledAppsForSplitTunnel();
                refreshSplitTunnelUi();
            } else {
                splitTunnelApps.clear();
                saveSplitTunnelAppsToPrefs();
                refreshSplitTunnelUi();
            }
        });
    }

    private void showSplitTunnelAppPickerDialog() {
        if (isConnectionConfigLocked()) {
            Toast.makeText(this, R.string.disconnect_vpn_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!splitTunnelSwitch.isChecked()) {
            Toast.makeText(this, R.string.split_tunnel_enable_first, Toast.LENGTH_SHORT).show();
            return;
        }

        loadInstalledAppsForSplitTunnel();
        if (splitTunnelAvailableApps.isEmpty()) {
            Toast.makeText(this, R.string.error_load_packages, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> packageNames = new ArrayList<>(splitTunnelAvailableApps.keySet());
        String[] labels = new String[packageNames.size()];
        boolean[] checked = new boolean[packageNames.size()];

        for (int i = 0; i < packageNames.size(); i++) {
            String packageName = packageNames.get(i);
            labels[i] = splitTunnelAvailableApps.get(packageName);
            checked[i] = splitTunnelApps.containsKey(packageName);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.split_tunnel_select_apps)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    splitTunnelApps.clear();
                    for (int i = 0; i < packageNames.size(); i++) {
                        if (checked[i]) {
                            String packageName = packageNames.get(i);
                            splitTunnelApps.put(packageName, splitTunnelAvailableApps.get(packageName));
                        }
                    }
                    saveSplitTunnelAppsToPrefs();
                    updateSplitTunnelStatus();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadInstalledAppsForSplitTunnel() {
        splitTunnelAvailableApps.clear();

        Intent launchIntent = new Intent(Intent.ACTION_MAIN, null);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> launchables = packageManager.queryIntentActivities(launchIntent, 0);
        Map<String, String> byPackage = new HashMap<>();

        for (ResolveInfo info : launchables) {
            if (info.activityInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (packageName == null || packageName.trim().isEmpty() || packageName.equals(getPackageName())) {
                continue;
            }
            if (byPackage.containsKey(packageName)) {
                continue;
            }

            CharSequence labelText = info.loadLabel(packageManager);
            String appLabel = labelText == null ? packageName : labelText.toString().trim();
            if (appLabel.isEmpty()) {
                appLabel = packageName;
            }

            byPackage.put(packageName, appLabel);
        }

        List<Map.Entry<String, String>> sortedApps = new ArrayList<>(byPackage.entrySet());
        Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        sortedApps.sort((left, right) -> collator.compare(left.getValue(), right.getValue()));

        for (Map.Entry<String, String> app : sortedApps) {
            splitTunnelAvailableApps.put(app.getKey(), app.getValue());
            androidPackageNames.put(app.getKey(), app.getKey());
        }
    }

    private void loadSplitTunnelAppsFromPrefs() {
        String saved = sharedPreferences.getString(PREF_SPLIT_TUNNEL_APPS, "");
        splitTunnelApps.clear();
        if (!saved.isEmpty()) {
            String[] parts = saved.split("\\|");
            for (String part : parts) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) {
                    String packageKey = kv[0].trim();
                    String displayName = kv[1].trim();
                    if (packageKey.isEmpty()) {
                        continue;
                    }

                    String resolvedPackage = resolveSplitTunnelPackageName(packageKey);
                    if (resolvedPackage == null || resolvedPackage.isEmpty()) {
                        continue;
                    }

                    String resolvedDisplayName = splitTunnelAvailableApps.get(resolvedPackage);
                    if (resolvedDisplayName == null || resolvedDisplayName.trim().isEmpty()) {
                        continue;
                    }

                    splitTunnelApps.put(resolvedPackage, resolvedDisplayName.isEmpty() ? displayName : resolvedDisplayName);
                }
            }
        }
    }

    private String resolveSplitTunnelPackageName(String storedKey) {
        String mapped = androidPackageNames.get(storedKey);
        if (mapped != null && !mapped.trim().isEmpty()) {
            return mapped;
        }
        return storedKey;
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
        if (!splitTunnelSwitch.isChecked()) {
            splitTunnelChooseAppsButton.setEnabled(false);
            splitTunnelChooseAppsButton.setAlpha(0.55f);
            splitTunnelStatus.setText(R.string.split_tunnel_no_apps);
            return;
        }

        splitTunnelChooseAppsButton.setEnabled(!isConnectionConfigLocked());
        splitTunnelChooseAppsButton.setAlpha(isConnectionConfigLocked() ? 0.55f : 1f);

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
        Set<String> blockedPackages = new LinkedHashSet<>();
        for (String packageKey : splitTunnelApps.keySet()) {
            String androidPkgName = androidPackageNames.get(packageKey);
            if (androidPkgName == null || androidPkgName.trim().isEmpty()) {
                androidPkgName = packageKey;
            }
            if (androidPkgName.contains(".")) {
                blockedPackages.add(androidPkgName);
            }
        }
        return new ArrayList<>(blockedPackages);
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

    private static Map<String, String> createPackageSniMap() {
        Map<String, String> map = new HashMap<>();
        map.put("youtube", "www.youtube.com");
        map.put("facebook", "www.facebook.com");
        map.put("tiktok", "www.tiktok.com");
        map.put("zoomnormal", "www.zoom.us");
        map.put("zoomdialog", "aka.ms");
        map.put("xtwitter", "www.x.com");
        map.put("instagram", "www.instagram.com");
        map.put("viber", "www.viber.com");
        map.put("netflix", "www.netflix.com");
        map.put("whatsapp", "www.whatsapp.com");
        map.put("telegram", "web.telegram.org");
        map.put("spotify", "open.spotify.com");
        map.put("linkedin", "www.linkedin.com");
        return map;
    }

    private static Map<String, String> createPackageDisplayNames() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("youtube", "YouTube");
        map.put("facebook", "Facebook");
        map.put("tiktok", "TikTok");
        map.put("zoomnormal", "Zoom (Normal)");
        map.put("zoomdialog", "Zoom (Dialog)");
        map.put("xtwitter", "X (Twitter)");
        map.put("instagram", "Instagram");
        map.put("viber", "Viber");
        map.put("netflix", "Netflix");
        map.put("whatsapp", "WhatsApp");
        map.put("telegram", "Telegram");
        map.put("spotify", "Spotify");
        map.put("linkedin", "LinkedIn");
        return map;
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

    private void refreshPackageSelectionUi() {
        String selectedLabel;
        if (CUSTOM_PACKAGE_KEY.equals(selectedPackageKey)) {
            String customSni = sharedPreferences.getString(PREF_CUSTOM_SNI, "");
            if (customSni != null && !customSni.trim().isEmpty()) {
                selectedLabel = getString(R.string.package_custom_selected_format, customSni.trim());
            } else {
                selectedLabel = getString(R.string.package_custom_sni);
            }
        } else {
            selectedLabel = PACKAGE_DISPLAY_NAMES.get(selectedPackageKey);
        }
        if (selectedLabel == null || selectedLabel.trim().isEmpty()) {
            selectedPackageValue.setText(R.string.package_selected_none);
            selectedPackageIcon.setImageResource(R.drawable.ic_pkg_default);
        } else {
            selectedPackageValue.setText(getString(R.string.package_selected_format, selectedLabel));
            selectedPackageIcon.setImageDrawable(resolvePackageIconDrawable(selectedPackageKey));
        }
        boolean enabled = !isConnectionConfigLocked();
        customSniButton.setEnabled(enabled);
        customSniButton.setAlpha(enabled ? 1f : 0.5f);
        renderPackageShortcutStrip();
    }

    private void renderPackageShortcutStrip() {
        packageIconContainer.removeAllViews();
        boolean enabled = !isConnectionConfigLocked();

        for (Map.Entry<String, String> entry : PACKAGE_DISPLAY_NAMES.entrySet()) {
            String packageKey = entry.getKey();
            if (!packageConfigs.containsKey(packageKey)) {
                continue;
            }

                FrameLayout iconHolder = new FrameLayout(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(46), dp(46));
                params.setMarginEnd(dp(8));
                iconHolder.setLayoutParams(params);

                ImageView iconView = new ImageView(this);
                FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                iconView.setLayoutParams(iconParams);
            iconView.setPadding(dp(7), dp(7), dp(7), dp(7));
            iconView.setImageDrawable(resolvePackageIconDrawable(packageKey));
            iconView.setBackgroundResource(packageKey.equals(selectedPackageKey)
                    ? R.drawable.bg_package_icon_chip_selected
                    : R.drawable.bg_package_icon_chip);
            iconView.setContentDescription(entry.getValue());
            iconView.setEnabled(enabled);
            iconView.setAlpha(enabled ? 1f : 0.6f);
            iconView.setOnClickListener(v -> {
                if (isConnectionConfigLocked()) {
                    Toast.makeText(this, R.string.disconnect_vpn_first, Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedPackageKey = packageKey;
                sharedPreferences.edit().putString(PREF_SELECTED_PACKAGE_KEY, selectedPackageKey).apply();
                refreshPackageSelectionUi();
            });
            iconView.setOnLongClickListener(v -> {
                Toast.makeText(this, entry.getValue(), Toast.LENGTH_SHORT).show();
                return true;
            });

            iconHolder.addView(iconView);

            if ("zoomnormal".equals(packageKey) || "zoomdialog".equals(packageKey)) {
                TextView badge = new TextView(this);
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                badgeParams.gravity = android.view.Gravity.END | android.view.Gravity.TOP;
                badgeParams.setMargins(0, dp(1), dp(1), 0);
                badge.setLayoutParams(badgeParams);
                badge.setText("zoomnormal".equals(packageKey) ? "N" : "D");
                badge.setTextSize(9f);
                badge.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                badge.setBackgroundResource(R.drawable.bg_stat_chip);
                badge.setPadding(dp(4), dp(1), dp(4), dp(1));
                iconHolder.addView(badge);
            }

            packageIconContainer.addView(iconHolder);
        }
    }

    private android.graphics.drawable.Drawable resolvePackageIconDrawable(String packageKey) {
        return ContextCompat.getDrawable(this, resolvePackageIconRes(packageKey));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int resolvePackageIconRes(String packageKey) {
        if (packageKey == null || CUSTOM_PACKAGE_KEY.equals(packageKey)) {
            return R.drawable.ic_pkg_default;
        }
        if ("youtube".equals(packageKey)) return R.drawable.ic_pkg_youtube;
        if ("facebook".equals(packageKey)) return R.drawable.ic_pkg_facebook;
        if ("tiktok".equals(packageKey)) return R.drawable.ic_pkg_tiktok;
        if ("zoomnormal".equals(packageKey)) return R.drawable.ic_pkg_zoomnormal;
        if ("zoomdialog".equals(packageKey)) return R.drawable.ic_pkg_zoomdialog;
        if ("xtwitter".equals(packageKey)) return R.drawable.ic_pkg_xtwitter;
        if ("instagram".equals(packageKey)) return R.drawable.ic_pkg_instagram;
        if ("viber".equals(packageKey)) return R.drawable.ic_pkg_viber;
        if ("netflix".equals(packageKey)) return R.drawable.ic_pkg_netflix;
        if ("whatsapp".equals(packageKey)) return R.drawable.ic_pkg_whatsapp;
        if ("telegram".equals(packageKey)) return R.drawable.ic_pkg_telegram;
        if ("spotify".equals(packageKey)) return R.drawable.ic_pkg_spotify;
        if ("linkedin".equals(packageKey)) return R.drawable.ic_pkg_linkedin;
        return R.drawable.ic_pkg_default;
    }

    private void showPackageSelectorDialog() {
        if (isConnectionConfigLocked()) {
            Toast.makeText(this, R.string.disconnect_vpn_first, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] entryLabels = new String[]{
                getString(R.string.package_source_builtin),
                getString(R.string.package_source_custom_sni)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.package_picker_title)
                .setItems(entryLabels, (dialog, which) -> {
                    if (which == 0) {
                        showBuiltInPackageDialog();
                    } else {
                        showCustomSniDialog();
                    }
                })
                .show();
    }

    private void showBuiltInPackageDialog() {
        if (isConnectionConfigLocked()) {
            Toast.makeText(this, R.string.disconnect_vpn_first, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> availableKeys = new ArrayList<>();
        List<String> availableLabels = new ArrayList<>();

        for (Map.Entry<String, String> entry : PACKAGE_DISPLAY_NAMES.entrySet()) {
            if (packageConfigs.containsKey(entry.getKey())) {
                availableKeys.add(entry.getKey());
                availableLabels.add(entry.getValue());
            }
        }

        if (availableKeys.isEmpty()) {
            Toast.makeText(this, R.string.error_load_packages, Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedIndex = availableKeys.indexOf(selectedPackageKey);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.package_picker_title)
                .setSingleChoiceItems(availableLabels.toArray(new String[0]), checkedIndex, (dialog, which) -> {
                    if (which >= 0 && which < availableKeys.size()) {
                        selectedPackageKey = availableKeys.get(which);
                        sharedPreferences.edit().putString(PREF_SELECTED_PACKAGE_KEY, selectedPackageKey).apply();
                        refreshPackageSelectionUi();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomSniDialog() {
        if (isConnectionConfigLocked()) {
            Toast.makeText(this, R.string.disconnect_vpn_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (packageConfigs.isEmpty()) {
            Toast.makeText(this, R.string.error_load_packages, Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.custom_sni_hint);
        String saved = sharedPreferences.getString(PREF_CUSTOM_SNI, "");
        if (saved != null && !saved.trim().isEmpty()) {
            input.setText(saved.trim());
            input.setSelection(input.getText().length());
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.package_custom_sni)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String sni = normalizeSniInput(input.getText() == null ? "" : input.getText().toString());
                    if (sni.isEmpty()) {
                        Toast.makeText(this, R.string.error_custom_sni_required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String baseConfig = packageConfigs.values().iterator().next();
                    String customConfig = applySniOverride(baseConfig, sni);
                    packageConfigs.put(CUSTOM_PACKAGE_KEY, ensurePackageRemark(customConfig, CUSTOM_PACKAGE_KEY));

                    sharedPreferences.edit()
                            .putString(PREF_CUSTOM_SNI, sni)
                            .putString(PREF_SELECTED_PACKAGE_KEY, CUSTOM_PACKAGE_KEY)
                            .apply();

                    selectedPackageKey = CUSTOM_PACKAGE_KEY;
                    refreshPackageSelectionUi();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String normalizeSniInput(String raw) {
        if (raw == null) {
            return "";
        }
        String out = raw.trim().toLowerCase(Locale.US);
        out = out.replace("https://", "").replace("http://", "");
        int slash = out.indexOf('/');
        if (slash >= 0) {
            out = out.substring(0, slash);
        }
        return out.replaceAll("[^a-z0-9.-]", "");
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
                    String cachePayload = serializeConfigMap(parsed);
                    applyConfigs(parsed);
                    sharedPreferences.edit()
                            .putString(PREF_SUBSCRIPTION_CACHE, cachePayload)
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

        String savedCustomSni = sharedPreferences.getString(PREF_CUSTOM_SNI, "");
        if (savedCustomSni != null && !savedCustomSni.trim().isEmpty() && !packageConfigs.isEmpty()) {
            String baseConfig = packageConfigs.values().iterator().next();
            String customConfig = applySniOverride(baseConfig, savedCustomSni.trim());
            packageConfigs.put(CUSTOM_PACKAGE_KEY, ensurePackageRemark(customConfig, CUSTOM_PACKAGE_KEY));
        }

        if (selectedPackageKey != null && !packageConfigs.containsKey(selectedPackageKey)) {
            selectedPackageKey = null;
            sharedPreferences.edit().remove(PREF_SELECTED_PACKAGE_KEY).apply();
        }

        runOnUiThread(this::refreshPackageSelectionUi);
    }

    private String downloadSubscriptionRaw() {
        HttpURLConnection connection = null;
        try {
            String subscriptionUrl = resolveSubscriptionUrl();
            if (subscriptionUrl == null || subscriptionUrl.trim().isEmpty()) {
                return null;
            }

            URL url = new URL(subscriptionUrl);
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

        String baseLink = null;
        for (String vlessLink : candidateVlessLinks) {
            if (baseLink == null && vlessLink != null && vlessLink.trim().startsWith("vless://")) {
                baseLink = vlessLink.trim();
            }

            int packageNameIndex = vlessLink.lastIndexOf('#');
            if (packageNameIndex == -1 || packageNameIndex == vlessLink.length() - 1) {
                continue;
            }

            String packageName = vlessLink.substring(packageNameIndex + 1).trim();
            String packageKey = normalizePackageName(packageName);
            if (PACKAGE_DISPLAY_NAMES.containsKey(packageKey)) {
                parsedConfigs.put(packageKey, applyPackageOverrides(vlessLink.trim(), packageKey));
            }
        }

        if (baseLink != null) {
            for (String packageKey : PACKAGE_DISPLAY_NAMES.keySet()) {
                if (!parsedConfigs.containsKey(packageKey)) {
                    String tagged = ensurePackageRemark(baseLink, packageKey);
                    parsedConfigs.put(packageKey, applyPackageOverrides(tagged, packageKey));
                }
            }
        }

        return parsedConfigs;
    }

    private String resolveSubscriptionUrl() {
        String userUrl = AuthSessionManager.getSubscriptionUrl(this);
        if (userUrl != null && !userUrl.trim().isEmpty()) {
            return userUrl.trim();
        }
        if (SUBSCRIPTION_URL != null && !SUBSCRIPTION_URL.trim().isEmpty()) {
            return SUBSCRIPTION_URL.trim();
        }
        return null;
    }

    private String serializeConfigMap(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return "";
        }

        List<String> keys = new ArrayList<>(configs.keySet());
        Collections.sort(keys);

        StringBuilder builder = new StringBuilder();
        for (String key : keys) {
            String value = configs.get(key);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            builder.append(value.trim()).append('\n');
        }
        return builder.toString();
    }

    private String ensurePackageRemark(String vlessLink, String packageKey) {
        int hashIndex = vlessLink.indexOf('#');
        if (hashIndex == -1) {
            return vlessLink + "#" + packageKey;
        }
        return vlessLink.substring(0, hashIndex + 1) + packageKey;
    }

    private String applyPackageOverrides(String vlessLink, String packageKey) {
        String sni = PACKAGE_SNI_BY_KEY.get(packageKey);
        if (sni == null || sni.trim().isEmpty()) {
            return vlessLink;
        }
        return applySniOverride(vlessLink, sni);
    }

    private String applySniOverride(String vlessLink, String sni) {
        if (sni == null || sni.trim().isEmpty()) {
            return vlessLink;
        }

        String[] hashParts = vlessLink.split("#", 2);
        String base = hashParts[0];
        String fragment = hashParts.length > 1 ? hashParts[1] : "";

        int queryStart = base.indexOf('?');
        if (queryStart == -1) {
            return vlessLink;
        }

        String prefix = base.substring(0, queryStart);
        String query = base.substring(queryStart + 1);
        String[] pairs = query.split("&");
        List<String> keptPairs = new ArrayList<>();

        for (String pair : pairs) {
            if (pair == null || pair.trim().isEmpty()) {
                continue;
            }
            int equalIndex = pair.indexOf('=');
            String key = equalIndex >= 0 ? pair.substring(0, equalIndex) : pair;
            String normalized = key.trim().toLowerCase(Locale.US);
            if ("sni".equals(normalized) || "host".equals(normalized)) {
                continue;
            }
            keptPairs.add(pair);
        }

        keptPairs.add("sni=" + Uri.encode(sni));
        keptPairs.add("host=" + Uri.encode(sni));

        String rebuilt = prefix + "?" + String.join("&", keptPairs);
        if (!fragment.isEmpty()) {
            rebuilt = rebuilt + "#" + fragment;
        }
        return rebuilt;
    }

    private void appendVlessLinksFromText(String text, List<String> target) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String[] parts = text.split("\\r?\\n");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith("vless://")) {
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
                .replaceAll("[^a-z0-9]", "");
    }

    private String getParsedPackagesDebugText(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return getString(R.string.parsed_packages_none);
        }

        List<String> loaded = new ArrayList<>();
        for (Map.Entry<String, String> entry : PACKAGE_DISPLAY_NAMES.entrySet()) {
            if (configs.containsKey(entry.getKey())) {
                loaded.add(entry.getValue());
            }
        }

        if (loaded.isEmpty()) {
            return getString(R.string.parsed_packages_none);
        }
        return getString(R.string.parsed_packages_format, String.join(", ", loaded));
    }

    private void fetchSubscriptionStatus() {
        final String token = AuthSessionManager.getToken(this);
        SubscriptionStatus cachedStatus = getCachedSubscriptionStatus();
        if (token == null || token.trim().isEmpty()) {
            if (cachedStatus.available) {
                renderSubscriptionStatus(cachedStatus);
            } else {
                remainingDataValue.setText(R.string.subscription_unavailable);
                expiryDateValue.setText(R.string.subscription_unavailable);
            }
            return;
        }

        if (cachedStatus.available) {
            renderSubscriptionStatus(cachedStatus);
        } else {
            remainingDataValue.setText(R.string.subscription_loading_value);
            expiryDateValue.setText(R.string.subscription_loading_value);
        }

        executorService.execute(() -> {
            SubscriptionStatus status = requestSubscriptionStatus(token);
            runOnUiThread(() -> {
                if (status.available) {
                    cacheSubscriptionStatus(status);
                    renderSubscriptionStatus(status);
                    return;
                }
                SubscriptionStatus fallback = getCachedSubscriptionStatus();
                if (fallback.available) {
                    renderSubscriptionStatus(fallback);
                } else {
                    renderSubscriptionStatus(status);
                }
            });
        });
    }

    private void renderCachedSubscriptionStatus() {
        SubscriptionStatus cached = getCachedSubscriptionStatus();
        if (cached.available) {
            renderSubscriptionStatus(cached);
        }
    }

    private SubscriptionStatus getCachedSubscriptionStatus() {
        boolean available = sharedPreferences.getBoolean(PREF_STATUS_CACHE_AVAILABLE, false);
        if (!available) {
            return SubscriptionStatus.unavailable();
        }

        boolean unlimited = sharedPreferences.getBoolean(PREF_STATUS_CACHE_UNLIMITED, false);
        long remaining = Math.max(0L, sharedPreferences.getLong(PREF_STATUS_CACHE_REMAINING, 0L));
        long expiryAt = Math.max(0L, sharedPreferences.getLong(PREF_STATUS_CACHE_EXPIRY, 0L));
        boolean expired = sharedPreferences.getBoolean(PREF_STATUS_CACHE_EXPIRED, expiryAt > 0 && expiryAt <= System.currentTimeMillis());
        return new SubscriptionStatus(unlimited, remaining, expiryAt, expired, true);
    }

    private void cacheSubscriptionStatus(SubscriptionStatus status) {
        if (status == null || !status.available) {
            return;
        }
        sharedPreferences.edit()
                .putBoolean(PREF_STATUS_CACHE_AVAILABLE, true)
                .putBoolean(PREF_STATUS_CACHE_UNLIMITED, status.unlimited)
                .putLong(PREF_STATUS_CACHE_REMAINING, Math.max(0L, status.remainingBytes))
                .putLong(PREF_STATUS_CACHE_EXPIRY, Math.max(0L, status.expiryAt))
                .putBoolean(PREF_STATUS_CACHE_EXPIRED, status.expired)
                .apply();
    }

    private SubscriptionStatus requestSubscriptionStatus(String token) {
        HttpURLConnection connection = null;
        try {
            String baseUrl = sanitizeAuthBaseUrl(BuildConfig.AUTH_BASE_URL);
            if (baseUrl.isEmpty()) {
                return SubscriptionStatus.unavailable();
            }

            URL url = new URL(baseUrl + "/api/user/subscription-status");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(AUTH_TIMEOUT_MS);
            connection.setReadTimeout(AUTH_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                return SubscriptionStatus.unavailable();
            }

            String responseText = readString(stream);
            JSONObject json = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
            if (!json.optBoolean("success", false)) {
                return SubscriptionStatus.unavailable();
            }

            JSONObject sub = json.optJSONObject("subscription");
            if (sub == null) {
                return SubscriptionStatus.unavailable();
            }

            boolean unlimited = sub.optBoolean("unlimited", false);
            long remaining = sub.optLong("remainingBytes", 0);
            long expiryAt = sub.optLong("expiryAt", 0);
            boolean expired = sub.optBoolean("isExpired", expiryAt > 0 && expiryAt <= System.currentTimeMillis());
            return new SubscriptionStatus(unlimited, remaining, expiryAt, expired, true);
        } catch (Exception ignored) {
            return SubscriptionStatus.unavailable();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void renderSubscriptionStatus(SubscriptionStatus status) {
        if (status == null || !status.available) {
            remainingDataValue.setText(R.string.subscription_unavailable);
            expiryDateValue.setText(R.string.subscription_unavailable);
            return;
        }

        if (status.unlimited) {
            remainingDataValue.setText(R.string.subscription_unlimited);
        } else {
            remainingDataValue.setText(Formatter.formatShortFileSize(this, Math.max(0L, status.remainingBytes)));
        }

        if (status.expiryAt > 0) {
            if (status.expired) {
                expiryDateValue.setText(R.string.subscription_expired);
                return;
            }
            String dateText = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(new Date(status.expiryAt));
            expiryDateValue.setText(dateText);
        } else {
            expiryDateValue.setText(R.string.subscription_unlimited);
        }
    }

    private String sanitizeAuthBaseUrl(String value) {
        if (value == null) {
            return "";
        }
        String out = value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private String readString(InputStream inputStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
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
        V2rayConstants.CONNECTION_STATES previousState = lastObservedState;
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
            updateConnectButtonGlow(state);
            refreshConfigControlLockState();

            if (state == V2rayConstants.CONNECTION_STATES.CONNECTED
                && previousState != V2rayConstants.CONNECTION_STATES.CONNECTED) {
                validateAccountStatus(true);
            }
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
        if (connectGlowAnimator != null) {
            connectGlowAnimator.cancel();
        }
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
        configureMainScrollBehavior();
        registerV2rayReceiver();
        updateConnectionUi(V2rayController.getConnectionState());
        fetchSubscriptionStatus();
    }

    private static final class SubscriptionStatus {
        private final boolean unlimited;
        private final long remainingBytes;
        private final long expiryAt;
        private final boolean expired;
        private final boolean available;

        private SubscriptionStatus(boolean unlimited, long remainingBytes, long expiryAt, boolean expired, boolean available) {
            this.unlimited = unlimited;
            this.remainingBytes = remainingBytes;
            this.expiryAt = expiryAt;
            this.expired = expired;
            this.available = available;
        }

        private static SubscriptionStatus unavailable() {
            return new SubscriptionStatus(false, 0, 0, false, false);
        }
    }

    private static final class UpdateInfo {
        private final long remoteVersionCode;
        private final String remoteVersionName;
        private final String downloadUrl;
        private final long currentVersionCode;
        private final String currentVersionName;
        private final boolean hasUpdate;

        private UpdateInfo(long remoteVersionCode,
                           String remoteVersionName,
                           String downloadUrl,
                           long currentVersionCode,
                           String currentVersionName,
                           boolean hasUpdate) {
            this.remoteVersionCode = remoteVersionCode;
            this.remoteVersionName = remoteVersionName;
            this.downloadUrl = downloadUrl;
            this.currentVersionCode = currentVersionCode;
            this.currentVersionName = currentVersionName;
            this.hasUpdate = hasUpdate;
        }
    }

    private static final class UpdateCheckResult {
        private final boolean success;
        private final String errorMessage;
        private final UpdateInfo info;

        private UpdateCheckResult(boolean success, String errorMessage, UpdateInfo info) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.info = info;
        }

        private static UpdateCheckResult success(UpdateInfo info) {
            return new UpdateCheckResult(true, null, info);
        }

        private static UpdateCheckResult error(String errorMessage) {
            return new UpdateCheckResult(false, errorMessage, null);
        }
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