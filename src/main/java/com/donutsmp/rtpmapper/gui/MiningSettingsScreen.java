package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.mining.MiningServerPolicy;
import com.donutsmp.rtpmapper.mining.MiningSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings and controls for the optional, bounded Baritone mining integration. */
public final class MiningSettingsScreen extends Screen {
    private static final int PANEL = 0xF0161A22;
    private static final int BORDER = 0xFF303846;
    private static final int TEXT = 0xFFE5EAF0;
    private static final int MUTED = 0xFF929DAD;
    private static final int ACCENT = 0xFF57D3FF;
    private static final int SUCCESS = 0xFF6EE7A8;
    private static final int ERROR = 0xFFFF6B6B;

    private final Screen parent;
    private final RtpMapperUiModel model;
    private EditBox targets;
    private EditBox quantity;
    private EditBox timeoutMinutes;
    private EditBox allowedServers;
    private Button startStopButton;
    private boolean allowSingleplayer;
    private String draftTargets;
    private String draftQuantity;
    private String draftTimeoutMinutes;
    private String draftAllowedServers;
    private String actionMessage = "";
    private boolean actionSucceeded = true;
    private long actionMessageExpiresAt;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int firstFieldY;
    private int rowSpacing;
    private int labelX;
    private boolean compactLayout;
    private boolean narrowLayout;

    public MiningSettingsScreen(Screen parent, RtpMapperUiModel model) {
        super(Component.literal("Baritone Mining"));
        this.parent = parent;
        this.model = model;
    }

    @Override
    protected void init() {
        captureDraftValues();
        MiningSettingsView settings = model.miningSettings();
        if (draftTargets == null) {
            draftTargets = String.join(", ", settings.blockIds());
            draftQuantity = Integer.toString(settings.quantity());
            draftTimeoutMinutes = format(settings.timeoutMinutes());
            draftAllowedServers = String.join(", ", settings.allowedServers());
            allowSingleplayer = settings.allowSingleplayer();
        }

        panelWidth = Math.min(540, Math.max(1, width - 24));
        panelHeight = Math.min(310, Math.max(1, height - 24));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        compactLayout = height < 340;
        narrowLayout = panelWidth < 400;
        rowSpacing = compactLayout ? 23 : 28;
        firstFieldY = panelY + (compactLayout ? 43 : 57);
        labelX = panelX + 12;
        int valueX = panelX + Math.min(164, Math.max(96, panelWidth / 3));
        int valueWidth = Math.max(1, panelX + panelWidth - 12 - valueX);

        targets = textBox(valueX, firstFieldY, valueWidth, draftTargets, 2_048, "Comma-separated block IDs");
        quantity = textBox(
            valueX,
            firstFieldY + rowSpacing,
            valueWidth,
            draftQuantity,
            8,
            "Matching-drop inventory target, 1-2304"
        );
        timeoutMinutes = textBox(
            valueX,
            firstFieldY + rowSpacing * 2,
            valueWidth,
            draftTimeoutMinutes,
            16,
            "Minutes, 1-120"
        );
        addRenderableWidget(CycleButton.onOffBuilder(allowSingleplayer).create(
            valueX,
            firstFieldY + rowSpacing * 3,
            valueWidth,
            20,
            Component.literal("Allow Single-player"),
            (button, value) -> allowSingleplayer = value
        ));
        allowedServers = textBox(
            valueX,
            firstFieldY + rowSpacing * 4,
            valueWidth,
            draftAllowedServers,
            1_024,
            "Private-server patterns; blank disables multiplayer"
        );

        int buttonY = panelY + panelHeight - 28;
        int gap = 5;
        int buttonWidth = Math.max(1, (panelWidth - 24 - gap * 3) / 4);
        int buttonX = panelX + 12;
        addRenderableWidget(Button.builder(Component.literal("Save"), ignored -> saveDraft())
            .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + gap;
        startStopButton = addRenderableWidget(Button.builder(
                Component.literal(narrowLayout ? "Start" : "Start Mining"),
                ignored -> toggleMining())
            .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + gap;
        addRenderableWidget(Button.builder(
                Component.literal(narrowLayout ? "Emergency" : "Emergency Stop"),
                ignored -> showResult(model.emergencyStop()))
            .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + gap;
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
            .bounds(buttonX, buttonY, buttonWidth, 20).build());
    }

    private EditBox textBox(int x, int y, int width, String value, int maximumLength, String hint) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setMaxLength(maximumLength);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void toggleMining() {
        if (model.miningStatus().running()) {
            showResult(model.stopMining());
            return;
        }
        if (saveDraft()) {
            showResult(model.startMining());
        }
    }

    private boolean saveDraft() {
        try {
            List<String> blockIds = splitCommaSeparated(targets.getValue());
            List<String> serverPatterns = splitCommaSeparated(allowedServers.getValue());
            int parsedQuantity;
            try {
                parsedQuantity = Integer.parseInt(quantity.getValue().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Inventory target must be a whole number.");
            }
            if (parsedQuantity < MiningSettings.MIN_QUANTITY
                    || parsedQuantity > MiningSettings.MAX_QUANTITY) {
                throw new IllegalArgumentException(
                    "Inventory target must be from " + MiningSettings.MIN_QUANTITY
                        + " to " + MiningSettings.MAX_QUANTITY + "."
                );
            }
            double parsedTimeout = parseFinite(timeoutMinutes.getValue(), "Mining timeout");
            MiningSettingsView updated = new MiningSettingsView(
                allowSingleplayer,
                serverPatterns,
                blockIds,
                parsedQuantity,
                parsedTimeout
            );
            UiActionResult result = model.applyMiningSettings(updated);
            showResult(result);
            if (result.success()) {
                targets.setValue(String.join(", ", updated.blockIds()));
                quantity.setValue(Integer.toString(updated.quantity()));
                timeoutMinutes.setValue(format(updated.timeoutMinutes()));
                allowedServers.setValue(String.join(", ", updated.allowedServers()));
            }
            return result.success();
        } catch (IllegalArgumentException exception) {
            showResult(UiActionResult.error(exception.getMessage()));
            return false;
        }
    }

    private void showResult(UiActionResult result) {
        actionSucceeded = result.success();
        actionMessage = result.message();
        actionMessageExpiresAt = System.currentTimeMillis() + 8_000L;
    }

    private void captureDraftValues() {
        if (targets == null) {
            return;
        }
        draftTargets = targets.getValue();
        draftQuantity = quantity.getValue();
        draftTimeoutMinutes = timeoutMinutes.getValue();
        draftAllowedServers = allowedServers.getValue();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF0080A0F);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!actionMessage.isEmpty() && System.currentTimeMillis() >= actionMessageExpiresAt) {
            actionMessage = "";
        }
        MiningStatusView status = model.miningStatus();
        startStopButton.setMessage(Component.literal(
            status.running()
                ? (narrowLayout ? "Stop" : "Stop Mining")
                : (narrowLayout ? "Start" : "Start Mining")
        ));

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, BORDER);
        graphics.drawString(font, title, labelX, panelY + 11, ACCENT, false);
        graphics.drawString(
            font,
            fitText(
                MiningServerPolicy.HARD_BLOCKED_SERVER
                    + "/subdomains blocked — use a separate Baritone profile.",
                panelWidth - 24
            ),
            labelX,
            panelY + 27,
            ERROR,
            false
        );

        drawLabel(graphics, narrowLayout ? "Targets" : "Mining targets", firstFieldY + 6);
        drawLabel(graphics, narrowLayout ? "Inv. target" : "Inventory target", firstFieldY + rowSpacing + 6);
        drawLabel(graphics, narrowLayout ? "Timeout" : "Timeout (minutes)", firstFieldY + rowSpacing * 2 + 6);
        drawLabel(graphics, "Single-player", firstFieldY + rowSpacing * 3 + 6);
        drawLabel(graphics, narrowLayout ? "Server list" : "Private server allowlist", firstFieldY + rowSpacing * 4 + 6);

        int statusY = firstFieldY + rowSpacing * 5 + 4;
        String baritone = status.baritoneAvailable() ? "Baritone detected" : "Baritone not detected";
        String server = status.serverAllowed() ? "server permitted" : "server blocked";
        graphics.drawString(
            font,
            fitText(baritone + " · " + server + serverSuffix(status), panelWidth - 24),
            labelX,
            statusY,
            status.baritoneAvailable() && status.serverAllowed() ? SUCCESS : MUTED,
            false
        );
        if (!compactLayout) {
            String activity = status.running()
                ? "RUNNING · " + friendlyState(status.state()) + remainingSuffix(status.secondsRemaining())
                : friendlyState(status.state());
            graphics.drawString(
                font,
                fitText(activity, panelWidth - 24),
                labelX,
                statusY + 14,
                status.running() ? SUCCESS : MUTED,
                false
            );
        }

        String message = !actionMessage.isEmpty() ? actionMessage : status.detail();
        if (!message.isEmpty()) {
            graphics.drawString(
                font,
                fitText(message, panelWidth - 24),
                labelX,
                panelY + panelHeight - 43,
                !actionMessage.isEmpty() && !actionSucceeded ? ERROR : MUTED,
                false
            );
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawLabel(GuiGraphics graphics, String value, int y) {
        graphics.drawString(font, value, labelX, y, TEXT, false);
    }

    private String serverSuffix(MiningStatusView status) {
        return status.serverDescription().isEmpty() ? "" : " · " + status.serverDescription();
    }

    private static String remainingSuffix(double secondsRemaining) {
        if (secondsRemaining < 0.0) {
            return "";
        }
        return String.format(Locale.ROOT, " · %.0fs left", secondsRemaining);
    }

    private static String friendlyState(String state) {
        return switch (state) {
            case "RUNNING" -> "Mining requested blocks";
            case "COMPLETED" -> "Baritone process ended";
            case "STOPPED" -> "Stopped";
            case "FAILED" -> "Mining stopped after an error";
            default -> "Idle";
        };
    }

    private static List<String> splitCommaSeparated(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        return Arrays.stream(input.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
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
