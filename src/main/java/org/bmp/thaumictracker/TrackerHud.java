package org.bmp.thaumictracker;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Text and rectangles only; item icons are supplied by the existing item renderer. */
public final class TrackerHud {
    private static final int ROW_HEIGHT = 22;
    private static final int TEXT = 0xFFE8DEF2;
    private static final int MUTED = 0xFFB2A7BD;
    private static final int MET = 0xFF88D9A0;
    private int page;
    private int pages = 1;
    private Component lastStatus;

    public void nextPage() { page = (page + 1) % pages; }
    public void reset() { page = 0; pages = 1; lastStatus = null; }

    public void render(GuiGraphicsExtractor graphics, TrackerSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        int width = Math.min(224, graphics.guiWidth() - 16);
        if (width < 120 || graphics.guiHeight() < 130) return;
        int x = graphics.guiWidth() - width - 8;
        // Keep space for potion icons at the top and the hotbar at the bottom.
        int y = 54;
        var titleLines = firstTwo(mc.font.split(snapshot.title(), width - 16));
        var statusLines = firstTwo(mc.font.split(snapshot.status(), width - 16));
        int header = (titleLines.size() + statusLines.size()) * 10 + 12;
        int capacity = Math.clamp((graphics.guiHeight() - y - header - 64) / ROW_HEIGHT, 1, 8);
        pages = Math.max(1, (snapshot.rows().size() + capacity - 1) / capacity);
        if (!snapshot.status().equals(lastStatus)) { page = 0; lastStatus = snapshot.status(); }
        page = Math.floorMod(page, pages);
        int start = page * capacity;
        int end = Math.min(start + capacity, snapshot.rows().size());
        int footer = pages > 1 ? 14 : 0;
        int readyHeight = snapshot.ready() ? 22 : 0;
        int height = header + (end - start) * ROW_HEIGHT + footer + readyHeight + 4;
        graphics.fill(x, y, x + width, y + height, 0xC0181422);
        graphics.fill(x, y, x + 2, y + height, 0xFFC197E0);
        int lineY = y + 7;
        for (var line : titleLines) {
            graphics.text(mc.font, line, x + 8, lineY, TEXT, true);
            lineY += 10;
        }
        for (var line : statusLines) {
            graphics.text(mc.font, line, x + 8, lineY + 2, MUTED, true);
            lineY += 10;
        }
        int rowY = y + header;
        for (int i = start; i < end; i++) {
            var row = snapshot.rows().get(i);
            int color = row.met() ? MET : TEXT;
            graphics.text(mc.font, row.met() ? "✓" : "•", x + 8, rowY + 4, row.met() ? MET : MUTED, true);
            boolean icon = !row.icon().isEmpty();
            if (icon) graphics.item(row.icon(), x + 20, rowY + 1);
            int textX = x + (icon ? 40 : 22);
            int countWidth = row.count().isEmpty() ? 0 : mc.font.width(row.count()) + 8;
            List<FormattedCharSequence> lines = firstTwo(mc.font.split(row.text(), Math.max(24, x + width - textX - 8 - countWidth)));
            int textY = rowY + (lines.size() == 1 ? 4 : 0);
            for (var line : lines) {
                graphics.text(mc.font, line, textX, textY, color, true);
                textY += 10;
            }
            if (!row.count().isEmpty()) graphics.text(mc.font, row.count(),
                    x + width - 8 - mc.font.width(row.count()), rowY + 4, color, true);
            rowY += ROW_HEIGHT;
        }
        if (snapshot.ready()) {
            for (var line : firstTwo(mc.font.split(TrackerSnapshot.tr("ready"), width - 16))) {
                graphics.text(mc.font, line, x + 8, rowY, MET, true);
                rowY += 10;
            }
            rowY = y + header + (end - start) * ROW_HEIGHT + readyHeight;
        }
        if (pages > 1) graphics.text(mc.font,
                TrackerSnapshot.tr("page", page + 1, pages, ThaumicTracker.NEXT_PAGE.getTranslatedKeyMessage()),
                x + 8, rowY + 2, MUTED, true);
    }

    private static List<FormattedCharSequence> firstTwo(List<FormattedCharSequence> lines) {
        return lines.subList(0, Math.min(2, lines.size()));
    }
}
