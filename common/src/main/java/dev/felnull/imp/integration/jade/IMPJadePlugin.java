package dev.felnull.imp.integration.jade;

import dev.felnull.imp.block.BoomboxBlock;
import dev.felnull.imp.block.CassetteDeckBlock;
import dev.felnull.imp.integration.JadeIntegration;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class IMPJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        if(!JadeIntegration.INSTANCE.isEnable())return;
        registration.registerBlockComponent(new BoomboxComponentProvider(), BoomboxBlock.class);
        registration.registerBlockComponent(new CassetteDeckComponentProvider(), CassetteDeckBlock.class);
    }
}
