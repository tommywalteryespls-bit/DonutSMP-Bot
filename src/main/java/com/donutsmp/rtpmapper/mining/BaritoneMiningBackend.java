package com.donutsmp.rtpmapper.mining;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Reflection-isolated adapter so the mapper still loads without Baritone. */
public final class BaritoneMiningBackend implements MiningBackend {
    private static final String MOD_ID = "baritone";
    private static final String API_CLASS = "baritone.api.BaritoneAPI";
    private static final String PROVIDER_CLASS = "baritone.api.IBaritoneProvider";
    private static final String BARITONE_CLASS = "baritone.api.IBaritone";
    private static final String MINE_PROCESS_CLASS = "baritone.api.process.IMineProcess";
    private static final String PATHING_BEHAVIOR_CLASS = "baritone.api.behavior.IPathingBehavior";

    private final Minecraft client;
    private Object activeMineProcess;
    private Object activePathingBehavior;

    public BaritoneMiningBackend(Minecraft client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public boolean available() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        try {
            Class.forName(API_CLASS, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public void start(List<String> blockIds, int quantity) {
        Objects.requireNonNull(blockIds, "blockIds");
        Objects.requireNonNull(client.player, "Player is unavailable");
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> baritoneType = Class.forName(BARITONE_CLASS, true, loader);
            Class<?> mineProcessType = Class.forName(MINE_PROCESS_CLASS, true, loader);

            Object baritone = resolveBaritone(loader);
            if (baritone == null) {
                throw new IllegalStateException("Baritone has no local-player instance");
            }

            Object mineProcess = invoke(baritoneType.getMethod("getMineProcess"), baritone);
            if (isMineProcessActive(mineProcess, mineProcessType)) {
                throw new IllegalStateException("Another Baritone mining process is already active");
            }
            Object pathingBehavior = invoke(baritoneType.getMethod("getPathingBehavior"), baritone);
            activeMineProcess = mineProcess;
            activePathingBehavior = pathingBehavior;
            invoke(
                    mineProcessType.getMethod("mineByName", int.class, String[].class),
                    mineProcess,
                    quantity,
                    blockIds.toArray(String[]::new)
            );
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            clearActiveReferences();
            throw new IllegalStateException("Incompatible Baritone API for Minecraft 1.21.11", exception);
        } catch (RuntimeException exception) {
            clearActiveReferences();
            throw exception;
        }
    }

    @Override
    public boolean isMineProcessActive() {
        Object mineProcess = activeMineProcess;
        if (mineProcess == null) {
            return false;
        }
        try {
            Class<?> type = Class.forName(MINE_PROCESS_CLASS, true, getClass().getClassLoader());
            return isMineProcessActive(mineProcess, type);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new IllegalStateException("Incompatible Baritone mining process", exception);
        }
    }

    /** Detects mining started through Baritone itself or another integration. */
    public boolean anyMineProcessActive() {
        if (!available()) {
            return false;
        }
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> baritoneType = Class.forName(BARITONE_CLASS, true, loader);
            Class<?> mineProcessType = Class.forName(MINE_PROCESS_CLASS, true, loader);
            Object baritone = resolveBaritone(loader);
            if (baritone == null) {
                return false;
            }
            Object mineProcess = invoke(baritoneType.getMethod("getMineProcess"), baritone);
            return isMineProcessActive(mineProcess, mineProcessType);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new IllegalStateException("Incompatible Baritone mining process", exception);
        }
    }

    @Override
    public void cancelMine() {
        Object mineProcess = activeMineProcess;
        if (mineProcess == null) {
            clearActiveReferences();
            return;
        }
        try {
            Class<?> type = Class.forName(MINE_PROCESS_CLASS, true, getClass().getClassLoader());
            invoke(type.getMethod("cancel"), mineProcess);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new IllegalStateException("Incompatible Baritone mining process", exception);
        } finally {
            clearActiveReferences();
        }
    }

    @Override
    public void cancelEverything() {
        Object pathingBehavior = activePathingBehavior;
        try {
            ClassLoader loader = getClass().getClassLoader();
            if (pathingBehavior == null && available()) {
                Class<?> baritoneType = Class.forName(BARITONE_CLASS, true, loader);
                Object baritone = resolveBaritone(loader);
                if (baritone != null) {
                    pathingBehavior = invoke(
                            baritoneType.getMethod("getPathingBehavior"),
                            baritone
                    );
                }
            }
            if (pathingBehavior != null) {
                Class<?> type = Class.forName(
                        PATHING_BEHAVIOR_CLASS,
                        true,
                        loader
                );
                invoke(type.getMethod("cancelEverything"), pathingBehavior);
            }
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new IllegalStateException("Incompatible Baritone pathing behavior", exception);
        } finally {
            clearActiveReferences();
        }
    }

    private Object resolveBaritone(ClassLoader loader)
            throws ClassNotFoundException, NoSuchMethodException {
        Class<?> apiType = Class.forName(API_CLASS, true, loader);
        Class<?> providerType = Class.forName(PROVIDER_CLASS, true, loader);
        Object provider = invoke(apiType.getMethod("getProvider"), null);
        Object baritone = null;
        LocalPlayer player = client.player;
        if (player != null) {
            baritone = invoke(
                    providerType.getMethod("getBaritoneForPlayer", LocalPlayer.class),
                    provider,
                    player
            );
        }
        return baritone != null
                ? baritone
                : invoke(providerType.getMethod("getPrimaryBaritone"), provider);
    }

    private static boolean isMineProcessActive(Object mineProcess, Class<?> mineProcessType)
            throws NoSuchMethodException {
        return (boolean)invoke(mineProcessType.getMethod("isActive"), mineProcess);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access the Baritone API", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Baritone API call failed", cause);
        }
    }

    private void clearActiveReferences() {
        activeMineProcess = null;
        activePathingBehavior = null;
    }
}
