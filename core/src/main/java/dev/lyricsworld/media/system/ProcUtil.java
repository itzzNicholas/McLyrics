package dev.lyricsworld.media.system;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs short-lived helper processes (osascript, playerctl) with a hard timeout. */
final class ProcUtil {
    private ProcUtil() {}

    /** @return stdout (UTF-8) or null if the command failed, timed out or is missing. */
    static String exec(long timeoutMs, String... cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            p.getOutputStream().close();
            final InputStream err = p.getErrorStream();
            Thread drain = new Thread(new Runnable() {
                @Override public void run() {
                    try { byte[] b = new byte[1024]; while (err.read(b) > 0) { /* discard */ } } catch (IOException ignored) { }
                }
            }, "LyricsWorld-proc-err");
            drain.setDaemon(true);
            drain.start();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream in = p.getInputStream();
            long deadline = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[4096];
            while (System.currentTimeMillis() < deadline) {
                if (in.available() > 0) {
                    int n = in.read(buf);
                    if (n < 0) break;
                    bos.write(buf, 0, n);
                } else if (!p.isAlive()) {
                    int n;
                    while (in.available() > 0 && (n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    break;
                } else {
                    Thread.sleep(10);
                }
            }
            if (p.isAlive()) {
                p.destroyForcibly();
                return null;
            }
            p.waitFor(100, TimeUnit.MILLISECONDS);
            return p.exitValue() == 0 ? new String(bos.toByteArray(), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (p != null && p.isAlive()) p.destroyForcibly();
        }
    }
}
