package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.util.CoordinateTransform;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Main dark-themed status, statistics, and interactive scatter-plot screen. */
public final class RtpMapperScreen extends Screen {
    private static final int BACKGROUND = 0xF0080A0F;
    private static final int PANEL = 0xE8171B24;
    private static final int BORDER = 0xFF303846;
    private static final int TEXT = 0xFFE5EAF0;
    private static final int MUTED = 0xFF8D99AA;
    private static final int ACCENT = 0xFF57D3FF;
    private static final int SUCCESS = 0xFF6EE7A8;
    private static final int ERROR = 0xFFFF7474;

    private final RtpMapperUiModel model;
    private final ChartRenderer chartRenderer = new ChartRenderer();
    private final CoordinateTransform transform = new CoordinateTransform();
    private final StatisticsPanel statisticsPanel = new StatisticsPanel();
    private DataScope scope = DataScope.SESSION;
    private Button toggleButton;
    private Button scopeButton;
    private boolean draggingChart;
    private boolean followData = true;
    private long fittedRevision = Long.MIN_VALUE;
    private String actionMessage = "";
    private boolean actionSucceeded = true;
    private long actionMessageExpiresAt;
    private int chartX;
    private int chartY;
    private int chartWidth;
    private int chartHeight;
    private int sideWidth;
    private int statsPanelX;
    private int statsPanelY;
    private int statsPanelWidth;
    private int statsPanelHeight;
    private int statisticsBucketScroll;
    private boolean initialized;

    public RtpMapperScreen(RtpMapperUiModel model) {
        super(Component.literal("DonutSMP RTP Mapper"));
        this.model = model;
    }

    @Override
    protected void init() {
        int previousChartWidth = chartWidth;
        int previousChartHeight = chartHeight;
        int margin = 12;
        int gap = 6;
        int buttonY = 34;
        int buttonHeight = 20;
        boolean compactControls = width < 500;
        boolean secondControlRow = compactControls || width < 620;
        int x = margin;

        toggleButton = addRenderableWidget(Button.builder(Component.literal("Start Mapping"), button -> toggleMapping())
            .bounds(x, buttonY, 100, buttonHeight).build());
        x += 100 + gap;
        addRenderableWidget(Button.builder(Component.literal("Clear Data"), button -> confirmClear())
            .bounds(x, buttonY, 86, buttonHeight).build());
        x += 86 + gap;
        addRenderableWidget(Button.builder(Component.literal("Export CSV"), button -> showResult(model.exportCsv()))
            .bounds(x, buttonY, 86, buttonHeight).build());
        x += 86 + gap;
        if (compactControls) {
            x = margin;
            buttonY = 58;
        }
        addRenderableWidget(Button.builder(Component.literal("Reset View"), button -> resetView())
            .bounds(x, buttonY, 86, buttonHeight).build());
        x += 86 + gap;
        addRenderableWidget(Button.builder(Component.literal("Settings"), button -> minecraft.setScreen(new SettingsScreen(this, model)))
            .bounds(x, buttonY, 76, buttonHeight).build());
        x += 76 + gap;

        int scopeX = secondControlRow && !compactControls ? margin : (compactControls ? x : width - margin - 112);
        int scopeY = secondControlRow && !compactControls ? 58 : buttonY;
        scopeButton = addRenderableWidget(Button.builder(Component.literal("View: " + scope.label()), button -> switchScope())
            .bounds(scopeX, scopeY, 112, buttonHeight).build());

        sideWidth = Math.clamp((int)(width * 0.28), 180, 280);
        chartX = margin + sideWidth + 10;
        chartY = secondControlRow ? 100 : 76;
        chartWidth = Math.max(1, width - chartX - margin);
        chartHeight = Math.max(1, height - chartY - margin);
        transform.setBounds(chartX, chartY, chartWidth, chartHeight);
        boolean chartSizeChanged = previousChartWidth != chartWidth || previousChartHeight != chartHeight;
        if (!initialized || (followData && chartSizeChanged)) {
            fitData(model.points(scope));
        }
        chartRenderer.invalidate();
        initialized = true;
    }

    private void toggleMapping() {
        MapperStatusView status = model.status();
        showResult(status.running() ? model.stopMapping() : model.startMapping());
    }

    private void confirmClear() {
        int count = model.status().allTimeSamples();
        minecraft.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    UiActionResult result = model.clearAllData();
                    minecraft.setScreen(this);
                    showResult(result);
                    resetView();
                } else {
                    minecraft.setScreen(this);
                }
            },
            Component.literal("Delete all " + formatInteger(count) + " RTP samples?"),
            Component.literal("This permanently clears session and all-time mapper data."),
            Component.literal("Delete"),
            Component.literal("Cancel")
        ));
    }

    private void switchScope() {
        scope = scope.other();
        scopeButton.setMessage(Component.literal("View: " + scope.label()));
        followData = true;
        fittedRevision = Long.MIN_VALUE;
        statisticsBucketScroll = 0;
        chartRenderer.invalidate();
    }

    private void resetView() {
        followData = true;
        fittedRevision = Long.MIN_VALUE;
        fitData(model.points(scope));
        chartRenderer.invalidate();
    }

    private void fitData(ChartPointProvider points) {
        if (points.size() == 0) {
            transform.fitToBounds(-1_000, 1_000, -1_000, 1_000, 0.08);
            fittedRevision = points.revision();
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < points.size(); index++) {
            double x = points.xAt(index);
            double z = points.zAt(index);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        transform.fitToBounds(minX, maxX, minZ, maxZ, 0.08);
        fittedRevision = points.revision();
    }

    private void showResult(UiActionResult result) {
        actionSucceeded = result.success();
        actionMessage = result.message();
        actionMessageExpiresAt = System.currentTimeMillis() + 6_000L;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, BACKGROUND);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!actionMessage.isEmpty() && System.currentTimeMillis() >= actionMessageExpiresAt) {
            actionMessage = "";
        }
        MapperStatusView status = model.status();
        MapperSettingsView settings = model.settings();
        ChartPointProvider points = model.points(scope);
        if (followData && points.revision() != fittedRevision) {
            fitData(points);
        }

        toggleButton.setMessage(Component.literal(status.running() ? "Stop Mapping" : "Start Mapping"));
        graphics.drawString(font, title, 12, 13, ACCENT, false);
        graphics.drawString(
            font,
            fitText(
                status.running() ? "RUNNING — " + friendlyState(status.state()) : "STOPPED",
                Math.max(8, width - 197)
            ),
            185,
            13,
            status.running() ? SUCCESS : MUTED,
            false
        );

        int sideX = 12;
        int statusY = chartY;
        int statusHeight = 128;
        graphics.fill(sideX, statusY, sideX + sideWidth, statusY + statusHeight, PANEL);
        graphics.renderOutline(sideX, statusY, sideWidth, statusHeight, BORDER);
        drawStatusPanel(graphics, status, sideX, statusY);

        statsPanelX = sideX;
        statsPanelY = statusY + statusHeight + 8;
        statsPanelWidth = sideWidth;
        statsPanelHeight = Math.max(0, height - statsPanelY - 12);
        if (statsPanelHeight >= 36) {
            MapperStatisticsView statistics = model.statistics(scope);
            statisticsBucketScroll = Math.min(
                statisticsBucketScroll,
                statisticsPanel.maximumBucketScroll(statistics, statsPanelHeight)
            );
            statisticsPanel.render(
                graphics,
                font,
                statistics,
                statsPanelX,
                statsPanelY,
                statsPanelWidth,
                statsPanelHeight,
                statisticsBucketScroll
            );
        } else {
            statsPanelHeight = 0;
        }

        graphics.drawString(
            font,
            fitText(
                "Random Teleports on DonutSMP — " + scope.label() + " (" + formatInteger(points.size()) + ")",
                Math.max(8, chartWidth)
            ),
            chartX,
            chartY - 13,
            TEXT,
            false
        );
        chartRenderer.render(
            graphics,
            font,
            points,
            transform,
            chartX,
            chartY,
            chartWidth,
            chartHeight,
            mouseX,
            mouseY,
            settings.pointSize(),
            settings.showGrid(),
            settings.showDistanceRings()
        );

        if (!actionMessage.isEmpty()) {
            int maximumTextWidth = Math.max(8, chartWidth - 22);
            String displayedMessage = fitText(actionMessage, maximumTextWidth);
            int messageWidth = font.width(displayedMessage) + 12;
            int messageX = chartX + chartWidth - messageWidth - 5;
            int messageY = chartY + 5;
            graphics.fill(messageX, messageY, messageX + messageWidth, messageY + 17, 0xE01A202A);
            graphics.drawString(font, displayedMessage, messageX + 6, messageY + 5, actionSucceeded ? SUCCESS : ERROR, false);
        } else if (!status.detail().isEmpty()) {
            graphics.drawString(font, fitText(status.detail(), Math.max(8, chartWidth - 16)), chartX + 8, chartY + chartHeight - 13, MUTED, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawStatusPanel(GuiGraphics graphics, MapperStatusView status, int x, int y) {
        int lineY = y + 8;
        graphics.drawString(font, "MAPPER STATUS", x + 8, lineY, ACCENT, false);
        lineY += 15;
        statusLine(graphics, x, lineY, "Samples", formatInteger(status.sessionSamples()) + " session / " + formatInteger(status.allTimeSamples()) + " total");
        lineY += 13;
        String current = status.hasCurrentPosition()
            ? rounded(status.currentX()) + " / " + rounded(status.currentY()) + " / " + rounded(status.currentZ())
            : "Unavailable";
        statusLine(graphics, x, lineY, "Current X/Y/Z", current);
        lineY += 13;
        String last = status.hasLastSample()
            ? rounded(status.lastX()) + " / " + rounded(status.lastZ())
            : "No samples";
        String lastLabel = status.hasLastSample()
            ? "Last [" + status.lastRequestedRegion().shortName() + "]"
            : "Last RTP X/Z";
        statusLine(graphics, x, lineY, lastLabel, last);
        lineY += 13;
        String next = status.secondsUntilNextAction() >= 0
            ? String.format(Locale.ROOT, "%.1fs", status.secondsUntilNextAction())
            : friendlyState(status.state());
        statusLine(graphics, x, lineY, "Next RTP", next);
        lineY += 13;
        statusLine(
            graphics,
            x,
            lineY,
            "Target region",
            status.targetRegion().shortName() + " · " + status.selectedRegionCount()
        );
        lineY += 13;
        statusLine(graphics, x, lineY, "Session", formatDuration(status.sessionDurationMillis()));
        lineY += 13;
        statusLine(graphics, x, lineY, "Failed attempts", Integer.toString(status.failedAttempts()));
        lineY += 13;
        graphics.drawString(
            font,
            fitText(
                status.serverAllowed() ? "DonutSMP server accepted" : "Mapper disabled on this server",
                Math.max(8, sideWidth - 16)
            ),
            x + 8,
            lineY,
            status.serverAllowed() ? SUCCESS : ERROR,
            false
        );
    }

    private void statusLine(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(font, label, x + 8, y, MUTED, false);
        graphics.drawString(font, fitText(value, Math.max(8, sideWidth - 101)), x + 93, y, TEXT, false);
    }

    private String fitText(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) {
            return value;
        }
        String ellipsis = "…";
        int available = Math.max(1, maximumWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(value, available) + ellipsis;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0 && transform.contains(event.x(), event.y())) {
            draggingChart = true;
            setDragging(true);
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingChart && event.button() == 0) {
            transform.panPixels(deltaX, deltaY);
            followData = false;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingChart && event.button() == 0) {
            draggingChart = false;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0
                && statsPanelHeight > 0
                && mouseX >= statsPanelX && mouseX < statsPanelX + statsPanelWidth
                && mouseY >= statsPanelY && mouseY < statsPanelY + statsPanelHeight) {
            MapperStatisticsView statistics = model.statistics(scope);
            int maximumScroll = statisticsPanel.maximumBucketScroll(statistics, statsPanelHeight);
            if (statisticsPanel.visibleBucketRows(statistics, statsPanelHeight) > 0
                    && maximumScroll > 0) {
                int direction = verticalAmount > 0.0 ? -1 : 1;
                statisticsBucketScroll = Math.clamp(
                    statisticsBucketScroll + direction,
                    0,
                    maximumScroll
                );
                return true;
            }
        }
        if (transform.contains(mouseX, mouseY) && verticalAmount != 0.0) {
            transform.zoomAt(mouseX, mouseY, verticalAmount);
            followData = false;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static String friendlyState(String state) {
        return switch (state) {
            case "WAITING_TO_SEND" -> "Ready to send";
            case "WAITING_FOR_TELEPORT" -> "Waiting for teleport";
            case "WAITING_FOR_STABILIZATION" -> "Stabilizing position";
            case "RECORDING" -> "Recording sample";
            case "COOLDOWN" -> "Cooldown";
            default -> "Idle";
        };
    }

    private static String rounded(double value) {
        return String.format(Locale.ROOT, "%,.0f", value);
    }

    private static String formatInteger(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1_000);
        long hours = seconds / 3_600;
        long minutes = seconds % 3_600 / 60;
        long remainingSeconds = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }
}
