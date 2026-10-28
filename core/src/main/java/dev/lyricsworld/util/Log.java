package dev.lyricsworld.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Tiny file+console logger. Never throws: logging must not be able to crash the game. */
public final class Log {
    private static final long MAX_BYTES = 1024 * 1024;
    private static volatile Path file;

    private Log() {}

    public static void init(Path logFile) {
        try {
            Files.createDirectories(logFile.getParent());
            if (Files.exists(logFile) && Files.size(logFile) > MAX_BYTES) {
                Files.move(logFile, logFile.resolveSibling(logFile.getFileName() + ".old"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            file = logFile;
        } catch (Throwable ignored) {
            file = null;
        }
    }

    public static void info(String msg) { write("INFO", msg, null); }

    public static void warn(String msg) { write("WARN", msg, null); }

    public static void warn(String msg, Throwable t) { write("WARN", msg, t); }

    public static void error(String msg, Throwable t) { write("ERROR", msg, t); }

    public static void debug(String msg) {
        if (Boolean.getBoolean("lyricsworld.debug")) write("DEBUG", msg, null);
    }

    private static synchronized void write(String level, String msg, Throwable t) {
        try {
            StringBuilder sb = new StringBuilder(128);
            sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
              .append(" [").append(level).append("] ").append(msg);
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                sb.append('\n').append(sw);
            }
            String line = sb.toString();
            System.out.println("[LyricsWorld] " + (t == null ? msg : msg + " (" + t + ")"));
            Path f = file;
            if (f != null) {
                Files.write(f, (line + "\n").getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException | RuntimeException ignored) {
            // logging must never throw
        }
    }
}
