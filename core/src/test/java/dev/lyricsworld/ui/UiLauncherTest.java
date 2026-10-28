package dev.lyricsworld.ui;

import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class UiLauncherTest {
    /** Regression: the extraction fallback once missed nested anonymous classes (UiMain$24$1). */
    @Test
    public void windowClassListCoversEveryCompiledUiMainClass() throws Exception {
        URL u = UiMain.class.getResource("UiMain.class");
        File dir = new File(u.toURI()).getParentFile();
        Set<String> onDisk = new HashSet<String>();
        for (String f : dir.list()) {
            if (f.startsWith("UiMain") && f.endsWith(".class")) onDisk.add("dev/lyricsworld/ui/" + f);
        }
        List<String> found = UiLauncher.windowClassNames(UiMain.class.getClassLoader());
        assertEquals(onDisk, new HashSet<String>(found));
        boolean deep = false;
        for (String n : found) if (n.matches(".*UiMain\\$\\d+\\$\\d+\\.class")) deep = true;
        assertTrue("expected doubly nested anonymous classes to exist and be listed", deep);
    }

    @Test
    public void displayCheckDoesNotThrow() {
        UiLauncher.displayLikelyAvailable();
    }
}
