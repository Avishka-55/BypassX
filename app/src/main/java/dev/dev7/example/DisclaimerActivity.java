package com.bypassx.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

public class DisclaimerActivity extends AppCompatActivity {

    private static final String PREF_NAME = "bypassx_prefs";
    private static final String PREF_DISCLAIMER_ACCEPTED = "disclaimer_accepted";
    private static final String PRIVACY_POLICY_URL = "https://bypassx-vpn.netlify.app/#privacy";

    private MaterialCheckBox torrentCheckbox;
    private MaterialCheckBox privacyCheckbox;
    private MaterialButton enterButton;
    private MaterialButton privacyLinkButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isDisclaimerAccepted()) {
            openAuthAndFinish();
            finish();
            return;
        }

        setContentView(R.layout.activity_disclaimer);

        torrentCheckbox = findViewById(R.id.disclaimer_agree_torrent_checkbox);
        privacyCheckbox = findViewById(R.id.disclaimer_agree_privacy_checkbox);
        enterButton = findViewById(R.id.disclaimer_enter_button);
        privacyLinkButton = findViewById(R.id.disclaimer_privacy_link_button);

        torrentCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());
        privacyCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());

        enterButton.setOnClickListener(v -> {
            if (torrentCheckbox.isChecked() && privacyCheckbox.isChecked()) {
                acceptDisclaimer();
            } else {
                Toast.makeText(this, R.string.disclaimer_button_hint, Toast.LENGTH_SHORT).show();
            }
        });

        privacyLinkButton.setOnClickListener(v -> openPrivacyPolicy());
        updateButtonState();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        updateButtonState();
    }

    private void updateButtonState() {
        boolean bothChecked = torrentCheckbox.isChecked() && privacyCheckbox.isChecked();
        enterButton.setEnabled(bothChecked);
        enterButton.setAlpha(bothChecked ? 1.0f : 0.5f);
    }

    private void acceptDisclaimer() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_DISCLAIMER_ACCEPTED, true)
                .apply();

        openAuthAndFinish();
        finish();
    }

    private void openAuthAndFinish() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openPrivacyPolicy() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.privacy_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDisclaimerAccepted() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_DISCLAIMER_ACCEPTED, false);
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, R.string.disclaimer_must_accept_message, Toast.LENGTH_SHORT).show();
    }
}
