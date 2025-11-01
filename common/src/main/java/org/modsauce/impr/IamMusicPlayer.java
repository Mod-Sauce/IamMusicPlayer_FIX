package org.modsauce.impr;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import dev.architectury.platform.Platform;
import org.modsauce.impr.advancements.IMPCriteriaTriggers;
import org.modsauce.impr.block.IMPBlocks;
import org.modsauce.impr.blockentity.IMPBlockEntities;
import org.modsauce.impr.component.IMPDataComponents;
import org.modsauce.impr.entity.village.IMPPoiType;
import org.modsauce.impr.entity.village.IMPVillagerProfessions;
import org.modsauce.impr.handler.CommonHandler;
import org.modsauce.impr.inventory.IMPMenus;
import org.modsauce.impr.item.IMPCreativeModeTabs;
import org.modsauce.impr.item.IMPItems;
import org.modsauce.impr.networking.IMPPackets;
import org.modsauce.impr.server.handler.ServerHandler;
import org.modsauce.impr.server.handler.ServerMusicHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;

public class IamMusicPlayer {
    public static final String MODID = "iammusicplayer";
    private static final Supplier<String> MODNAME = Suppliers.memoize(() -> Platform.getMod(MODID).getName());
    private static final IMPConfig CONFIG = AutoConfig.register(IMPConfig.class, Toml4jConfigSerializer::new).getConfig();

    public static void init() {
        IMPDataComponents.register();
        IMPPackets.init();
        IMPCreativeModeTabs.init();
        IMPItems.init();
        IMPBlocks.init();
        IMPBlockEntities.init();
        IMPMenus.init();
        IMPPoiType.init();
        IMPVillagerProfessions.init();
        IMPCriteriaTriggers.init();
        ServerMusicHandler.init();
        ServerHandler.init();
        CommonHandler.init();
    }

    public static void setup() {
        IMPVillagerProfessions.setup();
    }

    public static String getModName() {
        return MODNAME.get();
    }

    public static IMPConfig getConfig() {
        return CONFIG;
    }
}
