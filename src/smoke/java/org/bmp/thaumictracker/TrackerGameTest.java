package org.bmp.thaumictracker;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.capability.PlayerResearchAccess;
import com.leclowndu93150.thaumaturge.api.research.IResearchEntry;
import com.leclowndu93150.thaumaturge.api.research.ResearchRequirement;
import com.leclowndu93150.thaumaturge.api.research.pool.AspectPoolAccess;
import com.leclowndu93150.thaumaturge.content.research.PlayerKnowledge;
import com.leclowndu93150.thaumaturge.content.research.ResearchManager;
import com.leclowndu93150.thaumaturge.content.research.note.ResearchNoteData;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@EventBusSubscriber(modid = ThaumicTracker.ID)
@PrefixGameTestTemplate(false)
public final class TrackerGameTest {

    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("thaumictracker.gameTests")) event.register(TrackerGameTest.class);
    }

    @GameTest(templateNamespace = ThaumicTracker.ID, template = "empty", timeoutTicks = 100)
    public static void requirements(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TrackerTest"));
        var knowledge = (PlayerKnowledge) PlayerResearchAccess.of(player);
        var id = ResourceLocation.parse("thaumictracker:tracker_test");
        knowledge.wipe();
        player.getInventory().clearContent();
        check(TrackerSnapshot.read(player, ResourceLocation.parse("pack:removed")).rows().isEmpty(), "Removed entry failed");
        check(TrackerSnapshot.read(player, id).rows().isEmpty(), "Locked entry exposed requirements");
        knowledge.learn(id);
        var empty = TrackerSnapshot.read(player, id);
        check(empty.rows().size() == 6 && !empty.ready(), "Initial requirement set incorrect: " + empty);
        check(empty.rows().stream().noneMatch(TrackerSnapshot.Row::met), "Empty requirements unexpectedly satisfied");
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        player.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        player.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 2));
        player.getInventory().setItem(3, new ItemStack(Items.BIRCH_PLANKS, 2));
        player.getInventory().setItem(4, new ItemStack(Items.GOLD_INGOT, 1));
        var held = TrackerSnapshot.read(player, id);
        check(held.rows().get(1).met() && held.rows().get(2).met(), "Split stacks or item tags miscounted");
        check(held.rows().get(3).met(), "Native binary craft semantics not respected");
        player.getInventory().setItem(4, ItemStack.EMPTY);
        knowledge.learn(ResearchManager.craftedKey(ResourceLocation.parse("minecraft:gold_ingot")));
        check(TrackerSnapshot.read(player, id).rows().get(3).met(), "Craft history ignored");
        var prerequisite = ResourceLocation.parse("thaumictracker:prerequisite");
        knowledge.learn(prerequisite);
        knowledge.markComplete(prerequisite);
        knowledge.learn(ResearchNoteData.learnKey(id, 0));
        var aer = player.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY)
                .getOrThrow(ResourceKey.create(IAspect.REGISTRY_KEY, ResourceLocation.parse("thaumaturge:aer")));
        AspectPoolAccess.spend(player, aer, AspectPoolAccess.amount(player, aer));
        AspectPoolAccess.grant(player, aer, 4);
        var ready = TrackerSnapshot.read(player, id);
        check(ready.ready() && ready.rows().stream().allMatch(TrackerSnapshot.Row::met), "Theory/aspect/prerequisite checks disagree with server");
        // Losing a required item must clear readiness immediately on the next snapshot.
        player.getInventory().setItem(0, ItemStack.EMPTY);
        check(!TrackerSnapshot.read(player, id).ready(), "Inventory removal did not clear readiness");
        knowledge.setStage(id, 1);
        var next = TrackerSnapshot.read(player, id);
        check(next.rows().size() == 1 && next.rows().getFirst().icon().is(Items.GOLDEN_APPLE), "Stage change did not replace requirements");
        knowledge.setStage(id, 2);
        var later = TrackerSnapshot.read(player, id);
        check(later.rows().size() == 3 && later.rows().stream().noneMatch(TrackerSnapshot.Row::met),
                "Unexpected later-stage requirements: " + later);
        check(later.rows().getLast().count().equals("4 / 6"), "Observation rows must use the maximum cost, not their sum");
        knowledge.learn(ResearchNoteData.learnKey(id, 1));
        check(!TrackerSnapshot.read(player, id).rows().get(1).met(), "Learning one note satisfied a different note");
        knowledge.learn(ResearchNoteData.learnKey(id, 2));
        AspectPoolAccess.grant(player, aer, 2);
        check(TrackerSnapshot.read(player, id).ready(), "Later theory/aspect requirements did not become ready");
        check(AspectPoolAccess.amount(player, aer) == 6 && knowledge.stageOf(id) == 2,
                "Reading the tracker changed research or spent points");
        check(ResearchManager.advanceStage(player, id), "Server rejected a stage marked ready by the tracker");
        check(AspectPoolAccess.amount(player, aer) == 0 && knowledge.hasCompleted(id),
                "Server did not consume the displayed cost or complete the research");
        check(TrackerSnapshot.read(player, id).rows().isEmpty(), "Completed entry still displays costs");
        var patch = DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, Component.literal("Required name")).build();
        var named = new ResearchRequirement(HolderSet.direct(Items.PAPER.builtInRegistryHolder()), patch, 1);
        check(!named.matches(new ItemStack(Items.PAPER)), "Component requirements ignored");
        var shown = TrackerSnapshot.representative(named);
        check(named.matches(shown), "Displayed stack lost required components");
        // Exercise real 1.1 data: the same crystal item with a different aspect must not count.
        player.getInventory().clearContent();
        var bellowsId = ResourceLocation.parse("thaumaturge:bellows");
        var bellows = player.registryAccess().lookupOrThrow(IResearchEntry.REGISTRY_KEY)
                .getOrThrow(ResourceKey.create(IResearchEntry.REGISTRY_KEY, bellowsId)).value();
        var crystal = bellows.stages().getFirst().obtain().getFirst();
        var requiredCrystal = TrackerSnapshot.representative(crystal);
        check(requiredCrystal.has(TTDataComponents.CRYSTAL_ASPECT.get()) && crystal.matches(requiredCrystal),
                "Crystal icon lost its required aspect");
        var wrongCrystal = requiredCrystal.copy();
        wrongCrystal.remove(TTDataComponents.CRYSTAL_ASPECT.get());
        player.getInventory().setItem(0, wrongCrystal);
        knowledge.learn(bellowsId);
        var bellowsSnapshot = TrackerSnapshot.read(player, bellowsId);
        check(!bellowsSnapshot.rows().getFirst().met(), "Crystal without the required aspect counted");
        var terra = player.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY)
                .getOrThrow(ResourceKey.create(IAspect.REGISTRY_KEY, ResourceLocation.parse("thaumaturge:terra")));
        wrongCrystal.set(TTDataComponents.CRYSTAL_ASPECT.get(), new AspectInstance(terra, 1));
        check(!TrackerSnapshot.read(player, bellowsId).rows().getFirst().met(), "Crystal of a different aspect counted");
        check(bellowsSnapshot.rows().stream().anyMatch(row -> row.text().equals(TrackerSnapshot.tr("aspect",
                        Component.translatable("tooltip.thaumaturge.aspect.unknown")))),
                "Undiscovered aspect name was exposed");
        player.getInventory().setItem(0, requiredCrystal);
        check(TrackerSnapshot.read(player, bellowsId).rows().getFirst().met(), "Correct crystal was not counted");
        var trimsId = ResourceLocation.parse("thaumaturge:armor_trims");
        knowledge.learn(trimsId);
        var trims = player.registryAccess().lookupOrThrow(IResearchEntry.REGISTRY_KEY)
                .getOrThrow(ResourceKey.create(IResearchEntry.REGISTRY_KEY, trimsId)).value();
        var template = trims.stages().getFirst().obtain().getFirst();
        player.getInventory().setItem(0, new ItemStack(template.items().get(1)));
        check(TrackerSnapshot.read(player, trimsId).ready(), "New research did not accept an alternative smithing template");
        helper.succeed();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
