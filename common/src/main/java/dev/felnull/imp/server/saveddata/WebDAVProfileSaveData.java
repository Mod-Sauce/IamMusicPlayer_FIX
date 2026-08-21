package dev.felnull.imp.server.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import org.modsauce.otyacraftenginerenewed.server.level.saveddata.OEBaseSavedData;
import org.modsauce.otyacraftenginerenewed.server.util.OESaveDataUtils;
import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;
import dev.felnull.imp.server.webdav.ServerWebDAVProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WebDAVProfileSaveData extends OEBaseSavedData {
    private final Map<UUID, ServerWebDAVProfile> profiles = new HashMap<>();

    public static WebDAVProfileSaveData get(MinecraftServer server) {
        return OESaveDataUtils.getSaveData(server, "imp_webdav_profiles", factory());
    }

    public static Factory<WebDAVProfileSaveData> factory() {
        return createFactory(WebDAVProfileSaveData::new, WebDAVProfileSaveData::load, DataFixTypes.LEVEL);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag profilesTag = new CompoundTag();
        profiles.forEach((uuid, profile) -> profilesTag.put(uuid.toString(), profile.createSavedTag()));
        tag.put("Profiles", profilesTag);
        return tag;
    }

    public static WebDAVProfileSaveData load(CompoundTag tag, HolderLookup.Provider provider) {
        WebDAVProfileSaveData data = new WebDAVProfileSaveData();
        CompoundTag profilesTag = tag.getCompound("Profiles");
        for (String key : profilesTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                data.profiles.put(uuid, TagSerializable.loadSavedTag(profilesTag.getCompound(key), new ServerWebDAVProfile()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public ServerWebDAVProfile getProfile(UUID playerId) {
        return profiles.get(playerId);
    }

    public void setProfile(UUID playerId, ServerWebDAVProfile profile) {
        profiles.put(playerId, profile);
        setDirty();
    }
}
