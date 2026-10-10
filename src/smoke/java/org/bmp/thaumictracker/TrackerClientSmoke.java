package org.bmp.thaumictracker;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.research.IResearchEntry;
import com.leclowndu93150.thaumaturge.client.screen.research.EntryDetailScreen;
import com.leclowndu93150.thaumaturge.content.research.PlayerKnowledge;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.bmp.thaumictracker.mixin.EntryDetailScreenAccessor;
import org.slf4j.LoggerFactory;

/** Opt-in integration checks in a disposable development world, excluded from release jars. */
@EventBusSubscriber(modid = ThaumicTracker.ID, value = Dist.CLIENT)
public final class TrackerClientSmoke {
    private static final Identifier ID = Identifier.parse("thaumictracker:tracker_test");
    private static int phase;
    private static int delay = 40;
    private static int ticks;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumictracker.clientSmoke")) return;
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 4000) throw new AssertionError("Client smoke test timed out at phase " + phase);
        if (delay-- > 0) return;
        if (phase == 0) {
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(2);
            mc.options.simulationDistance().set(5);
            mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("tracker-smoke-" + System.currentTimeMillis(),
                    new LevelSettings("Tracker smoke test", GameType.CREATIVE,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0, false, false), WorldPresets::createFlatWorldDimensions, mc.screen);
            phase = 1;
            return;
        }
        if (mc.player == null || mc.level == null) return;
        switch (phase) {
            case 1 -> {
                mutateServer(mc, knowledge -> knowledge.addResearch(ID));
                phase++; delay = 40;
            }
            case 2 -> {
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.getToastManager().clear();
                mc.gui.getChat().clearMessages(true);
                var holder = mc.player.registryAccess().lookupOrThrow(IResearchEntry.REGISTRY_KEY)
                        .getOrThrow(ResourceKey.create(IResearchEntry.REGISTRY_KEY, ID));
                var screen = new EntryDetailScreen(holder, ID, null);
                mc.setScreen(screen);
                check(((EntryDetailScreenAccessor) (Object) screen).thaumictracker$entryId().equals(ID), "Accessor Mixin not applied");
                check(button(mc).getMessage().equals(TrackerSnapshot.tr("track")), "Track button missing");
                phase++; delay = 20;
            }
            case 3 -> {
                capture(mc, "01-book.png");
                click(mc);
                check(button(mc).getMessage().equals(TrackerSnapshot.tr("untrack")), "Book did not route mouse click to tracker");
                mc.screen.resize(mc.screen.width, mc.screen.height);
                check(mc.screen.children().stream().filter(child -> child instanceof Button b && b.getMessage().equals(TrackerSnapshot.tr("untrack"))).count() == 1,
                        "Resize duplicated or lost tracker button");
                phase++; delay = 20;
            }
            case 4 -> {
                capture(mc, "02-book-tracked.png");
                mc.setScreen(null);
                phase++; delay = 30;
            }
            case 5 -> {
                capture(mc, "03-hud.png");
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET));
                phase++; delay = 15;
            }
            case 6 -> {
                capture(mc, "03-hud-page-2.png");
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_J));
                phase++; delay = 5;
            }
            case 7 -> {
                check(!preferences().visible(), "Toggle hotkey failed to hide HUD");
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_J));
                mutateServer(mc, knowledge -> knowledge.setResearchStage(ID, 1));
                phase++; delay = 30;
            }
            case 8 -> {
                check(preferences().visible(), "Toggle hotkey failed to show HUD");
                var snapshot = TrackerSnapshot.read(mc.player, ID);
                check(snapshot.rows().size() == 1 && snapshot.rows().getFirst().icon().is(Items.GOLDEN_APPLE), "Client mirror did not change stage");
                capture(mc, "04-next-stage.png");
                mutateServer(mc, knowledge -> knowledge.markComplete(ID));
                phase++; delay = 30;
            }
            case 9 -> {
                check(TrackerSnapshot.read(mc.player, ID).rows().isEmpty(), "Completed client entry still has costs");
                capture(mc, "05-completed.png");
                phase++; delay = 30;
            }
            case 10 -> {
                LoggerFactory.getLogger(TrackerClientSmoke.class).info("THAUMIC TRACKER CLIENT SMOKE PASSED: mixin, button click, resize, HUD, hotkey, stage sync, completion");
                mc.stop();
                phase++;
            }
        }
    }
    private static void mutateServer(Minecraft mc, java.util.function.Consumer<PlayerKnowledge> mutation) {
        var uuid = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            var knowledge = (PlayerKnowledge) KnowledgeAccess.of(player);
            mutation.accept(knowledge);
            player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
            player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 2));
            knowledge.sync(player);
        });
    }
    private static TrackerPreferences preferences() {
        return new TrackerPreferences(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("thaumictracker.json"));
    }
    private static Button button(Minecraft mc) {
        return mc.screen.children().stream().filter(child -> child instanceof Button b &&
                (b.getMessage().equals(TrackerSnapshot.tr("track")) || b.getMessage().equals(TrackerSnapshot.tr("untrack"))))
                .map(child -> (Button) child).findFirst().orElseThrow();
    }
    private static void click(Minecraft mc) {
        Button b = button(mc);
        var click = new MouseButtonEvent(b.getX() + 5, b.getY() + 5, new MouseButtonInfo(0, 0));
        check(mc.screen.mouseClicked(click, false), "Mouse click not handled");
        mc.screen.mouseReleased(click);
    }
    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, message -> {});
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
