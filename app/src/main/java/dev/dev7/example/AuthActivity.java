package com.bypassx.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthActivity extends AppCompatActivity {

    private enum Mode {
        LOGIN,
        REGISTER
    }

    private Mode currentMode = Mode.LOGIN;

    private TextInputLayout nameInputLayout;
    private TextInputLayout emailInputLayout;
    private TextInputLayout passwordInputLayout;
    private TextInputLayout confirmPasswordInputLayout;

    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;

    private MaterialButton loginToggleButton;
    private MaterialButton registerToggleButton;
    private MaterialButton submitButton;
    private TextView switchHintText;
    private TextView authStatusText;
    private TextView authBackendInfoText;
    private ProgressBar authProgress;

    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AuthSessionManager.hasSession(this)) {
            openMainAndFinish();
            return;
        }

        setContentView(R.layout.activity_auth);
        executorService = Executors.newSingleThreadExecutor();

        bindViews();
        setupListeners();
        updateModeUi();
    }

    private void bindViews() {
        nameInputLayout = findViewById(R.id.auth_name_layout);
        emailInputLayout = findViewById(R.id.auth_email_layout);
        passwordInputLayout = findViewById(R.id.auth_password_layout);
        confirmPasswordInputLayout = findViewById(R.id.auth_confirm_password_layout);

        nameInput = findViewById(R.id.auth_name_input);
        emailInput = findViewById(R.id.auth_email_input);
        passwordInput = findViewById(R.id.auth_password_input);
        confirmPasswordInput = findViewById(R.id.auth_confirm_password_input);

        loginToggleButton = findViewById(R.id.auth_toggle_login);
        registerToggleButton = findViewById(R.id.auth_toggle_register);
        submitButton = findViewById(R.id.auth_submit_button);
        switchHintText = findViewById(R.id.auth_switch_hint);
        authStatusText = findViewById(R.id.auth_status_text);
        authBackendInfoText = findViewById(R.id.auth_backend_info);
        authProgress = findViewById(R.id.auth_progress);
        authBackendInfoText.setText(getString(R.string.auth_backend_format, BuildConfig.AUTH_BASE_URL));
    }

    private void setupListeners() {
        loginToggleButton.setOnClickListener(v -> {
            currentMode = Mode.LOGIN;
            updateModeUi();
        });

        registerToggleButton.setOnClickListener(v -> {
            currentMode = Mode.REGISTER;
            updateModeUi();
        });

        submitButton.setOnClickListener(v -> submitAuth());

        switchHintText.setOnClickListener(v -> {
            currentMode = currentMode == Mode.LOGIN ? Mode.REGISTER : Mode.LOGIN;
            updateModeUi();
        });
    }

    private void updateModeUi() {
        boolean registerMode = currentMode == Mode.REGISTER;

        nameInputLayout.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        confirmPasswordInputLayout.setVisibility(registerMode ? View.VISIBLE : View.GONE);

        loginToggleButton.setStrokeWidth(registerMode ? 0 : dpToPx(2));
        registerToggleButton.setStrokeWidth(registerMode ? dpToPx(2) : 0);

        submitButton.setText(registerMode ? R.string.auth_register_button : R.string.auth_login_button);
        switchHintText.setText(registerMode ? R.string.auth_switch_to_login : R.string.auth_switch_to_register);
        authStatusText.setText("");
    }

    private void submitAuth() {
        clearFieldErrors();

        String name = text(nameInput);
        String email = text(emailInput);
        String password = text(passwordInput);
        String confirmPassword = text(confirmPasswordInput);

        if (currentMode == Mode.REGISTER && TextUtils.isEmpty(name)) {
            nameInputLayout.setError(getString(R.string.auth_error_name_required));
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError(getString(R.string.auth_error_email_required));
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.setError(getString(R.string.auth_error_password_required));
            return;
        }

        if (password.length() < 6) {
            passwordInputLayout.setError(getString(R.string.auth_error_password_short));
            return;
        }

        if (currentMode == Mode.REGISTER && !password.equals(confirmPassword)) {
            confirmPasswordInputLayout.setError(getString(R.string.auth_error_password_mismatch));
            return;
        }

        setLoadingState(true);

        executorService.execute(() -> {
            AuthApiClient.AuthResponse response;
            if (currentMode == Mode.REGISTER) {
                response = AuthApiClient.register(name, email, password);
            } else {
                response = AuthApiClient.login(email, password);
            }

            runOnUiThread(() -> {
                setLoadingState(false);
                if (!response.success) {
                    authStatusText.setText(response.message);
                    return;
                }

                AuthSessionManager.saveSession(this, response.token, response.name, response.email);
                Toast.makeText(this, R.string.auth_success, Toast.LENGTH_SHORT).show();
                openMainAndFinish();
            });
        });
    }

    private void setLoadingState(boolean loading) {
        authProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading);
        loginToggleButton.setEnabled(!loading);
        registerToggleButton.setEnabled(!loading);
    }

    private void clearFieldErrors() {
        nameInputLayout.setError(null);
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
        confirmPasswordInputLayout.setError(null);
        authStatusText.setText("");
    }

    private String text(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void openMainAndFinish() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        super.onDestroy();
    }
}
