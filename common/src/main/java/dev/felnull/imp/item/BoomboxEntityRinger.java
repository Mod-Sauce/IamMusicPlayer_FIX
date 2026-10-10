package dev.felnull.imp.item;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.integration.TouhouLittleMaidIntegration;
import dev.felnull.imp.explatform.IMPMaidExpectPlatform;
import dev.felnull.imp.music.tracker.IMPMusicTrackers;
import dev.felnull.imp.music.tracker.MusicTrackerEntry;
import dev.felnull.imp.server.music.ringer.IBoomboxRinger;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BoomboxEntityRinger implements IBoomboxRinger {
    private final Entity entity;
    private final UUID uuid;
    private int lastInventory;

    public BoomboxEntityRinger(Entity entity, UUID uuid) {
        this.entity = entity;
        this.uuid = uuid;
    }

    @Override
    public Component getRingerName() {
        if (entity instanceof ItemEntity)
            return Component.translatable("imp.ringer.drop", getBoombox().getDisplayName());

        return Component.translatable("imp.ringer.have", getBoombox().getDisplayName(), entity.getDisplayName());
    }

    @Override
    public ServerLevel getRingerLevel() {
        return (ServerLevel) entity.level();
    }

    @Override
    public UUID getRingerUUID() {
        return uuid;
    }

    @Override
    public boolean exists() {
        boolean canRing = canRing(entity);
        boolean hasItem = !getBoombox().isEmpty();
        boolean exists = canRing && hasItem;

        // ⚠️ [Trace, log only] `exists()==false` makes `MusicRing.tick()` kick this ringer out at the end of the tick (→ STOP to the client), while the item side adds it back every tick (→ LOAD):
        // on the client that is the "loaded successfully, stopped immediately, loaded again" loop.
        if (!exists
                && dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("exists:" + uuid))
            dev.felnull.imp.client.music.link.LinkDebug.log("BoomboxEntityRinger NOT exists",
                    "uuid", uuid, "canRing", canRing, "itemFound", hasItem,
                    "entity", entity.getType() + "#" + entity.getId(),
                    "alive", entity.isAlive(),
                    "foundUUIDs", itemRingerUuids());

        return exists;
    }

    /** Ringer UUID of every boombox item found on the body/on the ground. */
    private String itemRingerUuids() {
        var sb = new StringBuilder();

        if (entity instanceof LivingEntity livingEntity) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                var u = BoomboxItem.getRingerUUID(livingEntity.getItemBySlot(slot));
                if (u != null) sb.append(slot).append('=').append(u).append(' ');
            }
        }
        if (entity instanceof Player player) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var u = BoomboxItem.getRingerUUID(inv.getItem(i));
                if (u != null) sb.append("slot").append(i).append('=').append(u).append(' ');
            }
        }
        if (entity instanceof ItemEntity itemEntity) {
            var u = BoomboxItem.getRingerUUID(itemEntity.getItem());
            if (u != null) sb.append("itemEntity=").append(u).append(' ');
        }

        return sb.isEmpty() ? "(no boombox with a ringer UUID on this entity)" : sb.toString();
    }

    @Override
    public MusicTrackerEntry getRingerTracker() {
        if(getRingerBoomboxData().getEarphoneUUID() != null){
            var loc = EarphoneSaveData.getInstance(getServerLevel()).get(getRingerBoomboxData().getEarphoneUUID());
            if(loc != null) {
                var entity = getServerLevel().getEntity(loc.ownerUUID());
                if(entity != null)
                    return IMPMusicTrackers.createEntityTracker(entity, getRingerVolume(), getRingerRange());
            }
        }
        return IMPMusicTrackers.createEntityTracker(entity, getRingerVolume(), getRingerRange());
    }

    @Override
    public @NotNull Vec3 getRingerSpatialPosition() {
        if(getRingerBoomboxData().getEarphoneUUID() != null){
            var loc = EarphoneSaveData.getInstance(getServerLevel()).get(getRingerBoomboxData().getEarphoneUUID());
            if(loc != null) {
                var entity = getServerLevel().getEntity(loc.ownerUUID());
                if(entity != null)return entity.position();
            }
        }
        return entity.position();
    }

    @NotNull
    private ItemStack getBoombox() {
        if (entity instanceof LivingEntity livingEntity) {
            for (EquipmentSlot value : EquipmentSlot.values()) {
                var item = livingEntity.getItemBySlot(value);
                if (uuid.equals(BoomboxItem.getRingerUUID(item))) return item;
            }
        }
        if (entity instanceof Player player) {
            var li = player.getInventory().getItem(lastInventory);
            if (uuid.equals(BoomboxItem.getRingerUUID(li))) return li;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var item = player.getInventory().getItem(i);
                if (uuid.equals(BoomboxItem.getRingerUUID(item))) {
                    lastInventory = i;
                    return item;
                }
            }
        }
        if (IamMusicPlayer.getConfig().dropItemRing && entity instanceof ItemEntity itemEntity) {
            var item = itemEntity.getItem();
            if (uuid.equals(BoomboxItem.getRingerUUID(item))) return item;
        }
        if (TouhouLittleMaidIntegration.INSTANCE.isEnable()){
            var item = IMPMaidExpectPlatform.getMaidBoombox(entity);
            if(uuid.equals(BoomboxItem.getRingerUUID(item)))return item;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull BoomboxData getRingerBoomboxData() {
        return BoomboxItem.getData(getBoombox(), entity.level().registryAccess());
    }

    public static boolean canRing(Entity entity) {
        if (!entity.isAlive()) return false;
        if (entity instanceof Player player) return !player.isSpectator();
        return true;
    }
}
