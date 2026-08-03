package com.inferno.labdimension.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lightweight tab-complete/autocomplete dropdown for a plain EditBox. Vanilla's
 * CommandSuggestions isn't usable here: it's hardcoded to either full Brigadier
 * command parsing or the chat connection's fixed custom-suggestions collection,
 * neither of which fits an arbitrary candidate list. This is a self-contained
 * substitute driven by a caller-supplied candidate supplier.
 *
 * Not a normal child widget -- the owning Screen must call keyPressed/
 * mouseClicked BEFORE forwarding those events to its own widgets/super, and
 * must call renderOverlay AFTER everything else has rendered so the dropdown
 * draws on top regardless of what's underneath (same reasoning ChatScreen uses
 * for its own CommandSuggestions instance).
 */
public final class SuggestionDropdown {
    private static final int MAX_VISIBLE = 8;
    private static final int ROW_HEIGHT = 12;

    private final EditBox box;
    private final Supplier<Collection<String>> candidateSource;
    private List<String> matches = List.of();
    private int highlighted = 0;
    private boolean dismissed = false;

    public SuggestionDropdown(EditBox box, Supplier<Collection<String>> candidateSource) {
        this.box = box;
        this.candidateSource = candidateSource;
        box.setResponder(s -> refresh());
    }

    private void refresh() {
        dismissed = false;
        String text = box.getValue();
        if (text.isEmpty()) {
            matches = List.of();
            return;
        }
        String needle = text.toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String candidate : candidateSource.get()) {
            String lower = candidate.toLowerCase(java.util.Locale.ROOT);
            // For "namespace:path" candidates (block ids), match on the full id
            // OR on just the path after the colon, so typing "stone" finds
            // "minecraft:stone" without requiring the "minecraft:" prefix.
            int colon = lower.indexOf(':');
            String pathOnly = colon >= 0 ? lower.substring(colon + 1) : lower;
            boolean isMatch = lower.startsWith(needle) || pathOnly.startsWith(needle);
            if (isMatch && !candidate.equalsIgnoreCase(text)) {
                out.add(candidate);
                if (out.size() >= MAX_VISIBLE) break;
            }
        }
        matches = out;
        highlighted = 0;
    }

    private boolean isShowing() {
        return !dismissed && box.isFocused() && !matches.isEmpty();
    }

    /** Returns true if the key was consumed (caller must not forward it further). */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isShowing()) return false;
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> {
                accept(matches.get(highlighted));
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                highlighted = Math.min(highlighted + 1, matches.size() - 1);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                highlighted = Math.max(highlighted - 1, 0);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> {
                dismissed = true;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Returns true if the click was consumed (caller must not forward it further). */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isShowing()) return false;
        int x = box.getX();
        int y = box.getY() + box.getHeight();
        for (int i = 0; i < matches.size(); i++) {
            int rowTop = y + i * ROW_HEIGHT;
            if (mouseX >= x && mouseX <= x + box.getWidth() && mouseY >= rowTop && mouseY <= rowTop + ROW_HEIGHT) {
                accept(matches.get(i));
                return true;
            }
        }
        return false;
    }

    private void accept(String value) {
        box.setValue(value);
        box.setCursorPosition(value.length());
        matches = List.of();
    }

    public void renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!isShowing()) return;
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);

        int x = box.getX();
        int y = box.getY() + box.getHeight();
        int width = box.getWidth();
        int height = matches.size() * ROW_HEIGHT;
        guiGraphics.fill(x, y, x + width, y + height, 0xDD1E1E1E);

        var font = Minecraft.getInstance().font;
        for (int i = 0; i < matches.size(); i++) {
            int rowTop = y + i * ROW_HEIGHT;
            boolean hoveredRow = mouseX >= x && mouseX <= x + width && mouseY >= rowTop && mouseY <= rowTop + ROW_HEIGHT;
            if (i == highlighted || hoveredRow) {
                guiGraphics.fill(x, rowTop, x + width, rowTop + ROW_HEIGHT, 0x552277FF);
            }
            guiGraphics.drawString(font, matches.get(i), x + 3, rowTop + 2, 0xFFFFFF, false);
        }

        pose.popPose();
    }
}
