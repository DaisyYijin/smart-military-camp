package com.szjx.personnel.client.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP API 客户端(基于 HttpURLConnection,JDK8 兼容,无第三方依赖)
 */
public class ApiClient {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 60000;

    private final Gson gson = new Gson();
    private String baseUrl;
    private String token;

    public ApiClient(String baseUrl) {
        setBaseUrl(baseUrl);
    }

    public void setBaseUrl(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    // ---------- 业务调用 ----------

    public JsonObject get(String path) throws ApiException {
        return request("GET", path, null, null);
    }

    public JsonObject get(String path, Map<String, String> params) throws ApiException {
        return request("GET", path, params, null);
    }

    public JsonObject post(String path, Object body) throws ApiException {
        return request("POST", path, null, body);
    }

    public JsonObject put(String path, Object body) throws ApiException {
        return request("PUT", path, null, body);
    }

    public JsonObject delete(String path) throws ApiException {
        return request("DELETE", path, null, null);
    }

    /**
     * 下载(导出),返回原始字节
     */
    public byte[] download(String path, Map<String, String> params) throws ApiException {
        try {
            String url = buildUrl(baseUrl + path, params);
            HttpURLConnection conn = open(url, "GET");
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return readAll(conn.getInputStream());
            }
            throw new ApiException("下载失败,HTTP " + code + " " + readError(conn));
        } catch (IOException e) {
            throw new ApiException("无法连接服务器:" + e.getMessage(), e);
        }
    }

    /**
     * multipart 文件上传
     */
    public JsonObject upload(String path, String fileName, byte[] fileBytes,
                             String contentType) throws ApiException {
        String boundary = "----szjx" + Long.toHexString(System.currentTimeMillis());
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (token != null) {
                conn.setRequestProperty("X-Auth-Token", token);
            }

            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            StringBuilder head = new StringBuilder();
            head.append("--").append(boundary).append("\r\n");
            head.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(fileName.replace("\"", "_")).append("\"\r\n");
            head.append("Content-Type: ").append(contentType).append("\r\n\r\n");
            body.write(head.toString().getBytes(StandardCharsets.UTF_8));
            body.write(fileBytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            conn.setFixedLengthStreamingMode(body.size());
            OutputStream out = conn.getOutputStream();
            try {
                out.write(body.toByteArray());
            } finally {
                out.close();
            }

            int code = conn.getResponseCode();
            String text = readAllAsString(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code == 401) {
                throw new ApiException("登录已过期,请重新登录");
            }
            if (code < 200 || code >= 300) {
                throw new ApiException("上传失败(HTTP " + code + "):" + brief(text));
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            int bizCode = json.has("code") ? json.get("code").getAsInt() : -1;
            if (bizCode != 0) {
                String msg = json.has("message") ? json.get("message").getAsString() : "上传失败";
                throw new ApiException(msg);
            }
            return json;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("网络错误:" + e.getMessage(), e);
        }
    }

    // ---------- 底层 ----------

    private JsonObject request(String method, String path, Map<String, String> params, Object body)
            throws ApiException {
        try {
            String url = buildUrl(baseUrl + path, params);
            HttpURLConnection conn = open(url, method);
            if (body != null) {
                conn.setDoOutput(true);
                byte[] payload = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setFixedLengthStreamingMode(payload.length);
                OutputStream out = conn.getOutputStream();
                try {
                    out.write(payload);
                } finally {
                    out.close();
                }
            }
            int code = conn.getResponseCode();
            String text = readAllAsString(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code == 401) {
                throw new ApiException("登录已过期,请重新登录");
            }
            if (code < 200 || code >= 300) {
                throw new ApiException("请求失败(HTTP " + code + "):" + brief(text));
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            int bizCode = json.has("code") ? json.get("code").getAsInt() : -1;
            if (bizCode != 0) {
                String msg = json.has("message") ? json.get("message").getAsString() : "操作失败";
                throw new ApiException(msg);
            }
            return json;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("网络错误:" + e.getMessage(), e);
        }
    }

    private HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        if (token != null) {
            conn.setRequestProperty("X-Auth-Token", token);
        }
        return conn;
    }

    private String buildUrl(String base, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            try {
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                // 不可能发生
            }
        }
        return sb.toString();
    }

    private static String readAllAsString(InputStream in) throws IOException {
        byte[] bytes = readAll(in);
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
            }
            return buf.toByteArray();
        } finally {
            in.close();
        }
    }

    private String readError(HttpURLConnection conn) {
        try {
            String s = readAllAsString(conn.getErrorStream());
            return brief(s);
        } catch (Exception e) {
            return "";
        }
    }

    private String brief(String s) {
        if (s == null) {
            return "";
        }
        s = s.replace('\n', ' ').replace('\r', ' ');
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
