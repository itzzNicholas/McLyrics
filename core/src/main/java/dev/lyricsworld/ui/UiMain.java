package dev.lyricsworld.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The external LyricsWorld window. Runs in its own JVM (see {@link UiLauncher}); talks to the game over
 * the loopback control API. Deliberately self-contained (JDK only, nested helper classes) so it can
 * start from nothing but the mod jar. Pass {@code --demo} to open it with fake data, no game needed.
 */
public final class UiMain {
    /** Named nested classes, needed by the jar-extraction fallback in {@link UiLauncher}. Kept in sync by a test. */
    public static final String[] NESTED = {"Json", "Api", "Form"};

    // ======================================================================== minimal JSON

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String text) { Json j = new Json(text); j.ws(); Object v = j.value(); return v; }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = str();
                    ws();
                    i++; // ':'
                    m.put(k, value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == '}') return m;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<Object>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == ']') return l;
                }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.valueOf(s.substring(st, i));
        }

        private String str() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        @SuppressWarnings("unchecked")
        static String write(Object o) {
            StringBuilder sb = new StringBuilder();
            write(sb, o);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(StringBuilder sb, Object o) {
            if (o == null) sb.append("null");
            else if (o instanceof Map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    quote(sb, e.getKey());
                    sb.append(':');
                    write(sb, e.getValue());
                }
                sb.append('}');
            } else if (o instanceof List) {
                sb.append('[');
                boolean first = true;
                for (Object x : (List<Object>) o) {
                    if (!first) sb.append(',');
                    first = false;
                    write(sb, x);
                }
                sb.append(']');
            } else if (o instanceof String) quote(sb, (String) o);
            else if (o instanceof Double) {
                double d = (Double) o;
                if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
            } else sb.append(o);
        }

        private static void quote(StringBuilder sb, String s) {
            sb.append('"');
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                if (c == '"' || c == '\\') sb.append('\\').append(c);
                else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                else sb.append(c);
            }
            sb.append('"');
        }
    }

    // ======================================================================== HTTP to the game

    static final class Api {
        final int port;
        final String token;

        Api(int port, String token) { this.port = port; this.token = token; }

        @SuppressWarnings("unchecked")
        Map<String, Object> call(String method, String path, String body) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
            try {
                c.setRequestMethod(method);
                c.setConnectTimeout(1500);
                c.setReadTimeout(3000);
                c.setRequestProperty("X-LW-Token", token);
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
                }
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                StringBuilder sb = new StringBuilder();
                if (in != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                Object parsed = sb.length() > 0 && sb.charAt(0) == '{' ? Json.parse(sb.toString()) : new LinkedHashMap<String, Object>();
                Map<String, Object> m = (Map<String, Object>) parsed;
                m.put("_http", (double) code);
                return m;
            } finally {
                c.disconnect();
            }
        }
    }

    // ======================================================================== window

    static final class Form {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        Form() { panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12)); }

        void add(String label, JComponent c) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row++;
            g.insets = new Insets(3, 3, 3, 3);
            g.anchor = GridBagConstraints.WEST;
            if (label == null) {
                g.gridx = 0;
                g.gridwidth = 2;
                g.fill = GridBagConstraints.HORIZONTAL;
                g.weightx = 1;
                panel.add(c, g);
            } else {
                g.gridx = 0;
                panel.add(new JLabel(label), g);
                g.gridx = 1;
                g.weightx = 1;
                g.fill = GridBagConstraints.HORIZONTAL;
                panel.add(c, g);
            }
        }

        JComponent done() {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row;
            g.weighty = 1;
            panel.add(Box.createVerticalGlue(), g);
            return new JScrollPane(panel);
        }
    }

    private final Api api;
    private final boolean demo;
    private final Map<String, Object> settings = new LinkedHashMap<String, Object>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Timer pushTimer;
    private final List<Runnable> refreshers = new ArrayList<Runnable>();
    private JFrame frame;
    private JLabel connectionLabel = new JLabel("Connecting to Minecraft...");
    private JLabel trackLabel = new JLabel("Nothing playing");
    private JLabel statusLabel = new JLabel(" ");
    private JLabel spotifyStatus = new JLabel(" ");
    private JProgressBar progress = new JProgressBar(0, 1000);
    private JTextPane lyricsPane = new JTextPane();
    private JPanel categoryPanel = new JPanel();
    private final Map<String, JCheckBox> categoryBoxes = new LinkedHashMap<String, JCheckBox>();
    private String root = "", logs = "", cache = "", localLyrics = "";
    private int knownRev = -1;
    private int failures;
    private boolean loading;
    private int initialTab;

    private UiMain(Api api, boolean demo) {
        this.api = api;
        this.demo = demo;
        this.pushTimer = new Timer(350, new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { pushSettings(); }
        });
        pushTimer.setRepeats(false);
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("LyricsWorld window: no display, exiting");
            return;
        }
        boolean demo = args.length > 0 && "--demo".equals(args[0]);
        Api api = null;
        if (!demo) {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) return;
            String[] p = line.trim().split(" ");
            api = new Api(Integer.parseInt(p[0]), p[1]);
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // default look and feel is fine
        }
        final UiMain ui = new UiMain(api, demo);
        for (String a : args) if (a.startsWith("--tab=")) ui.initialTab = Integer.parseInt(a.substring(6));
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { ui.build(); }
        });
        ui.pollLoop();
    }

    // ---- building ----

    @SuppressWarnings("unchecked")
    private void build() {
        frame = new JFrame("LyricsWorld" + (demo ? " (demo)" : ""));
        frame.setDefaultCloseOperation(demo ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { System.exit(0); }
        });

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Now playing", nowPlayingTab());
        tabs.addTab("Connections", connectionsTab());
        tabs.addTab("World display", displayTab());
        tabs.addTab("Emoji", emojiTab());

        tabs.setSelectedIndex(initialTab);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(connectionLabel);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(button("Open cache folder", new Runnable() {
            @Override public void run() { openFolder(cache); }
        }));
        right.add(button("Open log folder", new Runnable() {
            @Override public void run() { openFolder(logs); }
        }));
        right.add(button("Local lyrics folder", new Runnable() {
            @Override public void run() { openFolder(localLyrics); }
        }));
        bottom.add(left, BorderLayout.WEST);
        bottom.add(right, BorderLayout.EAST);

        frame.getContentPane().add(tabs, BorderLayout.CENTER);
        frame.getContentPane().add(bottom, BorderLayout.SOUTH);
        frame.setSize(780, 620);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private JComponent nowPlayingTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        trackLabel.setFont(trackLabel.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        top.add(trackLabel);
        top.add(Box.createVerticalStrut(4));
        top.add(statusLabel);
        top.add(Box.createVerticalStrut(6));
        top.add(progress);
        p.add(top, BorderLayout.NORTH);
        lyricsPane.setEditable(false);
        lyricsPane.setContentType("text/html");
        p.add(new JScrollPane(lyricsPane), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(check("enabled", "Show lyrics in the game world"));
        bottom.add(button("Toggle in game", new Runnable() {
            @Override public void run() { post("/toggle", null); }
        }));
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JComponent connectionsTab() {
        Form f = new Form();
        f.add("Music source", combo("source", new String[]{"AUTO", "SPOTIFY", "APPLE_MUSIC", "SYSTEM"}));
        f.add(null, new JLabel("<html><b>Auto</b>: Spotify Web API if connected, otherwise whatever your system is playing.<br>"
                + "<b>Apple Music</b>: Music.app (macOS) / Apple Music for Windows (system media session).<br>"
                + "<b>System</b>: any player (Windows media session, macOS Music/Spotify, Linux MPRIS via playerctl).</html>"));
        f.add("Sync offset (ms)", slider("syncOffsetMs", -2000, 2000, 1, "ms"));
        f.add(null, check("allowEstimatedSync", "Allow estimated word sync (interpolated from line timestamps)"));
        f.add(null, check("fetchLyricsOnline", "Download lyrics from LRCLIB (disable to use only cache / local files)"));
        f.add(null, new JLabel(" "));
        f.add(null, new JLabel("<html><b>Spotify</b><br>1. Create an app at developer.spotify.com/dashboard<br>"
                + "2. Add the Redirect URI shown below to the app<br>"
                + "3. Paste its Client ID here and press Connect. (No client secret is used: PKCE.)</html>"));
        f.add("Client ID", text("spotifyClientId"));
        final JLabel redirect = new JLabel();
        refreshers.add(new Runnable() {
            @Override public void run() {
                redirect.setText("http://127.0.0.1:" + intOf(settings.get("spotifyRedirectPort"), 8898) + "/callback");
            }
        });
        f.add("Redirect URI", redirect);
        f.add("Redirect port", slider("spotifyRedirectPort", 1024, 65535, 1, ""));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(button("Connect Spotify", new Runnable() {
            @Override public void run() { connectSpotify(); }
        }));
        row.add(button("Disconnect", new Runnable() {
            @Override public void run() { post("/spotify/logout", null); }
        }));
        f.add(null, row);
        f.add(null, spotifyStatus);
        return f.done();
    }

    private JComponent displayTab() {
        Form f = new Form();
        f.add("Position", combo("position", new String[]{"BEHIND", "ABOVE", "FRONT"}));
        f.add("Distance (blocks)", slider("distance", 10, 120, 10, ""));
        f.add("Height (blocks)", slider("height", -10, 80, 10, ""));
        f.add("Scale", slider("scale", 25, 400, 100, "x"));
        f.add("Opacity", slider("opacity", 5, 100, 100, ""));
        f.add("Visible rows", slider("rows", 1, 7, 1, ""));
        f.add("Arc spread (deg/row)", slider("arcSpread", 0, 45, 1, ""));
        f.add("Font style", combo("fontStyle", new String[]{"NORMAL", "BOLD", "ITALIC", "BOLD_ITALIC"}));
        f.add("Current word colour", colorButton("colorCurrent"));
        f.add("Sung colour", colorButton("colorSung"));
        f.add("Upcoming colour", colorButton("colorUpcoming"));
        f.add(null, check("shadow", "Text shadow"));
        f.add(null, check("outline", "Text outline (more readable, slightly more draw calls)"));
        f.add(null, check("seeThroughBlocks", "Draw through blocks"));
        f.add(null, check("fadeWhenMoving", "Fade out while moving / fighting"));
        f.add("Opacity while moving", slider("movingOpacity", 5, 100, 100, ""));
        return f.done();
    }

    private JComponent emojiTab() {
        Form f = new Form();
        f.add(null, check("emojiEnabled", "Show emoji for the meaning of the lyrics"));
        f.add("Intensity", slider("emojiIntensity", 0, 100, 1, "%"));
        f.add("Size", slider("emojiSize", 25, 300, 100, "x"));
        f.add("Variety", slider("emojiVariety", 0, 100, 1, "%"));
        f.add("Cooldown (ms)", slider("emojiCooldownMs", 0, 5000, 1, "ms"));
        f.add(null, check("emojiMainOnly", "Only the main emoji (key concepts and phrases)"));
        f.add(null, new JLabel("Categories"));
        categoryPanel.setLayout(new java.awt.GridLayout(0, 3));
        f.add(null, categoryPanel);
        f.add(null, new JLabel("<html>Add your own rules by dropping a .json file in <i>config/lyricsworld/emoji/</i> "
                + "(same format as the bundled rules_*.json).</html>"));
        return f.done();
    }

    // ---- control factories (all bound to the `settings` map) ----

    private JCheckBox check(final String key, String label) {
        final JCheckBox cb = new JCheckBox(label);
        cb.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (loading) return;
                settings.put(key, cb.isSelected());
                changed();
            }
        });
        refreshers.add(new Runnable() {
            @Override public void run() { cb.setSelected(Boolean.TRUE.equals(settings.get(key))); }
        });
        return cb;
    }

    private JComboBox<String> combo(final String key, String[] values) {
        final JComboBox<String> cb = new JComboBox<String>(values);
        cb.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (loading) return;
                settings.put(key, String.valueOf(cb.getSelectedItem()));
                changed();
            }
        });
        refreshers.add(new Runnable() {
            @Override public void run() { cb.setSelectedItem(String.valueOf(settings.get(key))); }
        });
        return cb;
    }

    private JTextField text(final String key) {
        final JTextField tf = new JTextField(30);
        tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void upd() {
                if (loading) return;
                settings.put(key, tf.getText().trim());
                changed();
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { upd(); }
        });
        refreshers.add(new Runnable() {
            @Override public void run() {
                String v = String.valueOf(settings.get(key) == null ? "" : settings.get(key));
                if (!v.equals(tf.getText())) tf.setText(v);
            }
        });
        return tf;
    }

    /** Slider over integers; the stored value is slider/divisor (so divisor 100 gives 0.25..4.0). */
    private JComponent slider(final String key, int min, int max, final int divisor, final String unit) {
        final JSlider sl = new JSlider(min, max, min);
        final JLabel val = new JLabel(" ");
        val.setPreferredSize(new Dimension(70, 20));
        sl.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override public void stateChanged(javax.swing.event.ChangeEvent e) {
                val.setText(fmt(sl.getValue(), divisor) + unit);
                if (loading) return;
                if (divisor == 1) settings.put(key, (double) sl.getValue());
                else settings.put(key, sl.getValue() / (double) divisor);
                changed();
            }
        });
        refreshers.add(new Runnable() {
            @Override public void run() {
                Object v = settings.get(key);
                double d = v instanceof Double ? (Double) v : 0;
                sl.setValue((int) Math.round(d * divisor));
                val.setText(fmt(sl.getValue(), divisor) + unit);
            }
        });
        JPanel p = new JPanel(new BorderLayout());
        p.add(sl, BorderLayout.CENTER);
        p.add(val, BorderLayout.EAST);
        return p;
    }

    private static String fmt(int v, int divisor) {
        return divisor == 1 ? String.valueOf(v) : String.format("%.2f", v / (double) divisor);
    }

    private JButton colorButton(final String key) {
        final JButton b = new JButton("   ");
        b.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                Color c = JColorChooser.showDialog(frame, "Pick a colour", new Color(intOf(settings.get(key), 0xFFFFFF) & 0xFFFFFF));
                if (c != null) {
                    settings.put(key, (double) (0xFF000000 | (c.getRGB() & 0xFFFFFF)));
                    b.setBackground(c);
                    changed();
                }
            }
        });
        refreshers.add(new Runnable() {
            @Override public void run() { b.setBackground(new Color(intOf(settings.get(key), 0xFFFFFF) & 0xFFFFFF)); }
        });
        return b;
    }

    private JButton button(String label, final Runnable action) {
        JButton b = new JButton(label);
        b.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { action.run(); }
        });
        return b;
    }

    // ---- behaviour ----

    private static int intOf(Object o, int def) {
        return o instanceof Double ? (int) (long) ((Double) o).doubleValue() : def;
    }

    private void changed() {
        pushTimer.restart();
    }

    @SuppressWarnings("unchecked")
    private void pushSettings() {
        if (demo || api == null) return;
        final Map<String, Object> copy = new LinkedHashMap<String, Object>(settings);
        // colours must be written as signed 32-bit ints for Gson
        for (String k : new String[]{"colorCurrent", "colorSung", "colorUpcoming"}) {
            copy.put(k, (double) (int) (long) intOf(copy.get(k), 0xFFFFFF));
        }
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    Map<String, Object> r = api.call("POST", "/settings", Json.write(copy));
                    if (r.get("_http") != null && ((Double) r.get("_http")) == 200) knownRev++; // our own change bumps rev by one
                } catch (Exception ignored) {
                    // next poll shows the connection problem
                }
            }
        });
    }

    private void post(final String path, final String body) {
        if (api == null) return;
        io.execute(new Runnable() {
            @Override public void run() {
                try { api.call("POST", path, body); } catch (Exception ignored) { }
            }
        });
    }

    private void connectSpotify() {
        if (api == null) {
            JOptionPane.showMessageDialog(frame, "Demo mode: no game connected.");
            return;
        }
        pushSettings();
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    Thread.sleep(500); // let the client id reach the game first
                    final Map<String, Object> r = api.call("POST", "/spotify/login", null);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            if (r.get("url") != null) {
                                try {
                                    Desktop.getDesktop().browse(new URI((String) r.get("url")));
                                } catch (Exception e) {
                                    JOptionPane.showMessageDialog(frame, "Open this link in your browser:\n" + r.get("url"));
                                }
                            } else {
                                JOptionPane.showMessageDialog(frame, String.valueOf(r.get("error")));
                            }
                        }
                    });
                } catch (Exception ignored) { }
            }
        });
    }

    private void openFolder(String path) {
        try {
            File f = new File(path);
            f.mkdirs();
            Desktop.getDesktop().open(f);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Folder: " + path);
        }
    }

    private void pollLoop() throws Exception {
        while (true) {
            Map<String, Object> state = null;
            try {
                state = demo ? demoState() : api.call("GET", "/state", null);
                failures = 0;
            } catch (Exception e) {
                failures++;
            }
            final Map<String, Object> st = state;
            if (failures >= 6) System.exit(0); // the game is gone
            SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() { apply(st); }
            });
            Thread.sleep(500);
        }
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> st) {
        if (st == null) {
            connectionLabel.setText("Lost connection to Minecraft (" + failures + ")");
            connectionLabel.setForeground(new Color(0xB00020));
            return;
        }
        connectionLabel.setText("Connected - Minecraft " + st.get("minecraft") + " / " + st.get("loader")
                + " / LyricsWorld " + st.get("version"));
        connectionLabel.setForeground(new Color(0x1B7F3B));

        Map<String, Object> paths = (Map<String, Object>) st.get("paths");
        if (paths != null) {
            root = String.valueOf(paths.get("root"));
            logs = String.valueOf(paths.get("logs"));
            cache = String.valueOf(paths.get("cache"));
            localLyrics = String.valueOf(paths.get("localLyrics"));
        }
        int rev = intOf(st.get("rev"), 0);
        Map<String, Object> remote = (Map<String, Object>) st.get("settings");
        if (remote != null && (knownRev < 0 || rev != knownRev) && !pushTimer.isRunning()) {
            knownRev = rev;
            loading = true;
            try {
                settings.clear();
                settings.putAll(remote);
                buildCategories((List<Object>) st.get("categories"));
                for (Runnable r : refreshers) r.run();
            } finally {
                loading = false;
            }
        }

        Map<String, Object> t = (Map<String, Object>) st.get("track");
        if (t == null) {
            trackLabel.setText("Nothing playing");
            progress.setValue(0);
        } else {
            trackLabel.setText(t.get("artist") + " - " + t.get("title"));
            double dur = t.get("durationMs") instanceof Double ? (Double) t.get("durationMs") : 0;
            double pos = t.get("positionMs") instanceof Double ? (Double) t.get("positionMs") : 0;
            progress.setValue(dur > 0 ? (int) (1000 * pos / dur) : 0);
        }
        StringBuilder status = new StringBuilder();
        status.append(st.get("mediaStatus"));
        if (st.get("mediaError") != null) status.append("  |  ").append(st.get("mediaError"));
        status.append("  |  ").append(st.get("lyricsStatus"));
        statusLabel.setText(status.toString());
        spotifyStatus.setText("Spotify: " + st.get("spotifyStatus"));
        renderLyrics((Map<String, Object>) st.get("lyrics"));
    }

    @SuppressWarnings("unchecked")
    private void buildCategories(List<Object> cats) {
        if (cats == null || categoryBoxes.size() == cats.size()) {
            syncCategories();
            return;
        }
        categoryPanel.removeAll();
        categoryBoxes.clear();
        for (Object o : cats) {
            final String name = String.valueOf(o);
            final JCheckBox cb = new JCheckBox(name, true);
            cb.addActionListener(new java.awt.event.ActionListener() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (loading) return;
                    List<Object> disabled = new ArrayList<Object>();
                    for (Map.Entry<String, JCheckBox> en : categoryBoxes.entrySet()) {
                        if (!en.getValue().isSelected()) disabled.add(en.getKey());
                    }
                    settings.put("disabledEmojiCategories", disabled);
                    changed();
                }
            });
            categoryBoxes.put(name, cb);
            categoryPanel.add(cb);
        }
        categoryPanel.revalidate();
        syncCategories();
    }

    @SuppressWarnings("unchecked")
    private void syncCategories() {
        Object d = settings.get("disabledEmojiCategories");
        List<Object> disabled = d instanceof List ? (List<Object>) d : new ArrayList<Object>();
        for (Map.Entry<String, JCheckBox> en : categoryBoxes.entrySet()) {
            en.getValue().setSelected(!disabled.contains(en.getKey()));
        }
    }

    @SuppressWarnings("unchecked")
    private void renderLyrics(Map<String, Object> l) {
        if (l == null) {
            lyricsPane.setText("<html><body style='font-family:sans-serif;color:gray;padding:10px'>No lyrics yet.</body></html>");
            return;
        }
        int cur = intOf(l.get("current"), -1);
        StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;padding:10px'>");
        if (Boolean.TRUE.equals(l.get("estimated"))) {
            sb.append("<p style='color:#a06000'><i>Estimated word sync (source has line timestamps only)</i></p>");
        }
        for (Object o : (List<Object>) l.get("lines")) {
            Map<String, Object> line = (Map<String, Object>) o;
            int i = intOf(line.get("i"), -1);
            String text = String.valueOf(line.get("text"));
            if (text.isEmpty()) text = "&#9835;";
            if (i == cur) {
                sb.append("<p style='font-size:20px;color:#1b5e20'><b>");
                String[] words = text.split(" ");
                int w = intOf(line.get("word"), 0);
                for (int k = 0; k < words.length; k++) {
                    sb.append(k == w ? "<span style='color:#d48806'>" : "").append(esc(words[k]))
                            .append(k == w ? "</span>" : "").append(' ');
                }
                sb.append("</b></p>");
            } else {
                sb.append("<p style='color:").append(i < cur ? "#888" : "#444").append("'>").append(esc(text)).append("</p>");
            }
        }
        lyricsPane.setText(sb.append("</body></html>").toString());
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---- demo mode ----

    private long demoStart = System.currentTimeMillis();

    private Map<String, Object> demoState() {
        long pos = (System.currentTimeMillis() - demoStart) % 30000;
        String json = "{\"version\":\"demo\",\"minecraft\":\"-\",\"loader\":\"demo\",\"rev\":1,"
                + "\"settings\":{\"enabled\":true,\"source\":\"AUTO\",\"syncOffsetMs\":0,\"allowEstimatedSync\":true,"
                + "\"fetchLyricsOnline\":true,\"spotifyClientId\":\"\",\"spotifyRedirectPort\":8898,\"position\":\"BEHIND\","
                + "\"distance\":3.0,\"height\":2.7,\"scale\":1.0,\"opacity\":0.95,\"rows\":3,\"arcSpread\":12.0,"
                + "\"fontStyle\":\"NORMAL\",\"colorCurrent\":-8090,\"colorSung\":-5200000,\"colorUpcoming\":-1,"
                + "\"shadow\":true,\"outline\":true,\"seeThroughBlocks\":true,\"fadeWhenMoving\":true,\"movingOpacity\":0.3,"
                + "\"emojiEnabled\":true,\"emojiIntensity\":60,\"emojiMainOnly\":false,\"emojiVariety\":70,"
                + "\"emojiCooldownMs\":700,\"emojiSize\":1.0,\"disabledEmojiCategories\":[\"drinks\"]},"
                + "\"categories\":[\"love\",\"breakup\",\"sadness\",\"drinks\",\"fire\"],"
                + "\"paths\":{\"root\":\"/tmp\",\"cache\":\"/tmp\",\"logs\":\"/tmp\",\"localLyrics\":\"/tmp\"},"
                + "\"mediaStatus\":\"Playing via demo\",\"spotifyStatus\":\"Not connected\",\"lyricsStatus\":\"Lyrics: demo\","
                + "\"track\":{\"title\":\"Demo Song\",\"artist\":\"Demo Artist\",\"durationMs\":30000,\"positionMs\":" + pos + "},"
                + "\"lyrics\":{\"current\":1,\"estimated\":true,\"lines\":[{\"i\":0,\"text\":\"You broke my heart\"},"
                + "{\"i\":1,\"text\":\"under the moonlight tonight\",\"word\":" + (pos / 2000 % 4) + "},{\"i\":2,\"text\":\"I dance in the rain\"}]}}";
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Json.parse(json);
        return m;
    }
}
