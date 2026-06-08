package dev.felnull.imp.integration.jade;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.CassetteDeckBlockEntity;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class CassetteDeckComponentProvider implements IBlockComponentProvider {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "cassette_deck");
    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (!(blockAccessor.getBlockEntity() instanceof CassetteDeckBlockEntity be)) return;
        if (be.getMonitor() != CassetteDeckBlockEntity.MonitorType.WRITE_EXECUTION) return;

        iTooltip.add(new CassetteDeckWriteElement(be));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
