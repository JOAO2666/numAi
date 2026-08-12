package io.github.gohoski.numai.api;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gohoski.numai.BuildConfig;
import io.github.gohoski.numai.R;
import io.github.gohoski.numai.data.ConfigManager;
import io.github.gohoski.numai.model.Config;
import io.github.gohoski.numai.util.ConnectionInputStream;

public class ApiClient {
    private final Context context;

    public ApiClient(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public ApiResponse execute(ApiRequest request) throws ApiError {
        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {
            String baseUrl = request.getBaseUrl();
            String apiKey = null;

            if (baseUrl == null && context != null) {
                Config config = ConfigManager.getInstance(context).getConfig();
                baseUrl = config.getBaseUrl();
                apiKey = config.getApiKey();
            }

            if (baseUrl == null) {
                baseUrl = "";
            }

            String fullUrl = baseUrl + request.getEndpoint();
            Map<String, List<String>> combinedHeaders = new HashMap<String, List<String>>();
            int redirectCount = 0;

            while (redirectCount < 5) {
                Log.d("ApiClient", fullUrl);
                URL url = new URL(fullUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false); // Manual redirect handling to preserve Set-Cookie
                connection.setRequestMethod(request.getMethod());

                if (apiKey != null && apiKey.length() > 0) {
                    connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                connection.setRequestProperty("User-Agent", "numAi/" + BuildConfig.VERSION_NAME + " (https://github.com/gohoski/numAi)");
                connection.setRequestProperty("Accept", "application/json");
//                connection.setRequestProperty("Connection", "close");

                for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }

                byte[] bodyBytes = null;
                if ("POST".equals(request.getMethod())) {
                    if (!request.getHeaders().containsKey("Content-Type")) {
                        connection.setRequestProperty("Content-Type", "application/json");
                    }
                    connection.setDoOutput(true);
                    if (request.getBody() != null && request.getBody().length() > 0) {
                        try {
                            bodyBytes = request.getBody().getBytes("UTF-8");
                        } catch (java.io.UnsupportedEncodingException e) {
                            bodyBytes = request.getBody().getBytes();
                        }
                    } else {
                        bodyBytes = new byte[0];
                    }
                    connection.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                    connection.setFixedLengthStreamingMode(bodyBytes.length);
                }

                connection.setConnectTimeout(12000);
                connection.setReadTimeout(request.getReadTimeout());

                if ("POST".equals(request.getMethod()) && bodyBytes != null && bodyBytes.length > 0) {
                    OutputStream outputStream = null;
                    try {
                        outputStream = connection.getOutputStream();
                        outputStream.write(bodyBytes);
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

                // Merge response headers (specifically Set-Cookie) across redirects
                if (connection.getHeaderFields() != null) {
                    for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                        if (entry.getKey() != null && "Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                            List<String> cookies = combinedHeaders.get("Set-Cookie");
                            if (cookies == null) {
                                cookies = new ArrayList<String>();
                                combinedHeaders.put("Set-Cookie", cookies);
                            }
                            cookies.addAll(entry.getValue());
                        } else if (entry.getKey() != null) {
                            combinedHeaders.put(entry.getKey(), entry.getValue());
                        }
                    }
                }

                if (statusCode == HttpURLConnection.HTTP_MOVED_PERM
                        || statusCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || statusCode == HttpURLConnection.HTTP_SEE_OTHER
                        || statusCode == 307) {
                    String loc = connection.getHeaderField("Location");
                    if (loc != null && loc.length() > 0) {
                        fullUrl = loc.startsWith("http") ? loc : baseUrl + loc;
                        redirectCount++;
                        connection.disconnect();

                        // Update Cookie header for the redirected request if cookies were set
                        List<String> cookies = combinedHeaders.get("Set-Cookie");
                        if (cookies != null && !cookies.isEmpty()) {
                            StringBuilder cookieHeader = new StringBuilder();
                            for (String c : cookies) {
                                String pair = c.split(";")[0].trim();
                                if (cookieHeader.length() > 0) cookieHeader.append("; ");
                                cookieHeader.append(pair);
                            }
                            request.addHeader("Cookie", cookieHeader.toString());
                        }
                        continue;
                    }
                }

                if (statusCode >= 200 && statusCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }
                ConnectionInputStream connStream = new ConnectionInputStream(inputStream, connection);

                return new ApiResponse(statusCode, connStream, combinedHeaders);
            }

            throw new ApiError("Too many redirects");

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
            String errMsg = e.getMessage();
            if (context != null) {
                errMsg = context.getString(R.string.errorNetwork, e.getMessage());
            }
            throw new ApiError(errMsg, e instanceof java.net.SocketTimeoutException);
        }
    }

    String executeAsString(ApiRequest request) throws ApiError {
        try {
            ApiResponse response = execute(request);
            return readInputStreamToString(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ApiError(e.getMessage());
        }
    }

    String readInputStreamToString(InputStream inputStream) throws IOException {
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
            return outputStream.toString("UTF-8");
        } finally {
            try {
                outputStream.close();
            } catch (IOException ignored) {}
            try {
                inputStream.close();
            } catch (IOException ignored) {}
        }
    }
}