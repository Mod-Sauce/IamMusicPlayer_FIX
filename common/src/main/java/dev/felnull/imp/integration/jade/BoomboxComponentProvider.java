package dev.felnull.imp.integration.jade;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class BoomboxComponentProvider implements IBlockComponentProvider {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "boombox");
    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (!(blockAccessor.getBlockEntity() instanceof BoomboxBlockEntity be)) return;
        var data = be.getBoomboxData();
        if (!data.isPowered()) return;
        var music = data.getSelectedMusic() != null ? data.getSelectedMusic() : data.getCassetteTapeMusic();
        if(music == null)return;

        iTooltip.add(new MusicInfoElement(data));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
