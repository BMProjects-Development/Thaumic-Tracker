package org.bmp.thaumictracker;

import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import com.leclowndu93150.thaumaturge.api.research.IResearchEntry;
import com.leclowndu93150.thaumaturge.api.research.ResearchRequirement;
import com.leclowndu93150.thaumaturge.content.research.ResearchManager;
import com.leclowndu93150.thaumaturge.content.research.note.ResearchNoteData;
import com.leclowndu93150.thaumaturge.content.research.note.ResearchNotes;
import com.leclowndu93150.thaumaturge.content.research.pool.AspectPools;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A fresh view of the authoritative client mirror; never changes knowledge or sends packets. */
public record TrackerSnapshot(Component title, Component status, List<Row> rows, boolean ready) {
    public record Row(Component text, ItemStack icon, boolean met, String count) {
        public Row(Component text, ItemStack icon, boolean met) { this(text, icon, met, ""); }
    }

    public static TrackerSnapshot read(Player player, Identifier id) {
        var holder = player.registryAccess().lookup(IResearchEntry.REGISTRY_KEY)
                .flatMap(registry -> registry.get(ResourceKey.create(IResearchEntry.REGISTRY_KEY, id)));
        if (holder.isEmpty()) return new TrackerSnapshot(Component.literal(id.toString()),
                tr("missing"), List.of(), false);
        var entry = holder.get().value();
        var knowledge = KnowledgeAccess.of(player);
        Component title = Component.translatable(entry.nameKey());
        if (knowledge.isResearchComplete(id)) return new TrackerSnapshot(title, tr("complete"), List.of(), false);
        if (!knowledge.isResearchKnown(id)) return new TrackerSnapshot(title, tr("locked"), List.of(), false);
        int index = Math.clamp(knowledge.researchStage(id), 0, entry.stages().size() - 1);
        var stage = entry.stages().get(index);
        List<Row> rows = new ArrayList<>();
        for (Identifier required : stage.requiredResearch()) {
            Component name = ResearchNotes.entryName(player.registryAccess(), required);
            rows.add(new Row(tr("research", name), ItemStack.EMPTY, knowledge.isResearchComplete(required)));
        }
        for (ResearchRequirement required : stage.obtain()) {
            int have = ResearchManager.countMatching(player, required);
            ItemStack icon = representative(required);
            rows.add(new Row(tr("obtain", itemName(icon)), icon, have >= required.amount(),
                    Math.min(have, required.amount()) + " / " + required.amount()));
        }
        for (ResearchRequirement required : stage.craft()) {
            ItemStack icon = representative(required);
            rows.add(new Row(tr("craft", itemName(icon)), icon,
                    ResearchManager.isCraftSatisfied(player, knowledge, required)));
        }
        int ordinal = ResearchNotes.theoryRowsBefore(entry, index);
        for (var required : stage.requiredKnowledge()) {
            if (required.type() == KnowledgeType.THEORY) {
                Identifier learnKey = ResearchNoteData.learnKey(id, ordinal++);
                rows.add(new Row(tr("theory", ordinal), ItemStack.EMPTY, knowledge.isResearchKnown(learnKey)));
            }
        }
        // In Thaumaturge 1.0.2 observations cost aspect-pool points, and theory requires learned notes.
        // Display the real costs rather than incorrectly comparing the reward amount with knowledge levels.
        for (var cost : ResearchNotes.stageObservationCost(entry, stage).entries()) {
            boolean discovered = AspectPools.isDiscovered(player, cost.aspect());
            int have = AspectPools.amount(player, cost.aspect());
            Component name = discovered ? AspectComponents.name(cost.aspect())
                    : Component.translatable("tooltip.thaumaturge.aspect.unknown");
            rows.add(new Row(tr("aspect", name), ItemStack.EMPTY, discovered && have >= cost.amount(),
                    Math.min(have, cost.amount()) + " / " + cost.amount()));
        }
        boolean ready = ResearchManager.stageRequirementsMet(player, knowledge, entry, id, index);
        return new TrackerSnapshot(title, tr("stage", index + 1, entry.stages().size()), List.copyOf(rows), ready);
    }

    public static ItemStack representative(ResearchRequirement requirement) {
        return requirement.items().size() == 0 ? ItemStack.EMPTY
                : new ItemStack(requirement.items().get(0), 1, requirement.components());
    }
    private static Component itemName(ItemStack stack) {
        return stack.isEmpty() ? tr("no_items") : stack.getHoverName();
    }
    public static Component tr(String suffix, Object... args) {
        return Component.translatable("gui.thaumictracker." + suffix, args);
    }
}
