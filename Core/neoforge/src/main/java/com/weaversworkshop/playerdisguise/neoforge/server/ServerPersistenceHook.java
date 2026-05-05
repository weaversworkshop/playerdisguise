package com.weaversworkshop.playerdisguise.neoforge.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.ServerDisguiseState;
import com.weaversworkshop.playerdisguise.server.SkinStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;

@EventBusSubscriber(modid = PlayerDisguise.MODID)
public final class ServerPersistenceHook {
    private static final int SAVE_INTERVAL_TICKS = 20 * 60 * 5;  // 5 minutes
    private static final int PRUNE_INTERVAL_TICKS = 20 * 60;     // 1 minute
    private static final int GC_INTERVAL_TICKS = 20 * 60 * 10;   // 10 minutes
    private static int saveCounter = 0;
    private static int pruneCounter = 0;
    private static int gcCounter = 0;

    private ServerPersistenceHook() {}

    private static Path aliasFile(MinecraftServer server) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        return worldDir.resolve("playerdisguise").resolve("aliases.json");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        Path worldDir = event.getServer().getWorldPath(LevelResource.ROOT);
        AliasRegistry.get().load(aliasFile(event.getServer()));
        SkinStore.get().setDir(worldDir.resolve("playerdisguise").resolve("skins"));
        ServerDisguiseState.get().clearAll();
        saveCounter = 0;
        pruneCounter = 0;
        gcCounter = 0;
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        AliasRegistry.get().save(aliasFile(event.getServer()));
        int removed = SkinStore.get().gcOrphans(ServerDisguiseState.get().liveSkinHashes());
        if (removed > 0) PlayerDisguise.LOGGER.info("Skin store GC: removed {} orphan blob(s)", removed);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        saveCounter++;
        pruneCounter++;
        gcCounter++;
        if (pruneCounter >= PRUNE_INTERVAL_TICKS) {
            pruneCounter = 0;
            AliasRegistry.get().pruneExpired();
        }
        if (saveCounter >= SAVE_INTERVAL_TICKS) {
            saveCounter = 0;
            AliasRegistry.get().save(aliasFile(event.getServer()));
        }
        if (gcCounter >= GC_INTERVAL_TICKS) {
            gcCounter = 0;
            int removed = SkinStore.get().gcOrphans(ServerDisguiseState.get().liveSkinHashes());
            if (removed > 0) PlayerDisguise.LOGGER.info("Skin store GC: removed {} orphan blob(s)", removed);
        }
    }
}
