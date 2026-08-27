package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.region.RtpRegion;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Draft-only multi-select screen for the six public DonutSMP RTP regions. */
public final class RegionSelectionScreen extends Screen {
    private static final int PANEL = 0xF0161A22;
    private static final int BORDER = 0xFF303846;
    private static final int TEXT = 0xFFE5EAF0;
    private static final int MUTED = 0xFF929DAD;
    private static final int ACCENT = 0xFF57D3FF;
    private static final int ERROR = 0xFFFF6B6B;

    private final Screen parent;
    private final Consumer<List<RtpRegion>> onDone;
    private final EnumSet<RtpRegion> workingSelection = EnumSet.noneOf(RtpRegion.class);
    private final Map<RtpRegion, Button> regionButtons = new EnumMap<>(RtpRegion.class);
    private Button doneButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public RegionSelectionScreen(
        Screen parent,
        List<RtpRegion> initialSelection,
        Consumer<List<RtpRegion>> onDone
    ) {
        super(Component.literal("RTP Regions"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.onDone = Objects.requireNonNull(onDone, "onDone");
        workingSelection.addAll(RtpRegion.normalizeSelection(initialSelection));
    }

    @Override
    protected void init() {
        regionButtons.clear();
        panelWidth = Math.min(340, Math.max(1, width - 24));
        panelHeight = Math.min(204, Math.max(1, height - 24));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int innerX = panelX + 12;
        int gap = 6;
        int columnWidth = Math.max(1, (panelWidth - 24 - gap) / 2);
        int firstY = panelY + 48;
        List<RtpRegion> regions = RtpRegion.selectableValues();
        for (int index = 0; index < regions.size(); index++) {
            RtpRegion region = regions.get(index);
            int column = index % 2;
            int row = index / 2;
            int x = innerX + column * (columnWidth + gap);
            int y = firstY + row * 24;
            Button button = addRenderableWidget(Button.builder(
                Component.empty(),
                ignored -> toggle(region)
            ).bounds(x, y, columnWidth, 20).build());
            regionButtons.put(region, button);
        }

        int buttonY = panelY + panelHeight - 28;
        int bulkY = buttonY - 48;
        addRenderableWidget(Button.builder(Component.literal("Select All"), ignored -> selectAll())
            .bounds(innerX, bulkY, columnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Select None"), ignored -> selectNone())
            .bounds(innerX + columnWidth + gap, bulkY, columnWidth, 20).build());

        int actionWidth = Math.min(90, Math.max(1, (panelWidth - 30) / 2));
        int actionGap = 6;
        int actionsWidth = actionWidth * 2 + actionGap;
        int actionsX = panelX + (panelWidth - actionsWidth) / 2;
        doneButton = addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> applyDraft())
            .bounds(actionsX, buttonY, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
            .bounds(actionsX + actionWidth + actionGap, buttonY, actionWidth, 20).build());
        refreshButtons();
    }

    private void toggle(RtpRegion region) {
        if (!workingSelection.remove(region)) {
            workingSelection.add(region);
        }
        refreshButtons();
    }

    private void selectAll() {
        workingSelection.clear();
        workingSelection.addAll(RtpRegion.selectableValues());
        refreshButtons();
    }

    private void selectNone() {
        workingSelection.clear();
        refreshButtons();
    }

    private void refreshButtons() {
        for (RtpRegion region : RtpRegion.selectableValues()) {
            Button button = regionButtons.get(region);
            if (button != null) {
                button.setMessage(Component.literal(
                    region.shortName() + ": " + (workingSelection.contains(region) ? "ON" : "OFF")
                ));
            }
        }
        if (doneButton != null) {
            doneButton.active = !workingSelection.isEmpty();
        }
    }

    private void applyDraft() {
        if (workingSelection.isEmpty()) {
            return;
        }
        onDone.accept(RtpRegion.normalizeSelection(workingSelection));
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF0080A0F);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, BORDER);
        graphics.drawString(font, title, panelX + 12, panelY + 12, ACCENT, false);
        graphics.drawString(
            font,
            fitText("Selected regions are sampled in round-robin order.", panelWidth - 24),
            panelX + 12,
            panelY + 29,
            MUTED,
            false
        );
        String count = workingSelection.size() + " of " + RtpRegion.selectableValues().size() + " selected";
        graphics.drawString(
            font,
            count,
            panelX + panelWidth - 12 - font.width(count),
            panelY + 12,
            TEXT,
            false
        );
        if (workingSelection.isEmpty()) {
            graphics.drawString(
                font,
                "Select at least one region.",
                panelX + 12,
                panelY + panelHeight - 42,
                ERROR,
                false
            );
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private String fitText(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) {
            return value;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(1, maximumWidth - font.width(ellipsis))) + ellipsis;
    }
}
