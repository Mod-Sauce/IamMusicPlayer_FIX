package dev.felnull.imp.server.saveddata;

import dev.felnull.imp.item.component.IMPComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.server.level.saveddata.OEBaseSavedData;
import org.modsauce.otyacraftenginerenewed.server.util.OESaveDataUtils;
import org.modsauce.otyacraftenginerenewed.util.OENbtUtils;

import java.util.*;

public class EarphoneSaveData extends OEBaseSavedData {
    private static final Map<UUID, EarphoneLocation> EARPHONE_LOCATION_DATA = new HashMap<>();
    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        OENbtUtils.writeList(compoundTag, "earphones", EARPHONE_LOCATION_DATA.values().stream().toList(), EarphoneLocation::save);
        return compoundTag;
    }

    public static EarphoneSaveData load(CompoundTag tag, HolderLookup.Provider provider){
        var data = new EarphoneSaveData();
        EARPHONE_LOCATION_DATA.clear();
        for (EarphoneLocation earphoneLocation: OENbtUtils.readList(tag, "earphones", null, EarphoneLocation::load, Tag.TAG_COMPOUND)){
            EARPHONE_LOCATION_DATA.put(earphoneLocation.earphoneUUID(), earphoneLocation);
        }
        return data;
    }

    public record EarphoneLocation(UUID earphoneUUID, UUID ownerUUID, boolean publicity){
        public static CompoundTag save(EarphoneLocation earphoneLocation){
            var compoundTag = new CompoundTag();
            compoundTag.putUUID("earphoneUUID", earphoneLocation.earphoneUUID);
            compoundTag.putUUID("ownerUUID", earphoneLocation.ownerUUID);
            compoundTag.putBoolean("publicity", earphoneLocation.publicity);
            return compoundTag;
        }

        public static EarphoneLocation load(Tag t){
            var tag = (CompoundTag)t;
            var eu = tag.getUUID("earphoneUUID");
            var ou = tag.getUUID("ownerUUID");
            var p = tag.getBoolean("publicity");
            return new EarphoneLocation(eu, ou, p);
        }
    }

    public static Factory<EarphoneSaveData> factory(){
        return createFactory(EarphoneSaveData::new, EarphoneSaveData::load, DataFixTypes.LEVEL);
    }

    public static EarphoneSaveData getInstance(ServerLevel level){
        return OESaveDataUtils.getSaveData(level, "imp_earphone_data", factory());
    }

    public Collection<EarphoneLocation> getEarphones() {
        return EARPHONE_LOCATION_DATA.values();
    }

    public boolean has(UUID earphoneUUID){
        return EARPHONE_LOCATION_DATA.containsKey(earphoneUUID);
    }

    public EarphoneLocation get(UUID earphoneUUID){
        return EARPHONE_LOCATION_DATA.get(earphoneUUID);
    }

    @Nullable
    public EarphoneLocation get(ServerPlayer player){
        for(EarphoneLocation earphoneLocation: EARPHONE_LOCATION_DATA.values())
            if(Objects.equals(earphoneLocation.ownerUUID, player.getUUID()))
                return earphoneLocation;
        return null;
    }

    public void addEarphone(EarphoneLocation earphoneLocation){
        EARPHONE_LOCATION_DATA.put(earphoneLocation.earphoneUUID, earphoneLocation);
        setDirty(true);
    }

    public void removeEarphone(UUID earphoneUUID){
        EARPHONE_LOCATION_DATA.remove(earphoneUUID);
        setDirty(true);
    }

    public void tick(ServerLevel level) {
        var iterator = EARPHONE_LOCATION_DATA.values().iterator();
        var remove = false;

        while (iterator.hasNext()) {
            EarphoneLocation loc = iterator.next();

            var entity = level.getEntity(loc.ownerUUID);

            if(entity == null)
                continue;

            if (!(entity instanceof Player)) {
                iterator.remove();
                remove = true;
                continue;
            }

            ItemStack item = findEarphone(level, loc);

            if(item == null){
                iterator.remove();
                remove = true;
                continue;
            }

            if (!item.has(IMPComponents.EARPHONE_UUID.get())) {
                iterator.remove();
                remove = true;
                continue;
            }

            var uuid = item.get(IMPComponents.EARPHONE_UUID.get());
            if (!loc.earphoneUUID.equals(uuid)) {
                iterator.remove();
                remove = true;
            }
        }
        if(remove)
            setDirty(true);
    }

    public static ItemStack findEarphone(ServerLevel level, EarphoneLocation location){
        return findEarphone(level.getEntity(location.ownerUUID), location);
    }

    public static ItemStack findEarphone(ClientLevel level, EarphoneLocation location){
        Entity entity1 = null;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(location.ownerUUID)) {
                entity1 = entity;
            }
        }
        if(entity1 == null)return null;
        return findEarphone(entity1, location);
    }

    private static ItemStack findEarphone(Entity entity, EarphoneLocation location){
        if (!(entity instanceof Player player)) {
            return null;
        }
        ItemStack item = null;
        for(ItemStack itemStack: player.getInventory().items) {
            var uuid = itemStack.get(IMPComponents.EARPHONE_UUID.get());
            if (uuid != null && Objects.equals(uuid, location.earphoneUUID))
                item = itemStack;
        }
        for(EquipmentSlot slot: EquipmentSlot.values()) {
            var itemStack = player.getItemBySlot(slot);
            var uuid = itemStack.get(IMPComponents.EARPHONE_UUID.get());
            if (uuid != null && Objects.equals(uuid, location.earphoneUUID))
                item = itemStack;
        }
        return item;
    }

    public static ItemStack findEarphone(Entity entity){
        if (!(entity instanceof Player player)) {
            return null;
        }
        ItemStack item = null;
        for(ItemStack itemStack: player.getInventory().items) {
            var uuid = itemStack.get(IMPComponents.EARPHONE_UUID.get());
            if(uuid == null)continue;
            item = itemStack;
        }
        for(EquipmentSlot slot: EquipmentSlot.values()) {
            var itemStack = player.getItemBySlot(slot);
            var uuid = itemStack.get(IMPComponents.EARPHONE_UUID.get());
            if(uuid == null)continue;
            item = itemStack;
        }
        return item;
    }
}
