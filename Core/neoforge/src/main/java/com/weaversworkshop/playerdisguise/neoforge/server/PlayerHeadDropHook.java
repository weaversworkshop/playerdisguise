package com.weaversworkshop.playerdisguise.neoforge.server;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.server.AliasRegistry;
import com.weaversworkshop.playerdisguise.server.ServerDisguiseState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = PlayerDisguise.MODID)
public final class PlayerHeadDropHook {
    public static final String DISGUISE_PROPERTY = "playerdisguise_skin";

    private PlayerHeadDropHook() {}

    /** Catches drops before other mods (e.g. corpse mods) move them out of the level. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player dying)) return;
        UUID dyingUuid = dying.getUUID();
        for (ItemEntity ie : event.getDrops()) {
            tryStamp(ie.getItem(), dyingUuid);
        }
    }

    /** Catch-all for player_heads that reach the level by other paths. */
    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity ie)) return;
        tryStamp(ie.getItem(), null);
    }

    /**
     * @param expectedOwner if non-null, only stamp when the head's profile id matches.
     *                      if null, stamp when the head's profile id is any disguised player.
     */
    private static void tryStamp(ItemStack stack, UUID expectedOwner) {
        if (!stack.is(Items.PLAYER_HEAD)) return;

        ResolvableProfile rp = stack.get(DataComponents.PROFILE);
        if (rp == null) return;

        if (rp.gameProfile() != null
                && !rp.gameProfile().getProperties().get(DISGUISE_PROPERTY).isEmpty()) return;

        UUID realUuid = rp.id().orElse(null);
        if (realUuid == null) return;
        if (expectedOwner != null && !expectedOwner.equals(realUuid)) return;

        String alias = AliasRegistry.get().aliasOf(realUuid);
        if (alias == null) return;

        ServerDisguiseState.Skin skin = ServerDisguiseState.get().skinFor(realUuid);
        String hash = skin == null ? "" : skin.hash();
        String model = skin == null ? "" : skin.model();
        if (hash.isBlank()) return;

        UUID syntheticId = UUID.nameUUIDFromBytes(("playerdisguise:" + alias).getBytes());
        PropertyMap props = new PropertyMap();
        props.put(DISGUISE_PROPERTY, new Property(DISGUISE_PROPERTY, hash + ":" + model));

        stack.set(DataComponents.PROFILE, new ResolvableProfile(
                Optional.of(alias), Optional.of(syntheticId), props));

        PlayerDisguise.LOGGER.debug("Stamped player_head: alias={} hash={} model={}", alias, hash, model);
    }
}
