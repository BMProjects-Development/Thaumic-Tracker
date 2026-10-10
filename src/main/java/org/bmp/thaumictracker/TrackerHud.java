package org.bmp.thaumictracker;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** A compact, quiet panel; all content shares the same scale for crisp item/text alignment. */
public final class TrackerHud {
    private static final float SCALE = 0.8f;
    private static final int WIDTH = 216;
    private static final int TEXT = 0xFFE2DBE9;
    private static final int MUTED = 0xFFA99EB2;
    private static final int MET = 0xFF8CC8A1;
    private int page;
    private int pages = 1;
    private Component lastStatus;

    private record RowLayout(TrackerSnapshot.Row row, List<FormattedCharSequence> lines, int textOffset, int height) {}

    public void nextPage() { page = (page + 1) % pages; }
    public void reset() { page = 0; pages = 1; lastStatus = null; }

    public void render(GuiGraphicsExtractor graphics, TrackerSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        int width = Math.min(WIDTH, (int) ((graphics.guiWidth() - 16) / SCALE));
        if (width < 120 || graphics.guiHeight() < 130) return;
        int x = (int) ((graphics.guiWidth() - 8) / SCALE) - width;
        int y = Math.round(54 / SCALE);
        var title = lines(mc.font, snapshot.title(), width - 14, 2);
        var status = lines(mc.font, snapshot.status(), width - 14, 2);
        var ready = snapshot.ready() ? lines(mc.font, TrackerSnapshot.tr("ready"), width - 14, 2)
                : List.<FormattedCharSequence>of();
        int header = 7 + title.size() * 10 + 3 + status.size() * 9 + 7;
        int readyHeight = ready.isEmpty() ? 0 : ready.size() * 9 + 5;
        int rowBudget = Math.max(24, (int) ((graphics.guiHeight() - 54 - 48) / SCALE) - header - readyHeight - 16);
        List<List<RowLayout>> sections = paginate(mc.font, snapshot.rows(), width, rowBudget);
        pages = sections.size();
        if (!snapshot.status().equals(lastStatus)) { page = 0; lastStatus = snapshot.status(); }
        page = Math.floorMod(page, pages);
        List<RowLayout> visible = sections.get(page);
        int rowsHeight = visible.stream().mapToInt(RowLayout::height).sum();
        int height = header + rowsHeight + readyHeight + (pages > 1 ? 13 : 0) + 4;

        graphics.pose().pushMatrix();
        graphics.pose().scale(SCALE, SCALE);
        try {
            graphics.fill(x, y, x + width, y + height, 0xA0181520);
            graphics.fill(x, y, x + width, y + 1, 0x605F4E70);
            graphics.fill(x, y, x + 26, y + 1, 0xC0B69ACB);
            int lineY = y + 7;
            for (var line : title) {
                graphics.text(mc.font, line, x + 7, lineY, TEXT, false);
                lineY += 10;
            }
            lineY += 3;
            for (var line : status) {
                graphics.text(mc.font, line, x + 7, lineY, MUTED, false);
                lineY += 9;
            }
            graphics.fill(x + 7, y + header - 4, x + width - 7, y + header - 3, 0x205F4E70);
            int rowY = y + header;
            for (RowLayout layout : visible) {
                var row = layout.row();
                int color = row.met() ? MET : TEXT;
                if (row.met()) {
                    graphics.fill(x + 6, rowY + 5, x + 7, rowY + 7, MET);
                    graphics.fill(x + 7, rowY + 7, x + 8, rowY + 8, MET);
                    graphics.fill(x + 8, rowY + 5, x + 9, rowY + 7, MET);
                    graphics.fill(x + 9, rowY + 3, x + 10, rowY + 5, MET);
                } else {
                    graphics.fill(x + 7, rowY + 6, x + 9, rowY + 8, 0x907D708C);
                }
                if (!row.icon().isEmpty()) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 14, rowY + 1);
                    graphics.pose().scale(0.75f, 0.75f);
                    graphics.item(row.icon(), 0, 0);
                    graphics.pose().popMatrix();
                }
                int textY = rowY + (layout.lines().size() == 1 ? 3 : 0);
                for (var line : layout.lines()) {
                    graphics.text(mc.font, line, x + layout.textOffset(), textY, color, false);
                    textY += 9;
                }
                if (!row.count().isEmpty()) graphics.text(mc.font, row.count(),
                        x + width - 7 - mc.font.width(row.count()), rowY + 3, row.met() ? MET : MUTED, false);
                rowY += layout.height();
            }
            if (!ready.isEmpty()) {
                rowY += 3;
                for (var line : ready) {
                    graphics.text(mc.font, line, x + 7, rowY, MET, false);
                    rowY += 9;
                }
                rowY += 2;
            }
            if (pages > 1) graphics.text(mc.font,
                    TrackerSnapshot.tr("page", page + 1, pages, ThaumicTracker.NEXT_PAGE.getTranslatedKeyMessage()),
                    x + 7, rowY + 2, MUTED, false);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static List<List<RowLayout>> paginate(Font font, List<TrackerSnapshot.Row> rows, int width, int budget) {
        List<List<RowLayout>> sections = new ArrayList<>();
        List<RowLayout> current = new ArrayList<>();
        int height = 0;
        for (var row : rows) {
            int offset = row.icon().isEmpty() ? 15 : 31;
            int countWidth = row.count().isEmpty() ? 0 : font.width(row.count()) + 7;
            var text = lines(font, row.text(), Math.max(24, width - offset - 7 - countWidth), 2);
            int rowHeight = Math.max(14, text.size() * 9) + 3;
            if (!current.isEmpty() && (current.size() >= 5 || height + rowHeight > budget)) {
                sections.add(current);
                current = new ArrayList<>();
                height = 0;
            }
            current.add(new RowLayout(row, text, offset, rowHeight));
            height += rowHeight;
        }
        sections.add(current);
        return sections;
    }

    private static List<FormattedCharSequence> lines(Font font, Component text, int width, int limit) {
        var wrapped = font.split(text, width);
        if (wrapped.size() <= limit) return wrapped;
        // Reserve room for an ellipsis rather than silently cutting off a long requirement.
        wrapped = font.split(text, Math.max(12, width - font.width("…")));
        List<FormattedCharSequence> clipped = new ArrayList<>(wrapped.subList(0, limit));
        clipped.set(limit - 1, FormattedCharSequence.composite(clipped.getLast(),
                FormattedCharSequence.forward("…", Style.EMPTY)));
        return clipped;
    }
}
