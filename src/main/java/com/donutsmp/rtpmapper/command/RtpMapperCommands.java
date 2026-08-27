package com.donutsmp.rtpmapper.command;

import com.donutsmp.rtpmapper.gui.MapperStatusView;
import com.donutsmp.rtpmapper.gui.MiningStatusView;
import com.donutsmp.rtpmapper.gui.RtpMapperUiModel;
import com.donutsmp.rtpmapper.gui.UiActionResult;
import com.mojang.brigadier.Command;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Registers the wholly client-side {@code /rtpmapper} command tree. */
public final class RtpMapperCommands {
    private static final String PREFIX = "[RTP Mapper] ";
    private static final AtomicReference<Runnable> PENDING_SCREEN = new AtomicReference<>();

    private RtpMapperCommands() {
    }

    public static void register(RtpMapperUiModel model) {
        Objects.requireNonNull(model, "model");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Runnable pending = PENDING_SCREEN.getAndSet(null);
            if (pending != null) {
                pending.run();
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                ClientCommandManager.literal("rtpmapper")
                        .executes(context -> showStatus(context.getSource(), model))
                        .then(ClientCommandManager.literal("start")
                                .executes(context -> sendResult(context.getSource(), model.startMapping())))
                        .then(ClientCommandManager.literal("stop")
                                .executes(context -> sendResult(context.getSource(), model.stopMapping())))
                        .then(ClientCommandManager.literal("status")
                                .executes(context -> showStatus(context.getSource(), model)))
                        .then(ClientCommandManager.literal("mine")
                                .executes(context -> showMiningStatus(context.getSource(), model))
                                .then(ClientCommandManager.literal("start")
                                        .executes(context -> sendResult(
                                                context.getSource(), model.startMining())))
                                .then(ClientCommandManager.literal("stop")
                                        .executes(context -> sendResult(
                                                context.getSource(), model.stopMining())))
                                .then(ClientCommandManager.literal("status")
                                        .executes(context -> showMiningStatus(
                                                context.getSource(), model))))
                        .then(ClientCommandManager.literal("emergency")
                                .executes(context -> sendResult(
                                        context.getSource(), model.emergencyStop())))
                        .then(ClientCommandManager.literal("clear")
                                .executes(context -> openClearConfirmation(context.getSource(), model)))
                        .then(ClientCommandManager.literal("export")
                                .executes(context -> sendResult(context.getSource(), model.exportCsv())))
        ));
    }

    private static int showStatus(FabricClientCommandSource source, RtpMapperUiModel model) {
        MapperStatusView status = model.status();
        String running = status.running() ? "RUNNING" : "STOPPED";
        String message = String.format(
                Locale.ROOT,
                "%s | State: %s | Target: %s (%d selected) | Samples: %,d session / %,d all-time | Failed: %,d",
                running,
                friendlyState(status.state()),
                status.targetRegion().displayName(),
                status.selectedRegionCount(),
                status.sessionSamples(),
                status.allTimeSamples(),
                status.failedAttempts()
        );
        if (!status.detail().isBlank()) {
            message += " | " + status.detail();
        }
        source.sendFeedback(Component.literal(PREFIX + message).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int showMiningStatus(
            FabricClientCommandSource source,
            RtpMapperUiModel model
    ) {
        MiningStatusView status = model.miningStatus();
        String availability = status.baritoneAvailable() ? "Baritone detected" : "Baritone not installed";
        String running = status.running() ? "RUNNING" : "STOPPED";
        String policy = status.serverAllowed() ? "ALLOWED" : "BLOCKED";
        String message = String.format(
                Locale.ROOT,
                "%s | %s | State: %s | Policy: %s | Server: %s | Targets: %d | Inventory target: %,d",
                running,
                availability,
                friendlyState(status.state()),
                policy,
                status.serverDescription().isBlank() ? "Unavailable" : status.serverDescription(),
                status.targets().size(),
                status.quantity()
        );
        if (status.secondsRemaining() >= 0) {
            message += String.format(Locale.ROOT, " | Timeout in %.1fs", status.secondsRemaining());
        }
        if (!status.detail().isBlank()) {
            message += " | " + status.detail();
        }
        source.sendFeedback(Component.literal(PREFIX + message).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int openClearConfirmation(FabricClientCommandSource source, RtpMapperUiModel model) {
        Minecraft minecraft = source.getClient();
        Screen returnScreen = minecraft.screen instanceof ChatScreen ? null : minecraft.screen;
        int sampleCount = Math.max(0, model.status().allTimeSamples());
        String formattedCount = String.format(Locale.ROOT, "%,d", sampleCount);

        PENDING_SCREEN.set(() -> minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(returnScreen);
                    if (confirmed) {
                        sendResult(source, model.clearAllData());
                    } else {
                        source.sendFeedback(Component.literal(PREFIX + "Clear cancelled.")
                                .withStyle(ChatFormatting.GRAY));
                    }
                },
                Component.literal("Delete all " + formattedCount + " RTP samples?"),
                Component.literal("This action cannot be undone."),
                Component.literal("Delete"),
                CommonComponents.GUI_CANCEL
        )));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendResult(FabricClientCommandSource source, UiActionResult result) {
        Objects.requireNonNull(result, "model action returned null");
        String message = result.message().isBlank()
                ? (result.success() ? "Done." : "The action failed.")
                : result.message();
        Component feedback = Component.literal(PREFIX + message)
                .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (result.success()) {
            source.sendFeedback(feedback);
            return Command.SINGLE_SUCCESS;
        }
        source.sendError(feedback);
        return 0;
    }

    private static String friendlyState(String state) {
        if (state == null || state.isBlank()) {
            return "IDLE";
        }
        return state.replace('_', ' ');
    }
}
