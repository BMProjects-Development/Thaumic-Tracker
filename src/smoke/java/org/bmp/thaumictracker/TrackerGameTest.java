package org.bmp.thaumictracker;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.research.ResearchRequirement;
import com.leclowndu93150.thaumaturge.content.research.PlayerKnowledge;
import com.leclowndu93150.thaumaturge.content.research.ResearchManager;
import com.leclowndu93150.thaumaturge.content.research.note.ResearchNoteData;
import com.leclowndu93150.thaumaturge.content.research.pool.AspectPools;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@EventBusSubscriber(modid = ThaumicTracker.ID)
public final class TrackerGameTest extends GameTestInstance {
    public TrackerGameTest(TestData<Holder<TestEnvironmentDefinition<?>>> data) { super(data); }

    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("thaumictracker.gameTests")) return;
        var environment = event.registerEnvironment(Identifier.parse("thaumictracker:smoke"), new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(Identifier.parse("thaumictracker:requirements"), new TrackerGameTest(
                new TestData<>(environment, Identifier.parse("thaumaturge:empty_5x5x7"), 100, 0, true, Rotation.NONE)));
    }

    @Override public void run(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TrackerTest"));
        var knowledge = (PlayerKnowledge) KnowledgeAccess.of(player);
        var id = Identifier.parse("thaumictracker:tracker_test");
        knowledge.clear();
        player.getInventory().clearContent();
        check(TrackerSnapshot.read(player, Identifier.parse("pack:removed")).rows().isEmpty(), "Removed entry failed");
        check(TrackerSnapshot.read(player, id).rows().isEmpty(), "Locked entry exposed requirements");
        knowledge.addResearch(id);
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
        knowledge.addResearch(ResearchManager.craftedKey(Identifier.parse("minecraft:gold_ingot")));
        check(TrackerSnapshot.read(player, id).rows().get(3).met(), "Craft history ignored");
        var prerequisite = Identifier.parse("thaumictracker:prerequisite");
        knowledge.addResearch(prerequisite);
        knowledge.markComplete(prerequisite);
        knowledge.addResearch(ResearchNoteData.learnKey(id, 0));
        var aer = player.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY)
                .getOrThrow(ResourceKey.create(IAspect.REGISTRY_KEY, Identifier.parse("thaumaturge:aer")));
        AspectPools.grant(player, aer, 4);
        var ready = TrackerSnapshot.read(player, id);
        check(ready.ready() && ready.rows().stream().allMatch(TrackerSnapshot.Row::met), "Theory/aspect/prerequisite checks disagree with server");
        // Losing a required item must clear readiness immediately on the next snapshot.
        player.getInventory().setItem(0, ItemStack.EMPTY);
        check(!TrackerSnapshot.read(player, id).ready(), "Inventory removal did not clear readiness");
        knowledge.setResearchStage(id, 1);
        var next = TrackerSnapshot.read(player, id);
        check(next.rows().size() == 1 && next.rows().getFirst().icon().is(Items.GOLDEN_APPLE), "Stage change did not replace requirements");
        knowledge.markComplete(id);
        check(TrackerSnapshot.read(player, id).rows().isEmpty(), "Completed entry still displays costs");
        var patch = DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, Component.literal("Required name")).build();
        var named = new ResearchRequirement(HolderSet.direct(Items.PAPER.builtInRegistryHolder()), patch, 1);
        check(!named.matches(new ItemStack(Items.PAPER)), "Component requirements ignored");
        var shown = TrackerSnapshot.representative(named);
        check(named.matches(shown), "Displayed stack lost required components");
        helper.succeed();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public MapCodec<? extends GameTestInstance> codec() { throw new UnsupportedOperationException(); }
    @Override protected MutableComponent typeDescription() { return Component.literal("thaumictracker:requirements"); }
}
