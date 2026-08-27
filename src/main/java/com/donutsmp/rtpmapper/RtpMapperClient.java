package com.donutsmp.rtpmapper;

import com.donutsmp.rtpmapper.command.RtpMapperCommands;
import com.donutsmp.rtpmapper.gui.RtpMapperScreen;
import com.donutsmp.rtpmapper.hud.RtpMapperHud;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric client entrypoint; no Mixins are required. */
public final class RtpMapperClient implements ClientModInitializer {
    public static final String MOD_ID = "rtpmapper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(MOD_ID, "controls")
    );

    private RtpMapperRuntime runtime;
    private KeyMapping openScreenKey;
    private KeyMapping emergencyStopKey;

    @Override
    public void onInitializeClient() {
        Minecraft client = Minecraft.getInstance();
        runtime = RtpMapperRuntime.create(client, LOGGER);
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.rtpmapper.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KEY_CATEGORY
        ));
        emergencyStopKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.rtpmapper.emergency_stop",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_END,
            KEY_CATEGORY
        ));
        ScreenEvents.BEFORE_INIT.register((eventClient, screen, scaledWidth, scaledHeight) ->
            ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, keyEvent) -> {
                if (!emergencyStopKey.matches(keyEvent)) {
                    return true;
                }
                activateEmergencyStop(eventClient);
                return false;
            })
        );

        ClientTickEvents.END_CLIENT_TICK.register(ignored -> onEndClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, eventClient) ->
            eventClient.execute(() -> runtime.onDisconnected(handler)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, eventClient) ->
            eventClient.execute(() -> runtime.onJoined(handler)));
        ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> runtime.close());
        RtpMapperCommands.register(runtime);
        RtpMapperHud.register(runtime);
        LOGGER.info("[RTP Mapper] Initialized for Minecraft 1.21.11");
    }

    private void onEndClientTick() {
        while (emergencyStopKey.consumeClick()) {
            activateEmergencyStop(Minecraft.getInstance());
        }
        runtime.tick();
        while (openScreenKey.consumeClick()) {
            Minecraft client = Minecraft.getInstance();
            if (!(client.screen instanceof RtpMapperScreen)) {
                client.setScreen(new RtpMapperScreen(runtime));
            }
        }
    }

    private void activateEmergencyStop(Minecraft client) {
        String message = runtime.emergencyStop().message();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[RTP Mapper] " + message), false);
        }
    }
}
