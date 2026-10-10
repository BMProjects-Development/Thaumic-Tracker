package org.bmp.thaumictracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local settings. Hash world/server identities so addresses and filesystem paths are not stored. */
public final class TrackerPreferences {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerPreferences.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final Map<String, String> selections = new HashMap<>();
    private boolean visible = true;

    public TrackerPreferences(Path path) {
        this.path = path;
        if (!Files.isRegularFile(path)) return;
        try (var reader = Files.newBufferedReader(path)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) {
                visible = data.visible;
                if (data.selections != null) {
                    data.selections.forEach((key, value) -> {
                        if (key != null && value != null) selections.put(key, value);
                    });
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Cannot read Thaumic Tracker settings at {}", path, e);
        }
    }

    public static String contextKey(String world, String player) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((world + "\n" + player).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String selected(String context) { return selections.get(context); }
    public boolean visible() { return visible; }
    public void setVisible(boolean value) { visible = value; save(); }
    public void select(String context, String entry) {
        if (entry == null) selections.remove(context);
        else selections.put(context, entry);
        save();
    }

    private void save() {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(new Data(visible, selections)));
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Cannot save Thaumic Tracker settings at {}", path, e);
        }
    }

    private static final class Data {
        boolean visible = true;
        Map<String, String> selections;
        private Data() {} // Gson must run the default initializer when fields are absent.
        Data(boolean visible, Map<String, String> selections) {
            this.visible = visible;
            this.selections = selections;
        }
    }
}
