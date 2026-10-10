package org.bmp.thaumictracker;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TrackerPreferencesTest {
    @TempDir Path folder;

    @Test void restoresSelectionsAndVisibilityWithoutMixingWorldsOrAccounts() throws Exception {
        Path file = folder.resolve("config/tracker.json");
        String a = TrackerPreferences.contextKey("local:world-a", "player-a");
        String b = TrackerPreferences.contextKey("local:world-b", "player-a");
        String c = TrackerPreferences.contextKey("local:world-a", "player-b");
        var settings = new TrackerPreferences(file);
        assertTrue(settings.visible());
        settings.select(a, "thaumaturge:first_steps");
        settings.select(b, "pack:custom_research");
        settings.setVisible(false);
        var restored = new TrackerPreferences(file);
        assertEquals("thaumaturge:first_steps", restored.selected(a));
        assertEquals("pack:custom_research", restored.selected(b));
        assertNull(restored.selected(c));
        assertFalse(restored.visible());
        restored.select(a, null);
        assertNull(new TrackerPreferences(file).selected(a));
        assertEquals("pack:custom_research", new TrackerPreferences(file).selected(b));
        assertFalse(Files.readString(file).contains("local:world"));
    }

    @Test void invalidOrEmptyConfigFallsBackToUsableDefaults() throws Exception {
        Path file = folder.resolve("tracker.json");
        for (String invalid : new String[] {"", "null", "{", "[]", "{}", "{\"selections\":null}"}) {
            Files.writeString(file, invalid);
            var settings = new TrackerPreferences(file);
            assertTrue(settings.visible(), invalid);
            assertNull(settings.selected("anything"));
            settings.select("anything", "pack:repaired");
            assertEquals("pack:repaired", new TrackerPreferences(file).selected("anything"));
        }
    }
}
