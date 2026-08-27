package com.donutsmp.rtpmapper.hud;

import com.donutsmp.rtpmapper.gui.MapperStatusView;
import com.donutsmp.rtpmapper.gui.RtpMapperUiModel;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Compact live mapper status rendered in the modern Fabric HUD layer stack. */
public final class RtpMapperHud {
    private static final Identifier ELEMENT_ID = Identifier.fromNamespaceAndPath("rtpmapper", "status");
    private static final int BACKGROUND = 0xD610141B;
    private static final int BORDER = 0xE04A5666;
    private static final int TITLE = 0xFF57D3FF;
    private static final int TEXT = 0xFFE5EAF0;
    private static final int MUTED = 0xFFA4AFBD;
    private static final int PADDING = 6;
    private static final int LINE_SPACING = 10;

    private RtpMapperHud() {
    }

    public static void register(RtpMapperUiModel model) {
        Objects.requireNonNull(model, "model");
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                ELEMENT_ID,
                (graphics, deltaTracker) -> render(graphics, model)
        );
    }

    private static void render(GuiGraphics graphics, RtpMapperUiModel model) {
        MapperStatusView status = model.status();
        if (!status.running() || !model.settings().showHud()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<String> lines = List.of(
                "RTP Mapper",
                String.format(Locale.ROOT, "Samples: %,d  Session: %,d",
                        status.allTimeSamples(), status.sessionSamples()),
                "Region: " + status.targetRegion().displayName()
                        + " (" + status.selectedRegionCount() + " selected)",
                lastSampleLine(status),
                nextActionLine(status)
        );

        int contentWidth = 0;
        for (String line : lines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        int x = 8;
        int y = 8;
        int width = contentWidth + PADDING * 2;
        int height = PADDING * 2 + lines.size() * LINE_SPACING;

        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        graphics.renderOutline(x, y, width, height, BORDER);
        for (int index = 0; index < lines.size(); index++) {
            int color = index == 0 ? TITLE : (index == lines.size() - 1 ? MUTED : TEXT);
            graphics.drawString(
                    font,
                    lines.get(index),
                    x + PADDING,
                    y + PADDING + index * LINE_SPACING,
                    color,
                    false
            );
        }
    }

    private static String lastSampleLine(MapperStatusView status) {
        if (!status.hasLastSample()) {
            return "Last: --";
        }
        return String.format(
                Locale.ROOT,
                "Last [%s]: %,.0f / %,.0f",
                status.lastRequestedRegion().shortName(),
                status.lastX(),
                status.lastZ()
        );
    }

    private static String nextActionLine(MapperStatusView status) {
        String friendlyState = status.state().replace('_', ' ');
        if ((status.state().equals("COOLDOWN") || status.state().equals("WAITING_TO_SEND"))
                && Double.isFinite(status.secondsUntilNextAction())) {
            return String.format(
                    Locale.ROOT,
                    "Next RTP: %.1fs  ·  %s",
                    Math.max(0.0, status.secondsUntilNextAction()),
                    friendlyState
            );
        }
        return "State: " + friendlyState;
    }
}
