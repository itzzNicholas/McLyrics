package dev.lyricsworld.media;

import com.google.gson.JsonObject;
import dev.lyricsworld.util.Http;
import dev.lyricsworld.util.Json;
import dev.lyricsworld.util.Log;
import dev.lyricsworld.util.MiniHttpServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spotify Authorization Code flow with PKCE. The redirect goes to a one-shot HTTP server bound to
 * 127.0.0.1 only; no secret is needed (public client), tokens never leave this machine.
 * The user registers their own app at developer.spotify.com and supplies its Client ID.
 */
public final class SpotifyAuth {
    private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SCOPE = "user-read-currently-playing user-read-playback-state";

    private final SpotifyTokenStore store;
    private volatile String accessToken;
    private volatile long accessExpiresAtMs;
    private volatile String refreshToken;
    private volatile String clientId = "";
    private volatile String status = "Not connected";
    private MiniHttpServer pending;

    public SpotifyAuth(SpotifyTokenStore store) {
        this.store = store;
        this.refreshToken = store.load();
        if (refreshToken != null) status = "Connected (saved login)";
    }

    public String status() { return status; }

    public boolean isConnected() { return refreshToken != null; }

    public void setClientId(String id) { this.clientId = id == null ? "" : id.trim(); }

    public void disconnect() {
        refreshToken = null;
        accessToken = null;
        store.clear();
        stopPending();
        status = "Not connected";
    }

    /**
     * Starts the login: opens a loopback listener and returns the URL the user must open in a browser.
     * Completes asynchronously; check {@link #status()}.
     */
    public synchronized String beginLogin(int port) throws IOException {
        if (clientId.isEmpty()) throw new IOException("Enter your Spotify Client ID first");
        stopPending();
        final String verifier = randomUrlSafe(64);
        final String state = randomUrlSafe(16);
        final String redirect = "http://127.0.0.1:" + port + "/callback";
        final String challenge = sha256Base64Url(verifier);

        MiniHttpServer srv = MiniHttpServer.start(port, new MiniHttpServer.Handler() {
            @Override public MiniHttpServer.Response handle(MiniHttpServer.Request req) {
                if (!"/callback".equals(req.path)) return MiniHttpServer.Response.text(404, "not found");
                Map<String, String> p = parseQuery(req.query);
                String msg;
                if (!state.equals(p.get("state"))) {
                    msg = "Login rejected: state mismatch.";
                    status = "Login failed (state mismatch)";
                } else if (p.get("error") != null) {
                    msg = "Spotify said: " + p.get("error");
                    status = "Login failed: " + p.get("error");
                } else {
                    try {
                        exchange(p.get("code"), verifier, redirect);
                        msg = "Spotify connected! You can close this tab and go back to Minecraft.";
                    } catch (IOException e) {
                        Log.warn("Spotify token exchange failed", e);
                        msg = "Login failed: " + e.getMessage();
                        status = msg;
                    }
                }
                new Thread(new Runnable() {
                    @Override public void run() { stopPending(); }
                }, "LyricsWorld-auth-stop").start();
                return MiniHttpServer.Response.html(200,
                        "<html><body style='font-family:sans-serif;text-align:center;margin-top:20vh'><h2>LyricsWorld</h2><p>"
                        + escape(msg) + "</p></body></html>");
            }
        });
        pending = srv;
        status = "Waiting for browser login...";
        // auto-timeout the listener after 5 minutes
        final MiniHttpServer started = srv;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try { Thread.sleep(5 * 60 * 1000L); } catch (InterruptedException e) { return; }
                synchronized (SpotifyAuth.this) {
                    if (pending == started) {
                        stopPending();
                        if (!isConnected()) status = "Login timed out";
                    }
                }
            }
        }, "LyricsWorld-auth-timeout");
        t.setDaemon(true);
        t.start();

        return AUTH_URL + "?response_type=code&client_id=" + Http.enc(clientId)
                + "&scope=" + Http.enc(SCOPE) + "&redirect_uri=" + Http.enc(redirect)
                + "&state=" + state + "&code_challenge_method=S256&code_challenge=" + challenge;
    }

    /** Returns a valid access token, refreshing if necessary; null if not connected or refresh failed. */
    public synchronized String accessToken() {
        if (refreshToken == null || clientId.isEmpty()) return null;
        if (accessToken != null && System.currentTimeMillis() < accessExpiresAtMs - 30_000) return accessToken;
        try {
            Map<String, String> f = new LinkedHashMap<String, String>();
            f.put("grant_type", "refresh_token");
            f.put("refresh_token", refreshToken);
            f.put("client_id", clientId);
            Http.Result r = Http.postForm(TOKEN_URL, f);
            if (!r.ok()) {
                if (r.code == 400 || r.code == 401) {
                    status = "Spotify login expired - please reconnect";
                    refreshToken = null;
                    store.clear();
                } else {
                    status = "Spotify token refresh failed (HTTP " + r.code + ")";
                }
                return null;
            }
            readTokens(Json.obj(r.body));
            return accessToken;
        } catch (IOException e) {
            status = "Spotify unreachable: " + e.getMessage();
            return null;
        }
    }

    private void exchange(String code, String verifier, String redirect) throws IOException {
        if (code == null) throw new IOException("no authorization code");
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("grant_type", "authorization_code");
        f.put("code", code);
        f.put("redirect_uri", redirect);
        f.put("client_id", clientId);
        f.put("code_verifier", verifier);
        Http.Result r = Http.postForm(TOKEN_URL, f);
        if (!r.ok()) throw new IOException("token endpoint answered HTTP " + r.code);
        readTokens(Json.obj(r.body));
        status = "Connected";
    }

    private void readTokens(JsonObject o) {
        accessToken = Json.str(o, "access_token", null);
        accessExpiresAtMs = System.currentTimeMillis() + Json.num(o, "expires_in", 3600) * 1000L;
        String rt = Json.str(o, "refresh_token", null);
        if (rt != null) {
            refreshToken = rt;
            store.save(rt);
        }
    }

    private synchronized void stopPending() {
        if (pending != null) {
            pending.close();
            pending = null;
        }
    }

    static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (q == null) return m;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i <= 0) continue;
            try {
                m.put(java.net.URLDecoder.decode(pair.substring(0, i), "UTF-8"),
                        java.net.URLDecoder.decode(pair.substring(i + 1), "UTF-8"));
            } catch (java.io.UnsupportedEncodingException ignored) { }
        }
        return m;
    }

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256Base64Url(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
