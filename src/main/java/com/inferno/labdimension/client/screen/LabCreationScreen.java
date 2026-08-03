package com.inferno.labdimension.client.screen;

import com.inferno.labdimension.dimension.LabSettings;
import com.inferno.labdimension.network.C2SCreateLab;
import com.inferno.labdimension.network.LabGenSpec;
import com.inferno.labdimension.network.LabRuntimeSpec;
import com.inferno.labdimension.network.LayerSpec;
import com.inferno.labdimension.network.S2COpenLabCreation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class LabCreationScreen extends Screen {
    private final S2COpenLabCreation data;

    private CycleButton<LabSettings.WorldType> worldTypeButton;

    // superflat panel
    private LayerListWidget layerList;
    private Button addLayerButton;
    private Button resetLayersButton;
    private Button setSeaLevelButton;

    // void panel
    private EditBox platformRadiusBox;
    private EditBox platformBlockBox;
    private SuggestionDropdown platformBlockSuggestions;

    // runtime settings (shared across both world types)
    private EditBox nameBox;
    private CycleButton<LabSettings.Visibility> visibilityButton;
    private CycleButton<LabSettings.GuestPermission> guestsButton;
    private PlayerListWidget whitelist;
    private PlayerListWidget blacklist;
    private EditBox whitelistAddBox;
    private EditBox blacklistAddBox;
    private SuggestionDropdown whitelistAddSuggestions;
    private SuggestionDropdown blacklistAddSuggestions;
    private Button whitelistAddButton;
    private Button blacklistAddButton;

    private Button createButton;
    private boolean confirmed;

    // layout anchors, computed in init(), reused in render() for labels
    private int panelTop;
    private int surfaceYRowY;
    private int columnHeaderRowY;
    private int settingsTop;
    private int addBoxTop;

    public LabCreationScreen(S2COpenLabCreation data) {
        super(Component.literal(data.replacingExisting() ? "Replace Your Lab" : "Create Your Lab"));
        this.data = data;
        this.confirmed = !data.replacingExisting();
    }

    private static List<String> onlinePlayerNames() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        return connection.getOnlinePlayers().stream()
                .map(p -> p.getProfile().getName())
                .collect(Collectors.toList());
    }

    private static List<String> blockIds() {
        return BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).collect(Collectors.toList());
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = 24;

        this.worldTypeButton = CycleButton.<LabSettings.WorldType>builder(v -> Component.literal(v.name()))
                .withValues(List.of(LabSettings.WorldType.values()))
                .withInitialValue(data.defaults().worldType())
                .create(centerX - 150, top, 300, 20, Component.literal("World Type"), (btn, val) -> updatePanelVisibility());
        this.addRenderableWidget(this.worldTypeButton);

        this.panelTop = top + 28;

        // --- superflat panel: buttons row, surface-Y row, column-header row, list ---
        this.addLayerButton = Button.builder(Component.literal("+ Layer"), b -> layerList.addLayer("minecraft:stone", 1))
                .pos(centerX - 150, panelTop).size(90, 20).build();
        this.addRenderableWidget(this.addLayerButton);

        this.resetLayersButton = Button.builder(Component.literal("Reset"), b -> layerList.setLayers(defaultLayerSpecs()))
                .pos(centerX - 55, panelTop).size(90, 20).build();
        this.addRenderableWidget(this.resetLayersButton);

        this.setSeaLevelButton = Button.builder(Component.literal("Pad to sea level"), b -> padToSeaLevel())
                .pos(centerX + 40, panelTop).size(110, 20).build();
        this.addRenderableWidget(this.setSeaLevelButton);

        this.surfaceYRowY = panelTop + 24;
        this.columnHeaderRowY = surfaceYRowY + 12;
        int layerListTop = columnHeaderRowY + 12;
        int layerListHeight = 96;

        this.layerList = new LayerListWidget(this.minecraft, this.width, layerListHeight, layerListTop, 22);
        this.layerList.setLayers(data.defaults().layers().isEmpty()
                ? defaultLayerSpecs() : data.defaults().layers());
        this.addRenderableWidget(this.layerList);

        int genPanelBottom = layerListTop + layerListHeight + 4;

        // --- void panel (mutually exclusive with the superflat panel above) ---
        this.platformRadiusBox = new EditBox(this.font, centerX - 150, panelTop + 12, 145, 20, Component.literal("Platform radius"));
        this.platformRadiusBox.setMaxLength(2);
        this.platformRadiusBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,2}"));
        this.platformRadiusBox.setValue(Integer.toString(Math.max(1, data.defaults().platformRadius())));
        this.addRenderableWidget(this.platformRadiusBox);

        this.platformBlockBox = new EditBox(this.font, centerX + 5, panelTop + 12, 145, 20, Component.literal("Platform block"));
        this.platformBlockBox.setMaxLength(64);
        this.platformBlockBox.setValue(data.defaults().platformBlock().isEmpty()
                ? "minecraft:smooth_stone" : data.defaults().platformBlock());
        this.addRenderableWidget(this.platformBlockBox);
        this.platformBlockSuggestions = new SuggestionDropdown(this.platformBlockBox, LabCreationScreen::blockIds);

        // --- runtime settings section ---
        this.settingsTop = genPanelBottom + 14;

        this.nameBox = new EditBox(this.font, centerX - 150, settingsTop + 12, 300, 20, Component.literal("Lab name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setHint(Component.literal("Lab display name"));
        String defaultName = data.replacingExisting() || !data.runtime().displayName().isEmpty()
                ? data.runtime().displayName()
                : Minecraft.getInstance().getUser().getName() + "'s Lab";
        this.nameBox.setValue(defaultName);
        this.addRenderableWidget(this.nameBox);

        this.visibilityButton = CycleButton.<LabSettings.Visibility>builder(v -> Component.literal(v.name()))
                .withValues(List.of(LabSettings.Visibility.values()))
                .withInitialValue(data.runtime().visibility())
                .create(centerX - 150, settingsTop + 38, 145, 20, Component.literal("Visibility"),
                        (btn, val) -> updateListInteractivity());
        this.addRenderableWidget(this.visibilityButton);

        List<LabSettings.GuestPermission> guestValues = data.worldEditPresent()
                ? List.of(LabSettings.GuestPermission.values())
                : List.of(LabSettings.GuestPermission.NONE, LabSettings.GuestPermission.BUILD);
        LabSettings.GuestPermission initialGuests = guestValues.contains(data.runtime().guestPermission())
                ? data.runtime().guestPermission()
                : LabSettings.GuestPermission.BUILD;
        this.guestsButton = CycleButton.<LabSettings.GuestPermission>builder(v -> Component.literal(v.name()))
                .withValues(guestValues)
                .withInitialValue(initialGuests)
                .create(centerX + 5, settingsTop + 38, 145, 20, Component.literal("Guests"));
        this.addRenderableWidget(this.guestsButton);

        // Extra gap below the visibility/guests row -- the labels drawn just below
        // this point used to sit at the exact bottom edge of that row (zero gap).
        this.addBoxTop = settingsTop + 82;
        int leftX = centerX - 150;
        int rightX = centerX + 5;

        this.whitelistAddBox = new EditBox(this.font, leftX, addBoxTop, 145, 20, Component.literal("Whitelist player name"));
        this.whitelistAddBox.setHint(Component.literal("Player name..."));
        this.addRenderableWidget(this.whitelistAddBox);
        this.whitelistAddSuggestions = new SuggestionDropdown(this.whitelistAddBox, LabCreationScreen::onlinePlayerNames);

        this.blacklistAddBox = new EditBox(this.font, rightX, addBoxTop, 145, 20, Component.literal("Blacklist player name"));
        this.blacklistAddBox.setHint(Component.literal("Player name..."));
        this.addRenderableWidget(this.blacklistAddBox);
        this.blacklistAddSuggestions = new SuggestionDropdown(this.blacklistAddBox, LabCreationScreen::onlinePlayerNames);

        this.whitelistAddButton = Button.builder(Component.literal("Whitelist Player"), b -> {
            String v = whitelistAddBox.getValue().trim();
            if (!v.isEmpty()) {
                whitelist.addName(v);
                blacklist.removeByName(v); // a name can't be on both lists at once
                whitelistAddBox.setValue("");
            }
        }).pos(leftX, addBoxTop + 22).size(145, 20).build();
        this.addRenderableWidget(this.whitelistAddButton);

        this.blacklistAddButton = Button.builder(Component.literal("Blacklist Player"), b -> {
            String v = blacklistAddBox.getValue().trim();
            if (!v.isEmpty()) {
                blacklist.addName(v);
                whitelist.removeByName(v); // a name can't be on both lists at once
                blacklistAddBox.setValue("");
            }
        }).pos(rightX, addBoxTop + 22).size(145, 20).build();
        this.addRenderableWidget(this.blacklistAddButton);

        int listTop = addBoxTop + 46;
        int listHeight = Math.max(40, this.height - listTop - 60);
        int columnWidth = 145; // matches the add box/button width above -- see
                               // PlayerListWidget's class doc for why this must
                               // equal the visual column width, not half the screen.

        this.whitelist = new PlayerListWidget(this.minecraft, columnWidth, listHeight, listTop, 22, name -> whitelist.removeByName(name));
        this.whitelist.setTheme(true);
        this.whitelist.setX(leftX);
        this.whitelist.setNames(data.runtime().whitelistNames());
        this.addRenderableWidget(this.whitelist);

        this.blacklist = new PlayerListWidget(this.minecraft, columnWidth, listHeight, listTop, 22, name -> blacklist.removeByName(name));
        this.blacklist.setTheme(false);
        this.blacklist.setX(rightX);
        this.blacklist.setNames(data.runtime().blacklistNames());
        this.addRenderableWidget(this.blacklist);

        this.createButton = Button.builder(Component.literal(data.replacingExisting() ? "Replace Lab" : "Create Lab"), b -> onCreatePressed())
                .pos(centerX - 154, this.height - 28).size(150, 20).build();
        this.addRenderableWidget(this.createButton);
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .pos(centerX + 4, this.height - 28).size(150, 20).build());

        updatePanelVisibility();
        updateListInteractivity();
    }

    private static List<LayerSpec> defaultLayerSpecs() {
        List<LayerSpec> out = new ArrayList<>();
        for (LabSettings.Layer l : LabSettings.defaultLayers()) {
            out.add(new LayerSpec(l.block().toString(), l.height()));
        }
        return out;
    }

    private void padToSeaLevel() {
        int target = 64;
        int current = layerList.currentSurfaceHeight();
        List<LayerSpec> layers = new ArrayList<>(layerList.currentLayers());
        if (layers.isEmpty()) {
            layerList.setLayers(defaultLayerSpecs());
            return;
        }
        int diff = target - current;
        if (diff == 0) return;
        // Insert/trim a stone layer just above the bottom (index 1, or 0 if only one layer exists).
        int insertIndex = Math.min(1, layers.size());
        if (diff > 0) {
            layers.add(insertIndex, new LayerSpec("minecraft:stone", diff));
        } else {
            int idx = Math.max(0, insertIndex - 1);
            LayerSpec l = layers.get(idx);
            int newHeight = l.height() + diff;
            if (newHeight <= 0) {
                layers.remove(idx);
            } else {
                layers.set(idx, new LayerSpec(l.block(), newHeight));
            }
        }
        layerList.setLayers(layers);
    }

    private void updatePanelVisibility() {
        boolean superflat = worldTypeButton.getValue() == LabSettings.WorldType.SUPERFLAT;
        layerList.visible = superflat;
        addLayerButton.visible = superflat;
        resetLayersButton.visible = superflat;
        setSeaLevelButton.visible = superflat;
        platformRadiusBox.visible = !superflat;
        platformBlockBox.visible = !superflat;
    }

    /** Whichever list doesn't match the current visibility grays out -- whitelist
     *  on PUBLIC, blacklist on PRIVATE. This is a client-GUI-only simplification:
     *  LabAccess.accessFor still checks the blacklist unconditionally server-side
     *  (a ban must keep working even on a private lab), so a grayed list is not
     *  actually inert, just deprioritized in the UI. */
    private void updateListInteractivity() {
        boolean isPrivate = visibilityButton.getValue() == LabSettings.Visibility.PRIVATE;
        whitelist.setInteractive(isPrivate);
        whitelistAddBox.active = isPrivate;
        whitelistAddButton.active = isPrivate;
        blacklist.setInteractive(!isPrivate);
        blacklistAddBox.active = !isPrivate;
        blacklistAddButton.active = !isPrivate;
    }

    private void onCreatePressed() {
        if (data.replacingExisting() && !confirmed) {
            confirmed = true;
            createButton.setMessage(Component.literal("Really replace? This deletes your current lab.").withStyle(ChatFormatting.RED));
            return;
        }

        LabGenSpec gen = new LabGenSpec(
                worldTypeButton.getValue(),
                layerList.currentLayers(),
                parseIntOr(platformRadiusBox.getValue(), 8),
                platformBlockBox.getValue().trim()
        );
        LabRuntimeSpec runtime = new LabRuntimeSpec(
                visibilityButton.getValue(),
                guestsButton.getValue(),
                nameBox.getValue(),
                new ArrayList<>(whitelist.currentNames()),
                new ArrayList<>(blacklist.currentNames())
        );
        PacketDistributor.sendToServer(new C2SCreateLab(data.confirmToken(), gen, runtime));
        this.onClose();
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (whitelistAddSuggestions.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (blacklistAddSuggestions.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (platformBlockSuggestions.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (layerList.suggestionKeyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (whitelistAddSuggestions.mouseClicked(mouseX, mouseY, button)) return true;
        if (blacklistAddSuggestions.mouseClicked(mouseX, mouseY, button)) return true;
        if (platformBlockSuggestions.mouseClicked(mouseX, mouseY, button)) return true;
        if (layerList.suggestionMouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int centerX = this.width / 2;

        if (worldTypeButton.getValue() == LabSettings.WorldType.SUPERFLAT) {
            int surface = data.minY() + layerList.currentSurfaceHeight();
            int color = surface == 64 ? 0x55FF55 : (surface > data.minY() + 384 ? 0xFF5555 : 0xFFFF55);
            guiGraphics.drawString(this.font, "Surface Y: " + surface, centerX - 150, surfaceYRowY, color, false);

            int headerLeft = layerList.getRowLeft();
            guiGraphics.drawString(this.font, "Block", headerLeft + 2, columnHeaderRowY, 0xAAAAAA, false);
            guiGraphics.drawString(this.font, "Height", headerLeft + 156, columnHeaderRowY, 0xAAAAAA, false);
        } else {
            guiGraphics.drawString(this.font, "Platform Radius", centerX - 150, panelTop, 0xAAAAAA, false);
            guiGraphics.drawString(this.font, "Platform Block", centerX + 5, panelTop, 0xAAAAAA, false);
        }

        guiGraphics.drawString(this.font, "Lab Name", centerX - 150, settingsTop, 0xAAAAAA, false);

        guiGraphics.drawString(this.font, "Whitelist", centerX - 150, addBoxTop - 10, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "Blacklist", centerX + 5, addBoxTop - 10, 0xFF8888, false);

        // EditBox doesn't visually dim much on its own when .active = false --
        // an explicit overlay makes "you can't type here right now" obvious.
        if (!whitelistAddBox.active) dimBox(guiGraphics, whitelistAddBox);
        if (!blacklistAddBox.active) dimBox(guiGraphics, blacklistAddBox);

        // Suggestion dropdowns render last, above everything else on the screen.
        whitelistAddSuggestions.renderOverlay(guiGraphics, mouseX, mouseY);
        blacklistAddSuggestions.renderOverlay(guiGraphics, mouseX, mouseY);
        platformBlockSuggestions.renderOverlay(guiGraphics, mouseX, mouseY);
        layerList.renderActiveSuggestion(guiGraphics, mouseX, mouseY);
    }

    private static void dimBox(GuiGraphics guiGraphics, EditBox box) {
        guiGraphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), 0x99000000);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
