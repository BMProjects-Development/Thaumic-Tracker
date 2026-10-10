package org.bmp.thaumictracker;

import com.leclowndu93150.thaumaturge.client.screen.research.EntryDetailScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.bmp.thaumictracker.mixin.EntryDetailScreenAccessor;
import org.lwjgl.glfw.GLFW;

/** Client-only add-on: compatible servers need only Thaumaturge. */
@Mod(value = ThaumicTracker.ID, dist = Dist.CLIENT)
public final class ThaumicTracker {
    public static final String ID = "thaumictracker";
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(ID, "main"));
    public static final KeyMapping TOGGLE = new KeyMapping("key.thaumictracker.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping NEXT_PAGE = new KeyMapping("key.thaumictracker.next_page", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
    private final TrackerPreferences preferences = new TrackerPreferences(FMLPaths.CONFIGDIR.get().resolve("thaumictracker.json"));
    private final TrackerHud hud = new TrackerHud();
    private Object connection;
    private String context;
    private Identifier selected;
    private TrackerSnapshot snapshot;
    private int ticks;

    public ThaumicTracker(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerHud);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::initScreen);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE);
        event.register(NEXT_PAGE);
    }
    private void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, Identifier.fromNamespaceAndPath(ID, "research"),
                (graphics, delta) -> {
                    if (preferences.visible() && selected != null && snapshot != null) hud.render(graphics, snapshot);
                });
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        updateSession(mc);
        if (mc.player == null || mc.level == null) return;
        if (mc.screen == null) {
            while (TOGGLE.consumeClick()) preferences.setVisible(!preferences.visible());
            while (NEXT_PAGE.consumeClick()) hud.nextPage();
        }
        if (++ticks % 10 == 0) refresh(mc);
    }

    private void updateSession(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            connection = null;
            context = null;
            selected = null;
            snapshot = null;
            ticks = 0;
            hud.reset();
            return;
        }
        if (connection == mc.getConnection()) return;
        connection = mc.getConnection();
        String world;
        if (mc.getSingleplayerServer() != null) {
            world = "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        } else if (mc.getCurrentServer() != null) {
            world = "server:" + mc.getCurrentServer().ip;
        } else {
            world = "remote:" + mc.getConnection().getConnection().getRemoteAddress();
        }
        context = TrackerPreferences.contextKey(world, mc.player.getUUID().toString());
        String stored = preferences.selected(context);
        selected = stored == null ? null : Identifier.tryParse(stored);
        hud.reset();
        refresh(mc);
    }

    private void refresh(Minecraft mc) {
        snapshot = selected == null || mc.player == null ? null : TrackerSnapshot.read(mc.player, selected);
    }

    private void initScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof EntryDetailScreen screen)) return;
        Minecraft mc = Minecraft.getInstance();
        updateSession(mc);
        Identifier entry = ((EntryDetailScreenAccessor) (Object) screen).thaumictracker$entryId();
        TrackBookmark button = new TrackBookmark(screen.width, screen.height, entry.equals(selected), pressed -> {
            updateSession(mc);
            if (context == null) return;
            selected = entry.equals(selected) ? null : entry;
            preferences.select(context, selected == null ? null : selected.toString());
            if (selected != null && !preferences.visible()) preferences.setVisible(true);
            hud.reset();
            refresh(mc);
            ((TrackBookmark) pressed).setTracked(entry.equals(selected));
        });
        event.addListener(button);
    }
}
