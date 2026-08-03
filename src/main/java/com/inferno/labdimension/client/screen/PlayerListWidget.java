package com.inferno.labdimension.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrollable list of player-name rows, each with its own remove button.
 * Must extend ContainerObjectSelectionList (not ObjectSelectionList) because
 * each row owns a focusable/clickable child widget -- ObjectSelectionList has
 * no mechanism to route mouse/keyboard events into per-row widgets.
 *
 * IMPORTANT: this widget's own `width` (constructor arg) must be set to the
 * actual desired column width, not something larger like "half the screen".
 * AbstractSelectionList.getRowLeft() = getX() + width/2 - rowWidth/2 -- if
 * `width` is much bigger than the row width, rows render centered deep inside
 * that oversized bounding box instead of flush against getX(), which is what
 * caused the whitelist column to visually drift into the blacklist column.
 * getRowWidth() below intentionally returns `this.width` with no cap so the
 * centering term is always zero and rows align exactly with getX().
 *
 * Themed white (whitelist) vs dark (blacklist) so the two lists read as
 * distinct "allow" / "deny" panels.
 */
public class PlayerListWidget extends ContainerObjectSelectionList<PlayerListWidget.Entry> {
    private final Consumer<String> onRemove;
    private boolean whitelistTheme = true;
    private boolean interactive = true;

    public PlayerListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight, Consumer<String> onRemove) {
        super(minecraft, width, height, y, itemHeight);
        this.onRemove = onRemove;
    }

    /** true = light "allow" theme (whitelist), false = dark "deny" theme (blacklist). */
    public void setTheme(boolean whitelist) {
        this.whitelistTheme = whitelist;
    }

    /** Grays the list and blocks removal when the list doesn't currently matter
     *  (e.g. the whitelist when visibility is PUBLIC). Adding/removing names is
     *  still driven by the screen's own add box/button, which the screen disables
     *  separately -- this only governs this list's own rendering/interaction. */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    public void setNames(List<String> names) {
        this.clearEntries();
        for (String name : names) {
            this.addEntry(new Entry(this, name, this.onRemove));
        }
    }

    public List<String> currentNames() {
        List<String> out = new ArrayList<>();
        for (Entry e : this.children()) {
            out.add(e.name);
        }
        return out;
    }

    public void addName(String name) {
        if (currentNames().stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
            this.addEntry(new Entry(this, name, this.onRemove));
        }
    }

    public void removeByName(String name) {
        this.children().stream()
                .filter(e -> e.name.equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(this::removeEntry);
    }

    public static final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final PlayerListWidget parent;
        private final String name;
        private final AbstractWidget removeButton;

        Entry(PlayerListWidget parent, String name, Consumer<String> onRemove) {
            this.parent = parent;
            this.name = name;
            // Back to the vanilla beveled Button per feedback -- sized to match
            // the row's own background box rather than a fixed 20x20.
            this.removeButton = Button.builder(Component.literal("X"), b -> onRemove.accept(name)).build();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                            int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean interactive = parent.interactive;
            int bg = parent.whitelistTheme
                    ? (interactive ? 0xCCE8E8E8 : 0x66E8E8E8)
                    : (interactive ? 0xCC3A1414 : 0x661A0A0A);
            int textColor = parent.whitelistTheme
                    ? (interactive ? 0x000000 : 0x666666)
                    : (interactive ? 0xFFCCCC : 0x774444);
            guiGraphics.fill(left, top, left + width, top + height - 1, bg);
            guiGraphics.drawString(Minecraft.getInstance().font, name, left + 4, top + (height - 8) / 2, textColor, false);

            int buttonSize = height - 1;
            this.removeButton.setWidth(buttonSize);
            this.removeButton.setHeight(buttonSize);
            this.removeButton.active = interactive;
            this.removeButton.setX(left + width - buttonSize - 2);
            this.removeButton.setY(top);
            this.removeButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(removeButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(removeButton);
        }
    }
}
