package com.inferno.labdimension.client.screen;

import com.inferno.labdimension.dimension.LabSettings;
import com.inferno.labdimension.network.C2SUpdateLabSettings;
import com.inferno.labdimension.network.LabRuntimeSpec;
import com.inferno.labdimension.network.S2COpenLabSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class LabSettingsScreen extends Screen {
    private final S2COpenLabSettings data;

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

    private int addBoxTop;

    public LabSettingsScreen(S2COpenLabSettings data) {
        super(Component.literal(data.ownerName() + "'s Lab Settings"));
        this.data = data;
    }

    private static List<String> onlinePlayerNames() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        return connection.getOnlinePlayers().stream()
                .map(p -> p.getProfile().getName())
                .collect(Collectors.toList());
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = 24;

        this.nameBox = new EditBox(this.font, centerX - 150, top + 12, 300, 20, Component.literal("Lab name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setHint(Component.literal("Lab display name"));
        this.nameBox.setValue(data.runtime().displayName());
        this.addRenderableWidget(this.nameBox);

        this.visibilityButton = CycleButton.<LabSettings.Visibility>builder(v -> Component.literal(v.name()))
                .withValues(List.of(LabSettings.Visibility.values()))
                .withInitialValue(data.runtime().visibility())
                .create(centerX - 150, top + 38, 145, 20, Component.literal("Visibility"),
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
                .create(centerX + 5, top + 38, 145, 20, Component.literal("Guests"));
        this.addRenderableWidget(this.guestsButton);

        // Extra gap below the visibility/guests row -- the labels drawn just below
        // this point used to sit at the exact bottom edge of that row (zero gap).
        this.addBoxTop = top + 82;
        int leftX = centerX - 150;
        int rightX = centerX + 5;

        this.whitelistAddBox = new EditBox(this.font, leftX, addBoxTop, 145, 20, Component.literal("Whitelist player name"));
        this.whitelistAddBox.setHint(Component.literal("Player name..."));
        this.addRenderableWidget(this.whitelistAddBox);
        this.whitelistAddSuggestions = new SuggestionDropdown(this.whitelistAddBox, LabSettingsScreen::onlinePlayerNames);

        this.blacklistAddBox = new EditBox(this.font, rightX, addBoxTop, 145, 20, Component.literal("Blacklist player name"));
        this.blacklistAddBox.setHint(Component.literal("Player name..."));
        this.addRenderableWidget(this.blacklistAddBox);
        this.blacklistAddSuggestions = new SuggestionDropdown(this.blacklistAddBox, LabSettingsScreen::onlinePlayerNames);

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

        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .pos(centerX - 154, this.height - 28).size(150, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .pos(centerX + 4, this.height - 28).size(150, 20).build());

        updateListInteractivity();
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

    private void save() {
        LabRuntimeSpec runtime = new LabRuntimeSpec(
                visibilityButton.getValue(),
                guestsButton.getValue(),
                nameBox.getValue(),
                new ArrayList<>(whitelist.currentNames()),
                new ArrayList<>(blacklist.currentNames())
        );
        PacketDistributor.sendToServer(new C2SUpdateLabSettings(runtime));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (whitelistAddSuggestions.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (blacklistAddSuggestions.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (whitelistAddSuggestions.mouseClicked(mouseX, mouseY, button)) return true;
        if (blacklistAddSuggestions.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int centerX = this.width / 2;
        guiGraphics.drawString(this.font, "Lab Name", centerX - 150, 24, 0xAAAAAA, false);

        guiGraphics.drawString(this.font, "Whitelist", centerX - 150, addBoxTop - 10, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "Blacklist", centerX + 5, addBoxTop - 10, 0xFF8888, false);

        // EditBox doesn't visually dim much on its own when .active = false --
        // an explicit overlay makes "you can't type here right now" obvious.
        if (!whitelistAddBox.active) dimBox(guiGraphics, whitelistAddBox);
        if (!blacklistAddBox.active) dimBox(guiGraphics, blacklistAddBox);

        whitelistAddSuggestions.renderOverlay(guiGraphics, mouseX, mouseY);
        blacklistAddSuggestions.renderOverlay(guiGraphics, mouseX, mouseY);
    }

    private static void dimBox(GuiGraphics guiGraphics, EditBox box) {
        guiGraphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), 0x99000000);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
