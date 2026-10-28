package dev.lyricsworld.util;

import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;

import static org.junit.Assert.*;

public class MiniHttpServerTest {
    private static MiniHttpServer echo() throws Exception {
        return MiniHttpServer.start(0, new MiniHttpServer.Handler() {
            @Override public MiniHttpServer.Response handle(MiniHttpServer.Request r) {
                if ("/boom".equals(r.path)) throw new IllegalStateException("x");
                return MiniHttpServer.Response.text(200, r.method + " " + r.path + "?" + r.query + " [" + r.body + "] "
                        + r.headers.get("x-test"));
            }
        });
    }

    @Test
    public void servesGetAndPost() throws Exception {
        MiniHttpServer s = echo();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + s.port() + "/a/b?x=1").openConnection();
            c.setRequestProperty("X-Test", "yes");
            assertEquals(200, c.getResponseCode());
            assertEquals("GET /a/b?x=1 [] yes", Http.readAll(c.getInputStream()));

            HttpURLConnection p = (HttpURLConnection) new URL("http://localhost:" + s.port() + "/p").openConnection();
            p.setRequestMethod("POST");
            p.setDoOutput(true);
            try (OutputStream os = p.getOutputStream()) { os.write("héllo".getBytes("UTF-8")); }
            assertEquals("POST /p? [héllo] null", Http.readAll(p.getInputStream()));
        } finally {
            s.close();
        }
    }

    @Test
    public void rejectsForeignHostHeader() throws Exception {
        MiniHttpServer s = echo();
        try (Socket sock = new Socket("127.0.0.1", s.port())) {
            sock.getOutputStream().write("GET / HTTP/1.1\r\nHost: evil.example.com\r\n\r\n".getBytes("ISO-8859-1"));
            String resp = Http.readAll(sock.getInputStream());
            assertTrue(resp, resp.startsWith("HTTP/1.1 403"));
        } finally {
            s.close();
        }
    }

    @Test
    public void handlerExceptionBecomes500AndServerSurvives() throws Exception {
        MiniHttpServer s = echo();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + s.port() + "/boom").openConnection();
            assertEquals(500, c.getResponseCode());
            HttpURLConnection ok = (HttpURLConnection) new URL("http://127.0.0.1:" + s.port() + "/ok").openConnection();
            assertEquals(200, ok.getResponseCode());
        } finally {
            s.close();
        }
    }

    @Test
    public void malformedRequestIs400() throws Exception {
        MiniHttpServer s = echo();
        try (Socket sock = new Socket("127.0.0.1", s.port())) {
            sock.getOutputStream().write("NONSENSE\r\n\r\n".getBytes("ISO-8859-1"));
            String resp = Http.readAll(sock.getInputStream());
            assertTrue(resp, resp.startsWith("HTTP/1.1 400"));
        } finally {
            s.close();
        }
    }

    @Test
    public void listensOnLoopbackOnly() throws Exception {
        MiniHttpServer s = echo();
        try {
            // connecting through a non-loopback address of this host must fail
            java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                for (java.net.InterfaceAddress a : ifs.nextElement().getInterfaceAddresses()) {
                    java.net.InetAddress ia = a.getAddress();
                    if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) {
                        try (Socket sock = new Socket()) {
                            sock.connect(new java.net.InetSocketAddress(ia, s.port()), 500);
                            fail("reachable on " + ia);
                        } catch (java.io.IOException expected) {
                            // good
                        }
                    }
                }
            }
        } finally {
            s.close();
        }
    }
}
