package com.bypassx.app;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public final class AuthApiClient {

    // Render free instances can take noticeable time to cold-start.
    private static final int TIMEOUT_MS = 60000;

    private AuthApiClient() {
    }

    public static AuthResponse login(String email, String password) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            return post("/api/auth/login", body);
        } catch (Exception e) {
            return AuthResponse.error("Login failed");
        }
    }

    public static AuthResponse register(String name, String email, String password) {
        try {
            JSONObject body = new JSONObject();
            body.put("name", name);
            body.put("email", email);
            body.put("password", password);
            return post("/api/auth/register", body);
        } catch (Exception e) {
            return AuthResponse.error("Registration failed");
        }
    }

    public static AuthResponse checkStatus(String email) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            return post("/api/auth/check-status", body);
        } catch (Exception e) {
            return AuthResponse.error("Status check failed");
        }
    }

    private static AuthResponse post(String path, JSONObject body) {
        HttpURLConnection connection = null;
        try {
            String baseUrl = sanitizeBaseUrl(BuildConfig.AUTH_BASE_URL);
            if (baseUrl.isEmpty()) {
                return AuthResponse.error("AUTH_BASE_URL is not configured");
            }
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                return AuthResponse.error("AUTH_BASE_URL must start with http:// or https://");
            }

            URL url = new URL(baseUrl + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String responseText = stream != null ? readString(stream) : "";
            JSONObject json;
            try {
                json = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
            } catch (Exception parseException) {
                if (code >= 500) {
                    return AuthResponse.error("Auth server unavailable (" + code + ")");
                }
                if (code == 404) {
                    return AuthResponse.error("Auth endpoint not found (404)");
                }
                return AuthResponse.error("Unexpected server response (" + code + ")");
            }
            boolean success = json.optBoolean("success", false);
            String message = json.optString("message", success ? "Success" : "Request failed");
            String status = json.optString("status", "").toLowerCase();

            if ("pending".equals(status)) {
                return AuthResponse.pending(message);
            }
            if ("rejected".equals(status)) {
                return AuthResponse.rejected(message);
            }

            if ("active".equals(status) && path.endsWith("/check-status")) {
                return AuthResponse.active(message);
            }

            if (!success) {
                return AuthResponse.error(message);
            }

            String token = json.optString("token", "");
            JSONObject user = json.optJSONObject("user");
            if (token.isEmpty() || user == null) {
                return AuthResponse.error("Invalid server response");
            }

            String name = user.optString("name", "");
            String email = user.optString("email", "");
            return AuthResponse.success(message, token, name, email);
        } catch (UnknownHostException e) {
            return AuthResponse.error("Auth server host not found");
        } catch (SocketTimeoutException e) {
            return AuthResponse.error("Auth server timeout. If backend is on Render, wait 20-60s and try again.");
        } catch (Exception e) {
            return AuthResponse.error("Could not reach authentication server (" + e.getClass().getSimpleName() + ")");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String readString(InputStream inputStream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    public static final class AuthResponse {
        public final boolean success;
        public final String message;
        public final String token;
        public final String name;
        public final String email;
        public final String status;

        private AuthResponse(boolean success, String message, String token, String name, String email, String status) {
            this.success = success;
            this.message = message;
            this.token = token;
            this.name = name;
            this.email = email;
            this.status = status;
        }

        public static AuthResponse success(String message, String token, String name, String email) {
            return new AuthResponse(true, message, token, name, email, "active");
        }

        public static AuthResponse pending(String message) {
            return new AuthResponse(false, message, "", "", "", "pending");
        }

        public static AuthResponse active(String message) {
            return new AuthResponse(true, message, "", "", "", "active");
        }

        public static AuthResponse rejected(String message) {
            return new AuthResponse(false, message, "", "", "", "rejected");
        }

        public static AuthResponse error(String message) {
            return new AuthResponse(false, message, "", "", "", "");
        }
    }
}
