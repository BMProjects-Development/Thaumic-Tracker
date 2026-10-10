package org.bmp.thaumictracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;

/** A small ribbon on the book cover, drawn entirely in GUI coordinates. */
public final class TrackBookmark extends Button {
    private boolean tracked;

    public TrackBookmark(int screenWidth, int screenHeight, boolean tracked, OnPress onPress) {
        // Hang below the left page, clear of both side tabs, corner arrows and the central back button.
        // The 1.0.2 book is 181 px high at 1.3 scale; keep the whole ribbon visible in small windows.
        super(Math.clamp(Math.round(screenWidth / 2f - 88), 4, Math.max(4, screenWidth - 22)),
                Math.clamp(Math.round((screenHeight + 235.3f) / 2) - 13, 4, Math.max(4, screenHeight - 28)),
                18, 24, TrackerSnapshot.tr("track"), onPress, DEFAULT_NARRATION);
        setTracked(tracked);
    }

    public void setTracked(boolean tracked) {
        this.tracked = tracked;
        setMessage(TrackerSnapshot.tr(tracked ? "untrack" : "track"));
        setTooltip(Tooltip.create(getMessage().copy().append("\n").append(TrackerSnapshot.tr("track_tooltip"))));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean highlight = isHoveredOrFocused();
        int border = highlight ? 0xFFE0CAA0 : 0xFF8B7763;
        int fabric = tracked ? (highlight ? 0xFF80658F : 0xFF655075)
                : (highlight ? 0xFF625665 : 0xFF493F4D);
        int ink = tracked || highlight ? 0xFFF0DEC0 : 0xFFB7A68D;
        graphics.fill(x + 2, y + 2, x + 19, y + 20, 0x48000000);
        graphics.fill(x + 1, y, x + 17, y + 19, border);
        graphics.fill(x, y + 1, x + 18, y + 18, border);
        graphics.fill(x + 1, y + 1, x + 17, y + 18, fabric);
        // Split tail of the ribbon.
        for (int row = 0; row < 5; row++) {
            graphics.fill(x, y + 18 + row, x + 8 - row, y + 19 + row, border);
            graphics.fill(x + 10 + row, y + 18 + row, x + 18, y + 19 + row, border);
            graphics.fill(x + 1, y + 18 + row, x + 7 - row, y + 19 + row, fabric);
            graphics.fill(x + 11 + row, y + 18 + row, x + 17, y + 19 + row, fabric);
        }
        graphics.fill(x + 3, y + 2, x + 15, y + 3, 0x30FFFFFF);
        // A tiny pin: head, shoulders and needle. The active ribbon also has a gold stitch.
        graphics.fill(x + 6, y + 6, x + 12, y + 7, ink);
        graphics.fill(x + 7, y + 7, x + 11, y + 10, ink);
        graphics.fill(x + 5, y + 10, x + 13, y + 11, ink);
        graphics.fill(x + 8, y + 11, x + 10, y + 15, ink);
        if (tracked) graphics.fill(x + 3, y + 4, x + 4, y + 16, 0xFFD5B786);
    }
}
