package dev.lyricsworld.ui;

import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class UiMainTest {
    @Test
    @SuppressWarnings("unchecked")
    public void miniJsonRoundTrip() {
        String src = "{\"a\":1,\"b\":[true,false,null,\"x\\n\\\"y\\\"\"],\"c\":{\"d\":-2.5e1},\"e\":\"\\u00e9\"}";
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) UiMain.Json.parse(src);
        assertEquals(1.0, (Double) m.get("a"), 0);
        assertEquals(-25.0, (Double) ((java.util.Map<String, Object>) m.get("c")).get("d"), 0);
        assertEquals("é", m.get("e"));
        String again = UiMain.Json.write(m);
        assertEquals(m, UiMain.Json.parse(again));
    }

    @Test
    public void nestedClassListMatchesCompiledClasses() throws Exception {
        URL u = UiMain.class.getResource("UiMain.class");
        File dir = new File(u.toURI()).getParentFile();
        Set<String> named = new HashSet<String>();
        for (String f : dir.list()) {
            if (f.startsWith("UiMain$") && f.endsWith(".class")) {
                String n = f.substring("UiMain$".length(), f.length() - ".class".length());
                if (!Character.isDigit(n.charAt(0))) named.add(n);
            }
        }
        assertEquals("UiMain.NESTED must list every named nested class", named, new HashSet<String>(Arrays.asList(UiMain.NESTED)));
    }
}
