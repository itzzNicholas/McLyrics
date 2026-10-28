package dev.lyricsworld.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Minimal HTTP client on top of HttpURLConnection (Java 8, no extra dependencies). */
public final class Http {
    public static final String USER_AGENT = "LyricsWorld/" + Version.VERSION
            + " (https://github.com/itzznicholas/mclyrics)";

    public static final class Result {
        public final int code;
        public final String body;
        public final long roundTripMs;

        Result(int code, String body, long roundTripMs) {
            this.code = code;
            this.body = body;
            this.roundTripMs = roundTripMs;
        }

        public boolean ok() { return code >= 200 && code < 300; }
    }

    private Http() {}

    public static Result get(String url, Map<String, String> headers) throws IOException {
        return request("GET", url, headers, null, null);
    }

    public static Result postForm(String url, Map<String, String> form) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return request("POST", url, null, sb.toString().getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded");
    }

    public static Result request(String method, String url, Map<String, String> headers,
                                 byte[] body, String contentType) throws IOException {
        long t0 = System.nanoTime();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                try (OutputStream os = c.getOutputStream()) { os.write(body); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);
            return new Result(code, text, (System.nanoTime() - t0) / 1_000_000L);
        } finally {
            c.disconnect();
        }
    }

    public static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
