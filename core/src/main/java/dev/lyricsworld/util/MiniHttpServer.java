package dev.lyricsworld.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Tiny HTTP/1.1 server bound to 127.0.0.1 only. Exists because the Java runtime bundled with the
 * Minecraft launcher does not ship the {@code jdk.httpserver} module. Connection: close, no keep-alive.
 * Requests whose Host header is not a loopback name are rejected (DNS-rebinding protection).
 */
public final class MiniHttpServer implements Closeable {
    public static final class Request {
        public String method = "";
        public String path = "";
        public String query = "";
        public final Map<String, String> headers = new HashMap<String, String>();
        public String body = "";
    }

    public static final class Response {
        public final int status;
        public final String contentType;
        public final byte[] body;

        public Response(int status, String contentType, String body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        public static Response json(int status, String json) { return new Response(status, "application/json; charset=utf-8", json); }

        public static Response html(int status, String html) { return new Response(status, "text/html; charset=utf-8", html); }

        public static Response text(int status, String text) { return new Response(status, "text/plain; charset=utf-8", text); }
    }

    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    private static final int MAX_HEADER = 16 * 1024;
    private static final int MAX_BODY = 256 * 1024;

    private final ServerSocket socket;
    private final Handler handler;
    private final ExecutorService pool;
    private volatile boolean closed;

    private MiniHttpServer(ServerSocket socket, Handler handler) {
        this.socket = socket;
        this.handler = handler;
        this.pool = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "LyricsWorld-http");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /** @param port 0 picks a free port */
    public static MiniHttpServer start(int port, Handler handler) throws IOException {
        ServerSocket ss = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"));
        final MiniHttpServer s = new MiniHttpServer(ss, handler);
        Thread t = new Thread(new Runnable() {
            @Override public void run() { s.acceptLoop(); }
        }, "LyricsWorld-http-accept");
        t.setDaemon(true);
        t.start();
        return s;
    }

    public int port() { return socket.getLocalPort(); }

    private void acceptLoop() {
        while (!closed) {
            try {
                final Socket c = socket.accept();
                pool.execute(new Runnable() {
                    @Override public void run() { serve(c); }
                });
            } catch (IOException e) {
                if (closed) return;
            } catch (RuntimeException e) {
                if (closed) return;
            }
        }
    }

    private void serve(Socket c) {
        try {
            c.setSoTimeout(5000);
            Response resp;
            try {
                Request req = parse(c.getInputStream());
                String host = req.headers.get("host");
                if (!hostOk(host)) {
                    resp = Response.text(403, "forbidden");
                } else {
                    resp = handler.handle(req);
                }
            } catch (SocketTimeoutException e) {
                return;
            } catch (IllegalArgumentException e) {
                resp = Response.text(400, "bad request");
            } catch (Exception e) {
                Log.warn("HTTP handler error", e);
                resp = Response.text(500, "internal error");
            }
            OutputStream os = c.getOutputStream();
            String head = "HTTP/1.1 " + resp.status + " " + reason(resp.status) + "\r\n"
                    + "Content-Type: " + resp.contentType + "\r\n"
                    + "Content-Length: " + resp.body.length + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n\r\n";
            os.write(head.getBytes(StandardCharsets.ISO_8859_1));
            os.write(resp.body);
            os.flush();
        } catch (IOException ignored) {
            // client went away
        } finally {
            try { c.close(); } catch (IOException ignored) { }
        }
    }

    private static boolean hostOk(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        int colon = h.lastIndexOf(':');
        if (colon > 0) h = h.substring(0, colon);
        return h.equals("127.0.0.1") || h.equals("localhost") || h.equals("[::1]");
    }

    static Request parse(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int b = in.read();
            if (b < 0) throw new IllegalArgumentException("eof in header");
            head.write(b);
            if (head.size() > MAX_HEADER) throw new IllegalArgumentException("header too large");
            if ((b == '\r' && (state == 0 || state == 2)) || (b == '\n' && (state == 1 || state == 3))) state++;
            else state = b == '\r' ? 1 : 0;
        }
        String[] lines = new String(head.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length < 2) throw new IllegalArgumentException("bad request line");
        Request r = new Request();
        r.method = first[0];
        String target = first[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        r.query = q < 0 ? "" : target.substring(q + 1);
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) r.headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
        }
        String cl = r.headers.get("content-length");
        if (cl != null) {
            int len;
            try {
                len = Integer.parseInt(cl);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad content-length");
            }
            if (len < 0 || len > MAX_BODY) throw new IllegalArgumentException("body too large");
            byte[] body = new byte[len];
            int off = 0;
            while (off < len) {
                int n = in.read(body, off, len - off);
                if (n < 0) throw new IllegalArgumentException("eof in body");
                off += n;
            }
            r.body = new String(body, StandardCharsets.UTF_8);
        }
        return r;
    }

    private static String reason(int s) {
        switch (s) {
            case 200: return "OK";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            default: return s >= 500 ? "Internal Server Error" : "OK";
        }
    }

    @Override
    public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
    }
}
