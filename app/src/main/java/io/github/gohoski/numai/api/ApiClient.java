package io.github.gohoski.numai.api;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import io.github.gohoski.numai.BuildConfig;
import io.github.gohoski.numai.R;
import io.github.gohoski.numai.data.ConfigManager;
import io.github.gohoski.numai.model.Config;
import io.github.gohoski.numai.util.ConnectionInputStream;

public class ApiClient {
    private final Context context;

    public ApiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public ApiResponse execute(ApiRequest request) throws ApiError {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            Config config = ConfigManager.getInstance(context).getConfig();
            Log.d("ApiClient", config.getBaseUrl());
            String fullUrl = config.getBaseUrl() + request.getEndpoint();
            URL url = new URL(fullUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(request.getMethod());
            connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            connection.setRequestProperty("User-Agent", "numAi/" + BuildConfig.VERSION_NAME + " (https://github.com/gohoski/numai)");
            connection.setRequestProperty("Accept", "application/json");
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if ("POST".equals(request.getMethod())) {
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
            }
            connection.setConnectTimeout(15000);
            if ("POST".equals(request.getMethod()) && request.getBody() != null && request.getBody().length() != 0) {
                OutputStream outputStream = null;
                try {
                    outputStream = connection.getOutputStream();
                    outputStream.write(request.getBody().getBytes());
                    outputStream.flush();
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException ignored) {}
                    }
                }
            }
            int statusCode = connection.getResponseCode();
            if (statusCode >= 200 && statusCode < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }
            ConnectionInputStream connStream = new ConnectionInputStream(inputStream, connection);

            return new ApiResponse(statusCode, connStream);
        } catch (IOException e) {
            e.printStackTrace();
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
            throw new ApiError(context.getString(R.string.errorNetwork,e.getMessage()));
        }
    }

    public String executeAsString(ApiRequest request) throws ApiError {
        InputStream inputStream = null;
        try {
            ApiResponse response = execute(request);
            return readInputStreamToString(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ApiError(e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public String readInputStreamToString(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } finally {
            outputStream.close();
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
        }
        return outputStream.toString("UTF-8");
    }
}
