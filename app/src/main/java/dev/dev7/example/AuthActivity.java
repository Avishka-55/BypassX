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
    private TextInputLayout otpInputLayout;

    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private TextInputEditText otpInput;

    private MaterialButton loginToggleButton;
    private MaterialButton registerToggleButton;
    private MaterialButton sendOtpButton;
    private MaterialButton editRegisterDetailsButton;
    private MaterialButton submitButton;
    private MaterialButton checkStatusButton;
    private TextView switchHintText;
    private TextView authStatusText;
    private TextView registerStepText;
    private ProgressBar authProgress;

    private ExecutorService executorService;
    private String pendingEmail = "";
    private String pendingPassword = "";
    private String lastOtpRequestedEmail = "";
    private boolean isRegisterOtpStep = false;

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
        otpInputLayout = findViewById(R.id.auth_otp_layout);

        nameInput = findViewById(R.id.auth_name_input);
        emailInput = findViewById(R.id.auth_email_input);
        passwordInput = findViewById(R.id.auth_password_input);
        confirmPasswordInput = findViewById(R.id.auth_confirm_password_input);
        otpInput = findViewById(R.id.auth_otp_input);

        loginToggleButton = findViewById(R.id.auth_toggle_login);
        registerToggleButton = findViewById(R.id.auth_toggle_register);
        sendOtpButton = findViewById(R.id.auth_send_otp_button);
        editRegisterDetailsButton = findViewById(R.id.auth_edit_register_details_button);
        submitButton = findViewById(R.id.auth_submit_button);
        checkStatusButton = findViewById(R.id.auth_check_status_button);
        switchHintText = findViewById(R.id.auth_switch_hint);
        authStatusText = findViewById(R.id.auth_status_text);
        registerStepText = findViewById(R.id.auth_register_step_text);
        authProgress = findViewById(R.id.auth_progress);
        checkStatusButton.setVisibility(View.GONE);
    }

    private void setupListeners() {
        loginToggleButton.setOnClickListener(v -> {
            currentMode = Mode.LOGIN;
            updateModeUi();
        });

        registerToggleButton.setOnClickListener(v -> {
            currentMode = Mode.REGISTER;
            isRegisterOtpStep = false;
            updateModeUi();
        });

        submitButton.setOnClickListener(v -> submitAuth());
        sendOtpButton.setOnClickListener(v -> sendRegisterOtp());
        editRegisterDetailsButton.setOnClickListener(v -> {
            isRegisterOtpStep = false;
            updateModeUi();
        });
        checkStatusButton.setOnClickListener(v -> checkPendingStatus());

        switchHintText.setOnClickListener(v -> {
            currentMode = currentMode == Mode.LOGIN ? Mode.REGISTER : Mode.LOGIN;
            isRegisterOtpStep = false;
            clearPendingState();
            updateModeUi();
        });
    }

    private void updateModeUi() {
        boolean registerMode = currentMode == Mode.REGISTER;

        boolean showRegisterDetails = registerMode && !isRegisterOtpStep;
        boolean showRegisterOtpStep = registerMode && isRegisterOtpStep;

        nameInputLayout.setVisibility(showRegisterDetails ? View.VISIBLE : View.GONE);
        emailInputLayout.setVisibility((registerMode && isRegisterOtpStep) ? View.GONE : View.VISIBLE);
        passwordInputLayout.setVisibility((registerMode && isRegisterOtpStep) ? View.GONE : View.VISIBLE);
        confirmPasswordInputLayout.setVisibility(showRegisterDetails ? View.VISIBLE : View.GONE);
        otpInputLayout.setVisibility(showRegisterOtpStep ? View.VISIBLE : View.GONE);
        sendOtpButton.setVisibility(showRegisterDetails ? View.VISIBLE : View.GONE);
        editRegisterDetailsButton.setVisibility(showRegisterOtpStep ? View.VISIBLE : View.GONE);
        registerStepText.setVisibility(showRegisterOtpStep ? View.VISIBLE : View.GONE);
        if (showRegisterOtpStep) {
            registerStepText.setText(getString(R.string.auth_register_step_verify_format, emailForStepText()));
        }

        loginToggleButton.setStrokeWidth(registerMode ? 0 : dpToPx(2));
        registerToggleButton.setStrokeWidth(registerMode ? dpToPx(2) : 0);

        submitButton.setText(registerMode ? R.string.auth_register_button : R.string.auth_login_button);
        switchHintText.setText(registerMode ? R.string.auth_switch_to_login : R.string.auth_switch_to_register);
        submitButton.setVisibility(registerMode ? (showRegisterOtpStep ? View.VISIBLE : View.GONE) : View.VISIBLE);
        authStatusText.setText("");
    }

    private void submitAuth() {
        clearFieldErrors();

        String name = text(nameInput);
        String email = text(emailInput);
        String password = text(passwordInput);
        String confirmPassword = text(confirmPasswordInput);
        String otp = text(otpInput);

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

        if (currentMode == Mode.REGISTER && !isRegisterOtpStep) {
            authStatusText.setText(R.string.auth_send_code_first);
            return;
        }

        if (currentMode == Mode.REGISTER && TextUtils.isEmpty(otp)) {
            otpInputLayout.setError(getString(R.string.auth_error_otp_required));
            return;
        }

        if (currentMode == Mode.REGISTER && otp.length() != 6) {
            otpInputLayout.setError(getString(R.string.auth_error_otp_invalid));
            return;
        }

        if (currentMode == Mode.REGISTER
                && !lastOtpRequestedEmail.isEmpty()
                && !lastOtpRequestedEmail.equalsIgnoreCase(email)) {
            authStatusText.setText(R.string.auth_error_otp_email_changed);
            return;
        }

        setLoadingState(true);

        executorService.execute(() -> {
            AuthApiClient.AuthResponse response;
            if (currentMode == Mode.REGISTER) {
                response = AuthApiClient.register(name, email, password, otp);
            } else {
                response = AuthApiClient.login(email, password);
            }

            runOnUiThread(() -> {
                setLoadingState(false);
                String pendingStatus = response.status == null ? "" : response.status;
                if ("pending".equalsIgnoreCase(pendingStatus)) {
                    showPendingState(email, password, response.message);
                    return;
                }
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
        sendOtpButton.setEnabled(!loading);
        editRegisterDetailsButton.setEnabled(!loading);
        loginToggleButton.setEnabled(!loading);
        registerToggleButton.setEnabled(!loading);
        checkStatusButton.setEnabled(!loading);
    }

    private void clearFieldErrors() {
        nameInputLayout.setError(null);
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
        confirmPasswordInputLayout.setError(null);
        otpInputLayout.setError(null);
        authStatusText.setText("");
    }

    private void sendRegisterOtp() {
        if (currentMode != Mode.REGISTER) {
            return;
        }

        clearFieldErrors();
        final String email = text(emailInput);
        if (email.isEmpty()) {
            emailInputLayout.setError(getString(R.string.auth_error_email_required));
            return;
        }

        final String name = text(nameInput);
        final String password = text(passwordInput);
        final String confirmPassword = text(confirmPasswordInput);

        if (name.isEmpty()) {
            nameInputLayout.setError(getString(R.string.auth_error_name_required));
            return;
        }

        if (password.isEmpty()) {
            passwordInputLayout.setError(getString(R.string.auth_error_password_required));
            return;
        }

        if (password.length() < 6) {
            passwordInputLayout.setError(getString(R.string.auth_error_password_short));
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordInputLayout.setError(getString(R.string.auth_error_password_mismatch));
            return;
        }

        setLoadingState(true);
        executorService.execute(() -> {
            AuthApiClient.AuthResponse response = AuthApiClient.sendRegisterOtp(email);
            runOnUiThread(() -> {
                setLoadingState(false);
                if (!response.success) {
                    authStatusText.setText(response.message);
                    return;
                }

                lastOtpRequestedEmail = email;
                isRegisterOtpStep = true;
                otpInput.setText("");
                updateModeUi();
                authStatusText.setText(getString(R.string.auth_otp_sent));
                Toast.makeText(this, R.string.auth_otp_sent, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private String emailForStepText() {
        String email = text(emailInput);
        if (!email.isEmpty()) {
            return email;
        }
        if (!lastOtpRequestedEmail.isEmpty()) {
            return lastOtpRequestedEmail;
        }
        return getString(R.string.auth_email_hint);
    }

    private void showPendingState(String email, String password, String message) {
        pendingEmail = email;
        pendingPassword = password;
        authStatusText.setText(message);
        checkStatusButton.setVisibility(View.VISIBLE);
    }

    private void clearPendingState() {
        pendingEmail = "";
        pendingPassword = "";
        checkStatusButton.setVisibility(View.GONE);
    }

    private void checkPendingStatus() {
        final String email = !pendingEmail.isEmpty() ? pendingEmail : text(emailInput);
        final String fallbackPassword = text(passwordInput);
        if (email.isEmpty()) {
            authStatusText.setText(R.string.auth_error_email_required);
            return;
        }

        setLoadingState(true);
        executorService.execute(() -> {
            AuthApiClient.AuthResponse statusResponse = AuthApiClient.checkStatus(email);

            if (!statusResponse.success && !"pending".equalsIgnoreCase(statusResponse.status)) {
                runOnUiThread(() -> {
                    setLoadingState(false);
                    authStatusText.setText(statusResponse.message);
                });
                return;
            }

            if ("pending".equalsIgnoreCase(statusResponse.status)) {
                runOnUiThread(() -> {
                    setLoadingState(false);
                    authStatusText.setText(statusResponse.message);
                    checkStatusButton.setVisibility(View.VISIBLE);
                });
                return;
            }

            if (!"active".equalsIgnoreCase(statusResponse.status)) {
                runOnUiThread(() -> {
                    setLoadingState(false);
                    clearPendingState();
                    authStatusText.setText(statusResponse.message);
                });
                return;
            }

            final String password = !pendingPassword.isEmpty() ? pendingPassword : fallbackPassword;
            if (password.isEmpty()) {
                runOnUiThread(() -> {
                    setLoadingState(false);
                    authStatusText.setText(R.string.auth_account_active_login_now);
                    clearPendingState();
                    currentMode = Mode.LOGIN;
                    updateModeUi();
                });
                return;
            }

            AuthApiClient.AuthResponse loginResponse = AuthApiClient.login(email, password);
            runOnUiThread(() -> {
                setLoadingState(false);
                if (!loginResponse.success) {
                    authStatusText.setText(loginResponse.message);
                    return;
                }

                clearPendingState();
                AuthSessionManager.saveSession(this, loginResponse.token, loginResponse.name, loginResponse.email);
                Toast.makeText(this, R.string.auth_success, Toast.LENGTH_SHORT).show();
                openMainAndFinish();
            });
        });
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
