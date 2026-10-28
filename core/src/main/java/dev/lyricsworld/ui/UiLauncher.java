package dev.lyricsworld.ui;

import dev.lyricsworld.config.SettingsStore;
import dev.lyricsworld.util.Dirs;
import dev.lyricsworld.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Starts the external settings window as a separate JVM process: Swing/AWT inside Minecraft's own
 * process clashes with LWJGL/GLFW on macOS (both want the main thread), and a crash or hang of the
 * window must never be able to take the game down. The window dies with the game (it polls the
 * control server and exits when it disappears; we also kill it from a shutdown hook).
 */
public final class UiLauncher {
    private static final String MAIN = "dev.lyricsworld.ui.UiMain";
    private final Dirs dirs;
    private final ControlServer control;
    private final SettingsStore settings;
    private Process process;

    public UiLauncher(Dirs dirs, ControlServer control, SettingsStore settings) {
        this.dirs = dirs;
        this.control = control;
        this.settings = settings;
    }

    public synchronized void launchIfWanted() {
        if (!settings.get().showExternalWindow) return;
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                Log.info("No display available - external window not started (settings stay editable in settings.json)");
                return;
            }
        } catch (Throwable t) { // java.desktop missing from a trimmed runtime
            Log.info("No desktop support in this Java runtime - external window disabled");
            return;
        }
        launch();
    }

    public synchronized void launch() {
        if (process != null && process.isAlive()) return;
        try {
            String cp = resolveClasspath();
            if (cp == null) {
                Log.warn("Cannot locate the mod classes for the external window");
                return;
            }
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator
                    + (System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
            List<String> cmd = new ArrayList<String>();
            cmd.add(javaBin);
            cmd.add("-Xmx96m");
            cmd.add("-Djava.awt.headless=false");
            cmd.add("-Dapple.awt.application.name=LyricsWorld");
            cmd.add("-Dfile.encoding=UTF-8");
            cmd.add("-cp");
            cmd.add(cp);
            cmd.add(MAIN);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(dirs.logsDir.resolve("window.log").toFile()));
            Process p = pb.start();
            // endpoint + token go through stdin, not the command line (visible to other local users)
            try (OutputStream os = p.getOutputStream()) {
                os.write((control.port() + " " + control.token() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            process = p;
            Log.info("External window started");
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not start the external window", e);
        }
    }

    public synchronized void stop() {
        if (process != null) process.destroy();
    }

    /**
     * Classpath that contains {@link UiMain}. Normally the mod jar; when the class loader hides the jar
     * location (some mod loaders), the window classes are copied into a small temporary jar instead.
     */
    private String resolveClasspath() {
        try {
            URL loc = UiMain.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null && "file".equals(loc.getProtocol())) {
                File f = new File(loc.toURI());
                if (f.exists()) return f.getAbsolutePath();
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // fall through to extraction
        }
        try {
            Path jar = dirs.cacheDir.resolve("window.jar");
            Files.createDirectories(jar.getParent());
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
                copyClass(out, "dev/lyricsworld/ui/UiMain.class");
                for (int i = 1; i < 200; i++) {
                    if (!copyClass(out, "dev/lyricsworld/ui/UiMain$" + i + ".class")) break;
                }
                for (String n : UiMain.NESTED) copyClass(out, "dev/lyricsworld/ui/UiMain$" + n + ".class");
            }
            return jar.toAbsolutePath().toString();
        } catch (IOException | RuntimeException e) {
            Log.warn("Cannot extract window classes", e);
            return null;
        }
    }

    private static boolean copyClass(JarOutputStream out, String name) throws IOException {
        try (InputStream in = UiLauncher.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) return false;
            out.putNextEntry(new JarEntry(name));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.closeEntry();
            return true;
        }
    }
}
