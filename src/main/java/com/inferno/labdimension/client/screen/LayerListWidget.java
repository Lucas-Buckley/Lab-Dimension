package com.inferno.labdimension.client.screen;

import com.inferno.labdimension.network.LayerSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Editable stack of superflat layers, bottom-to-top by list order. Each row has
 * a block-id field, a height field, and reorder/remove buttons -- order matters
 * here (it determines the generated stack), so unlike PlayerListWidget this list
 * needs up/down, not just remove.
 */
public class LayerListWidget extends ContainerObjectSelectionList<LayerListWidget.LayerEntry> {

    // Computed once -- BuiltInRegistries.BLOCK is a static bootstrap registry that
    // doesn't change at runtime, and this list can be reused by every row's dropdown.
    private static List<String> blockIdCache;

    private static List<String> blockIds() {
        if (blockIdCache == null) {
            blockIdCache = BuiltInRegistries.BLOCK.keySet().stream()
                    .map(Object::toString)
                    .collect(Collectors.toUnmodifiableList());
        }
        return blockIdCache;
    }

    public LayerListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    /** Delegates to whichever row's block-id field currently owns the suggestion
     *  dropdown, if any. Must be called by the owning Screen AFTER super.render(),
     *  same top-level pass as the screen's own field dropdowns, so it always draws
     *  on top regardless of which rows are below it in the scroll. */
    public void renderActiveSuggestion(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (LayerEntry e : this.children()) {
            e.blockSuggestions.renderOverlay(guiGraphics, mouseX, mouseY);
        }
    }

    /** Returns true if some row's dropdown consumed the key. */
    public boolean suggestionKeyPressed(int keyCode, int scanCode, int modifiers) {
        for (LayerEntry e : this.children()) {
            if (e.blockSuggestions.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    /** Returns true if some row's dropdown consumed the click. */
    public boolean suggestionMouseClicked(double mouseX, double mouseY, int button) {
        for (LayerEntry e : this.children()) {
            if (e.blockSuggestions.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public int getRowWidth() {
        return Math.min(280, this.width - 20);
    }

    public void setLayers(List<LayerSpec> layers) {
        this.clearEntries();
        for (LayerSpec l : layers) {
            this.addEntry(new LayerEntry(this, l.block(), l.height()));
        }
    }

    public List<LayerSpec> currentLayers() {
        List<LayerSpec> out = new ArrayList<>();
        for (LayerEntry e : this.children()) {
            out.add(e.toSpec());
        }
        return out;
    }

    public int currentSurfaceHeight() {
        int sum = 0;
        for (LayerSpec l : currentLayers()) sum += Math.max(0, l.height());
        return sum;
    }

    public void addLayer(String block, int height) {
        this.addEntry(new LayerEntry(this, block, height));
    }

    void moveUp(LayerEntry entry) {
        swap(entry, -1);
    }

    void moveDown(LayerEntry entry) {
        swap(entry, 1);
    }

    private void swap(LayerEntry entry, int delta) {
        List<LayerEntry> entries = new ArrayList<>(this.children());
        int i = entries.indexOf(entry);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= entries.size()) return;
        List<LayerSpec> specs = new ArrayList<>();
        for (LayerEntry e : entries) specs.add(e.toSpec());
        LayerSpec tmp = specs.get(i);
        specs.set(i, specs.get(j));
        specs.set(j, tmp);
        setLayers(specs);
    }

    void remove(LayerEntry entry) {
        this.removeEntry(entry);
    }

    public static final class LayerEntry extends ContainerObjectSelectionList.Entry<LayerEntry> {
        private final LayerListWidget parent;
        private final EditBox blockField;
        private final EditBox heightField;
        private final AbstractWidget upButton;
        private final AbstractWidget downButton;
        private final AbstractWidget removeButton;
        final SuggestionDropdown blockSuggestions;

        LayerEntry(LayerListWidget parent, String block, int height) {
            this.parent = parent;
            this.blockField = new EditBox(Minecraft.getInstance().font, 0, 0, 150, 18, Component.literal("Block"));
            this.blockField.setMaxLength(64);
            this.blockField.setValue(block);
            this.blockSuggestions = new SuggestionDropdown(this.blockField, LayerListWidget::blockIds);

            this.heightField = new EditBox(Minecraft.getInstance().font, 0, 0, 40, 18, Component.literal("Height"));
            this.heightField.setMaxLength(3);
            this.heightField.setValue(Integer.toString(height));
            this.heightField.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));

            this.upButton = Button.builder(Component.literal("^"), b -> parent.moveUp(this)).size(18, 18).build();
            this.downButton = Button.builder(Component.literal("v"), b -> parent.moveDown(this)).size(18, 18).build();
            this.removeButton = Button.builder(Component.literal("X"), b -> parent.remove(this)).size(18, 18).build();
        }

        LayerSpec toSpec() {
            int h;
            try {
                h = Integer.parseInt(heightField.getValue().trim());
            } catch (NumberFormatException e) {
                h = 1;
            }
            return new LayerSpec(blockField.getValue().trim(), Math.max(1, h));
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                            int mouseX, int mouseY, boolean hovering, float partialTick) {
            int x = left;
            blockField.setX(x);
            blockField.setY(top);
            blockField.render(guiGraphics, mouseX, mouseY, partialTick);
            x += 154;

            heightField.setX(x);
            heightField.setY(top);
            heightField.render(guiGraphics, mouseX, mouseY, partialTick);
            x += 44;

            upButton.setX(x);
            upButton.setY(top);
            upButton.render(guiGraphics, mouseX, mouseY, partialTick);
            x += 20;

            downButton.setX(x);
            downButton.setY(top);
            downButton.render(guiGraphics, mouseX, mouseY, partialTick);
            x += 20;

            removeButton.setX(x);
            removeButton.setY(top);
            removeButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(blockField, heightField, upButton, downButton, removeButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(blockField, heightField, upButton, downButton, removeButton);
        }
    }
}
