package com.donutsmp.rtpmapper.gui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import com.donutsmp.rtpmapper.config.RtpMapperConfig;
import com.donutsmp.rtpmapper.region.RtpRegion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private static final int PANEL = 0xF0161A22;
    private static final int BORDER = 0xFF303846;
    private static final int TEXT = 0xFFE5EAF0;
    private static final int MUTED = 0xFF929DAD;
    private static final int ACCENT = 0xFF57D3FF;
    private static final int ERROR = 0xFFFF6B6B;

    private final Screen parent;
    private final RtpMapperUiModel model;
    private EditBox interval;
    private EditBox threshold;
    private EditBox timeout;
    private EditBox stabilization;
    private EditBox pointSize;
    private EditBox centerStopRadius;
    private EditBox worldBorderMargin;
    private EditBox allowedServers;
    private boolean showHud;
    private boolean autoResume;
    private boolean storeY;
    private boolean showGrid;
    private boolean showRings;
    private boolean stopNearCenter;
    private boolean stopNearWorldBorder;
    private String errorMessage = "";
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean compactLayout;
    private String draftInterval;
    private String draftThreshold;
    private String draftTimeout;
    private String draftStabilization;
    private String draftPointSize;
    private String draftCenterStopRadius;
    private String draftWorldBorderMargin;
    private String draftAllowedServers;
    private List<RtpRegion> draftSelectedRegions;

    public SettingsScreen(Screen parent, RtpMapperUiModel model) {
        super(Component.literal("RTP Mapper Settings"));
        this.parent = parent;
        this.model = model;
    }

    @Override
    protected void init() {
        captureDraftValues();
        MapperSettingsView settings = model.settings();
        if (draftInterval == null) {
            draftInterval = format(settings.intervalSeconds());
            draftThreshold = format(settings.teleportThresholdBlocks());
            draftTimeout = format(settings.teleportTimeoutSeconds());
            draftStabilization = format(settings.stabilizationSeconds());
            draftPointSize = format(settings.pointSize());
            draftCenterStopRadius = format(settings.centerStopRadiusBlocks());
            draftWorldBorderMargin = format(settings.worldBorderMarginBlocks());
            draftAllowedServers = String.join(", ", settings.allowedServers());
            showHud = settings.showHud();
            autoResume = settings.autoResume();
            storeY = settings.storeY();
            showGrid = settings.showGrid();
            showRings = settings.showDistanceRings();
            stopNearCenter = settings.stopNearCenter();
            stopNearWorldBorder = settings.stopNearWorldBorder();
            draftSelectedRegions = settings.selectedRegions();
        }

        panelWidth = Math.min(610, width - 24);
        panelHeight = Math.min(390, height - 24);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        compactLayout = width < 600 || height < 414;
        if (compactLayout) {
            initCompact(settings);
            return;
        }
        int valueX = panelX + 260;
        int valueWidth = panelWidth - 280;
        int firstY = panelY + 45;
        int spacing = 27;

        interval = numericBox(valueX, firstY, valueWidth, draftInterval, "Seconds, 1-60");
        threshold = numericBox(valueX, firstY + spacing, valueWidth, draftThreshold, "Blocks, 32-60,000,000");
        timeout = numericBox(valueX, firstY + spacing * 2, valueWidth, draftTimeout, "Seconds, 5-300");
        stabilization = numericBox(valueX, firstY + spacing * 3, valueWidth, draftStabilization, "Seconds, 0.25-5");
        pointSize = numericBox(valueX, firstY + spacing * 4, valueWidth, draftPointSize, "Pixels, 1-8");
        int guardToggleWidth = Math.min(132, Math.max(92, valueWidth / 3));
        int guardGap = 8;
        int guardFieldX = valueX + guardToggleWidth + guardGap;
        int guardFieldWidth = Math.max(40, valueWidth - guardToggleWidth - guardGap);
        int centerGuardY = firstY + spacing * 5;
        int borderGuardY = firstY + spacing * 6;
        addRenderableWidget(CycleButton.onOffBuilder(stopNearCenter).create(
            valueX, centerGuardY, guardToggleWidth, 20, Component.literal("Center Guard"),
            (button, value) -> stopNearCenter = value
        ));
        centerStopRadius = numericBox(
            guardFieldX, centerGuardY, guardFieldWidth, draftCenterStopRadius,
            "Center radius in blocks"
        );
        addRenderableWidget(CycleButton.onOffBuilder(stopNearWorldBorder).create(
            valueX, borderGuardY, guardToggleWidth, 20, Component.literal("Border Guard"),
            (button, value) -> stopNearWorldBorder = value
        ));
        worldBorderMargin = numericBox(
            guardFieldX, borderGuardY, guardFieldWidth, draftWorldBorderMargin,
            "Square-border margin in blocks"
        );
        allowedServers = new EditBox(font, valueX, firstY + spacing * 7, valueWidth, 20, Component.literal("Allowed server patterns"));
        allowedServers.setMaxLength(512);
        allowedServers.setValue(draftAllowedServers);
        addRenderableWidget(allowedServers);

        int togglesY = firstY + spacing * 8 + 8;
        int toggleGap = 8;
        int toggleWidth = (panelWidth - 40 - toggleGap) / 2;
        int left = panelX + 16;
        int right = left + toggleWidth + toggleGap;
        addRenderableWidget(CycleButton.onOffBuilder(showHud).create(left, togglesY, toggleWidth, 20, Component.literal("Show HUD"), (button, value) -> showHud = value));
        addRenderableWidget(CycleButton.onOffBuilder(autoResume).create(right, togglesY, toggleWidth, 20, Component.literal("Auto Resume"), (button, value) -> autoResume = value));
        addRenderableWidget(CycleButton.onOffBuilder(storeY).create(left, togglesY + 24, toggleWidth, 20, Component.literal("Store Y"), (button, value) -> storeY = value));
        addRenderableWidget(CycleButton.onOffBuilder(showGrid).create(right, togglesY + 24, toggleWidth, 20, Component.literal("Show Grid"), (button, value) -> showGrid = value));
        addRenderableWidget(CycleButton.onOffBuilder(showRings).create(left, togglesY + 48, toggleWidth, 20, Component.literal("Distance Rings"), (button, value) -> showRings = value));
        addRenderableWidget(Button.builder(Component.literal(regionButtonLabel(false)), button -> openRegionSelector())
            .bounds(right, togglesY + 48, toggleWidth, 20).build());

        int buttonY = panelY + panelHeight - 30;
        addRenderableWidget(Button.builder(Component.literal("Baritone Mining…"), button -> openMiningSettings())
            .bounds(panelX + 16, buttonY, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save()).bounds(panelX + panelWidth - 202, buttonY, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(panelX + panelWidth - 106, buttonY, 90, 20).build());
    }

    private void initCompact(MapperSettingsView settings) {
        int innerX = panelX + 12;
        int gap = 8;
        int columnWidth = (panelWidth - 24 - gap) / 2;
        int rightColumn = innerX + columnWidth + gap;
        int fieldOffset = Math.min(62, Math.max(42, columnWidth / 2));
        int fieldWidth = Math.max(34, columnWidth - fieldOffset);
        int contentY = panelY + 25;
        int row = 20;

        interval = numericBox(innerX + fieldOffset, contentY, fieldWidth, draftInterval, "Seconds, 1-60");
        threshold = numericBox(rightColumn + fieldOffset, contentY, fieldWidth, draftThreshold, "Blocks, 32-60,000,000");
        timeout = numericBox(innerX + fieldOffset, contentY + row, fieldWidth, draftTimeout, "Seconds, 5-300");
        stabilization = numericBox(rightColumn + fieldOffset, contentY + row, fieldWidth, draftStabilization, "Seconds, 0.25-5");
        pointSize = numericBox(innerX + fieldOffset, contentY + row * 2, fieldWidth, draftPointSize, "Pixels, 1-8");
        centerStopRadius = numericBox(
            rightColumn + fieldOffset,
            contentY + row * 2,
            fieldWidth,
            draftCenterStopRadius,
            "Center radius in blocks"
        );
        worldBorderMargin = numericBox(
            innerX + fieldOffset,
            contentY + row * 3,
            fieldWidth,
            draftWorldBorderMargin,
            "Square-border margin in blocks"
        );
        allowedServers = new EditBox(
            font,
            innerX + fieldOffset,
            contentY + row * 4,
            Math.max(34, panelWidth - 24 - fieldOffset),
            20,
            Component.literal("Allowed server patterns")
        );
        allowedServers.setMaxLength(512);
        allowedServers.setValue(draftAllowedServers);
        addRenderableWidget(allowedServers);

        int togglesY = contentY + row * 5 + 2;
        int toggleGap = 4;
        int toggleWidth = (panelWidth - 24 - toggleGap * 2) / 3;
        int first = innerX;
        int second = first + toggleWidth + toggleGap;
        int third = second + toggleWidth + toggleGap;
        addRenderableWidget(CycleButton.onOffBuilder(showHud).create(first, togglesY, toggleWidth, 20, Component.literal("HUD"), (button, value) -> showHud = value));
        addRenderableWidget(CycleButton.onOffBuilder(autoResume).create(second, togglesY, toggleWidth, 20, Component.literal("Resume"), (button, value) -> autoResume = value));
        addRenderableWidget(CycleButton.onOffBuilder(storeY).create(third, togglesY, toggleWidth, 20, Component.literal("Store Y"), (button, value) -> storeY = value));
        addRenderableWidget(CycleButton.onOffBuilder(showGrid).create(first, togglesY + 20, toggleWidth, 20, Component.literal("Grid"), (button, value) -> showGrid = value));
        addRenderableWidget(CycleButton.onOffBuilder(showRings).create(second, togglesY + 20, toggleWidth, 20, Component.literal("Rings"), (button, value) -> showRings = value));
        addRenderableWidget(Button.builder(Component.literal(regionButtonLabel(true)), button -> openRegionSelector())
            .bounds(third, togglesY + 20, toggleWidth, 20).build());
        addRenderableWidget(CycleButton.onOffBuilder(stopNearCenter).create(
            first, togglesY + 40, toggleWidth, 20, Component.literal("Center Guard"),
            (button, value) -> stopNearCenter = value
        ));
        addRenderableWidget(CycleButton.onOffBuilder(stopNearWorldBorder).create(
            second, togglesY + 40, toggleWidth, 20, Component.literal("Border Guard"),
            (button, value) -> stopNearWorldBorder = value
        ));

        int buttonY = panelY + panelHeight - 28;
        addRenderableWidget(Button.builder(Component.literal("Mining…"), button -> openMiningSettings())
            .bounds(panelX + 12, buttonY, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save()).bounds(panelX + panelWidth - 158, buttonY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(panelX + panelWidth - 82, buttonY, 70, 20).build());
    }

    private EditBox numericBox(int x, int y, int width, String value, String hint) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setMaxLength(32);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void save() {
        try {
            double intervalValue = parseFinite(interval.getValue(), "RTP interval");
            double thresholdValue = parseFinite(threshold.getValue(), "Teleport threshold");
            double timeoutValue = parseFinite(timeout.getValue(), "Teleport timeout");
            double stabilizationValue = parseFinite(stabilization.getValue(), "Stabilization time");
            double pointSizeValue = parseFinite(pointSize.getValue(), "Point size");
            double centerStopRadiusValue = parseFinite(centerStopRadius.getValue(), "Center stop radius");
            double worldBorderMarginValue = parseFinite(worldBorderMargin.getValue(), "World border margin");
            requireRange("RTP interval", intervalValue,
                RtpMapperConfig.MIN_RTP_INTERVAL_SECONDS, RtpMapperConfig.MAX_RTP_INTERVAL_SECONDS);
            requireRange("Teleport threshold", thresholdValue,
                RtpMapperConfig.MIN_TELEPORT_THRESHOLD_BLOCKS, RtpMapperConfig.MAX_TELEPORT_THRESHOLD_BLOCKS);
            requireRange("Teleport timeout", timeoutValue,
                RtpMapperConfig.MIN_TELEPORT_TIMEOUT_SECONDS, RtpMapperConfig.MAX_TELEPORT_TIMEOUT_SECONDS);
            requireRange("Stabilization time", stabilizationValue,
                RtpMapperConfig.MIN_STABILIZATION_SECONDS, RtpMapperConfig.MAX_STABILIZATION_SECONDS);
            if (pointSizeValue < RtpMapperConfig.MIN_POINT_SIZE || pointSizeValue > RtpMapperConfig.MAX_POINT_SIZE) {
                throw new IllegalArgumentException("Point size must be from 1 to 8.");
            }
            requireRange("Center stop radius", centerStopRadiusValue,
                RtpMapperConfig.MIN_CENTER_STOP_RADIUS_BLOCKS,
                RtpMapperConfig.MAX_CENTER_STOP_RADIUS_BLOCKS);
            requireRange("World border margin", worldBorderMarginValue,
                RtpMapperConfig.MIN_WORLD_BORDER_MARGIN_BLOCKS,
                RtpMapperConfig.MAX_WORLD_BORDER_MARGIN_BLOCKS);
            List<String> servers = Arrays.stream(allowedServers.getValue().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
            if (servers.isEmpty()) {
                throw new IllegalArgumentException("At least one allowed server is required.");
            }

            MapperSettingsView updated = new MapperSettingsView(
                intervalValue,
                thresholdValue,
                timeoutValue,
                stabilizationValue,
                showHud,
                autoResume,
                storeY,
                showGrid,
                showRings,
                pointSizeValue,
                servers,
                draftSelectedRegions,
                stopNearCenter,
                centerStopRadiusValue,
                stopNearWorldBorder,
                worldBorderMarginValue
            );
            UiActionResult result = model.applySettings(updated);
            if (!result.success()) {
                errorMessage = result.message();
                return;
            }
            minecraft.setScreen(parent);
        } catch (IllegalArgumentException exception) {
            errorMessage = exception.getMessage();
        }
    }

    private static double parseFinite(String value, String label) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(label + " must be finite.");
        }
        return parsed;
    }

    private static void requireRange(String label, double value, double minimum, double maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(String.format(
                Locale.ROOT,
                "%s must be from %s to %s.",
                label,
                format(minimum),
                format(maximum)
            ));
        }
    }

    private void captureDraftValues() {
        if (interval == null) {
            return;
        }
        draftInterval = interval.getValue();
        draftThreshold = threshold.getValue();
        draftTimeout = timeout.getValue();
        draftStabilization = stabilization.getValue();
        draftPointSize = pointSize.getValue();
        draftCenterStopRadius = centerStopRadius.getValue();
        draftWorldBorderMargin = worldBorderMargin.getValue();
        draftAllowedServers = allowedServers.getValue();
    }

    private void openRegionSelector() {
        captureDraftValues();
        minecraft.setScreen(new RegionSelectionScreen(
            this,
            draftSelectedRegions,
            regions -> draftSelectedRegions = regions
        ));
    }

    private void openMiningSettings() {
        captureDraftValues();
        minecraft.setScreen(new MiningSettingsScreen(this, model));
    }

    private String regionButtonLabel(boolean compact) {
        int selected = draftSelectedRegions == null ? 0 : draftSelectedRegions.size();
        int total = RtpRegion.selectableValues().size();
        if (compact) {
            return "Regions " + selected + "/" + total;
        }
        return selected == total ? "RTP Regions: All " + total : "RTP Regions: " + selected + "/" + total;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF0080A0F);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, BORDER);
        if (compactLayout && !errorMessage.isEmpty()) {
            graphics.drawString(
                font,
                fitText(errorMessage, Math.max(8, panelWidth - 24)),
                panelX + 12,
                panelY + 10,
                ERROR,
                false
            );
        } else {
            graphics.drawString(font, title, panelX + 16, panelY + 14, ACCENT, false);
        }
        if (compactLayout) {
            renderCompactLabels(graphics);
        } else {
            graphics.drawString(font, "Guards apply to the next RTP and save its landing first.", panelX + 175, panelY + 14, MUTED, false);
            int firstY = panelY + 51;
            int spacing = 27;
            label(graphics, "RTP interval", firstY);
            label(graphics, "Teleport detection threshold", firstY + spacing);
            label(graphics, "Teleport timeout", firstY + spacing * 2);
            label(graphics, "Stabilization time", firstY + spacing * 3);
            label(graphics, "Point size", firstY + spacing * 4);
            label(graphics, "Center stop radius (Overworld)", firstY + spacing * 5);
            label(graphics, "Border margin at ±225k", firstY + spacing * 6);
            label(graphics, "Allowed servers", firstY + spacing * 7);
        }

        if (!compactLayout && !errorMessage.isEmpty()) {
            graphics.drawString(
                font,
                fitText(errorMessage, Math.max(8, panelWidth - 32)),
                panelX + 16,
                panelY + panelHeight - 45,
                ERROR,
                false
            );
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void label(GuiGraphics graphics, String label, int y) {
        graphics.drawString(font, label, panelX + 16, y, TEXT, false);
    }

    private void renderCompactLabels(GuiGraphics graphics) {
        int innerX = panelX + 12;
        int gap = 8;
        int columnWidth = (panelWidth - 24 - gap) / 2;
        int rightColumn = innerX + columnWidth + gap;
        int contentY = panelY + 31;
        int row = 20;
        graphics.drawString(font, "Interval", innerX, contentY, TEXT, false);
        graphics.drawString(font, "Threshold", rightColumn, contentY, TEXT, false);
        graphics.drawString(font, "Timeout", innerX, contentY + row, TEXT, false);
        graphics.drawString(font, "Stabilize", rightColumn, contentY + row, TEXT, false);
        graphics.drawString(font, "Point px", innerX, contentY + row * 2, TEXT, false);
        graphics.drawString(font, "Center r", rightColumn, contentY + row * 2, TEXT, false);
        graphics.drawString(font, "Border m", innerX, contentY + row * 3, TEXT, false);
        graphics.drawString(font, "Servers", innerX, contentY + row * 4, TEXT, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String fitText(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) {
            return value;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(1, maximumWidth - font.width(ellipsis))) + ellipsis;
    }
}
