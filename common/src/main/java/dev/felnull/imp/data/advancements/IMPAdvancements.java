package dev.felnull.imp.data.advancements;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.advancements.AddMusicTrigger;
import dev.felnull.imp.advancements.IMPCriteriaTriggers;
import dev.felnull.imp.advancements.ListenToMusicTrigger;
import dev.felnull.imp.advancements.WriteCassetteTapeTrigger;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.item.IMPItems;
import dev.felnull.imp.util.IMPItemUtil;
import net.minecraft.advancements.*;
import net.minecraft.world.item.Items;
import org.modsauce.otyacraftenginerenewed.advancement.ModInvolvementTrigger;
import org.modsauce.otyacraftenginerenewed.advancement.OECriteriaTriggers;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.AdvancementSubProviderWrapper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public class IMPAdvancements extends AdvancementSubProviderWrapper {
    protected IMPAdvancements(CrossDataGeneratorAccess crossDataGeneratorAccess) {
        super(crossDataGeneratorAccess);
    }
    @Override
    public void generate(Consumer<AdvancementHolder> advancementConsumer) {
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(IMPBlocks.BOOMBOX.get(), Component.translatable("advancements.iammusicplayer.root.title"), Component.translatable("advancements.iammusicplayer.root.description"), ResourceLocation.parse("iammusicplayer:textures/gui/advancements/backgrounds/imp.png"), AdvancementType.TASK, false, false, false)
                .addCriterion(IamMusicPlayer.MODID, new Criterion<>(OECriteriaTriggers.MOD_INVOLVEMENT_TRIGGER, ModInvolvementTrigger.TriggerInstance.involvedMod(IamMusicPlayer.MODID)))
                .save(advancementConsumer, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/root").toString());

        AdvancementHolder addMusic = Advancement.Builder.advancement().parent(root)
                .display(IMPBlocks.MUSIC_MANAGER.get(), Component.translatable("advancements.iammusicplayer.add_music.title"), Component.translatable("advancements.iammusicplayer.add_music.description"), null, AdvancementType.TASK, true, true, false)
                .addCriterion("add_music", new Criterion<>(IMPCriteriaTriggers.ADD_MUSIC, AddMusicTrigger.TriggerInstance.addMusic()))
                .save(advancementConsumer, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/add_music").toString());

        AdvancementHolder writeCassetteTape = Advancement.Builder.advancement().parent(addMusic)
                .display(IMPBlocks.CASSETTE_DECK.get(), Component.translatable("advancements.iammusicplayer.write_cassette_tape.title"), Component.translatable("advancements.iammusicplayer.write_cassette_tape.description"), null, AdvancementType.TASK, true, true, false)
                .addCriterion("write_cassette_tape", new Criterion<>(IMPCriteriaTriggers.WRITE_CASSETTE_TAPE, WriteCassetteTapeTrigger.TriggerInstance.writeCassetteTape()))
                .save(advancementConsumer, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/write_cassette_tape").toString());

        AdvancementHolder listenToMusic = Advancement.Builder.advancement().parent(writeCassetteTape)
                .display(IMPBlocks.BOOMBOX.get(), Component.translatable("advancements.iammusicplayer.listen_to_music.title"), Component.translatable("advancements.iammusicplayer.listen_to_music.description"), null, AdvancementType.TASK, true, true, false)
                .addCriterion("listen_to_music", new Criterion<>(IMPCriteriaTriggers.LISTEN_TO_MUSIC, ListenToMusicTrigger.TriggerInstance.listen(false, false)))
                .save(advancementConsumer, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_music").toString());

        AdvancementHolder listenToRadio = Advancement.Builder.advancement().parent(listenToMusic)
                .display(IMPItems.RADIO_ANTENNA.get(), Component.translatable("advancements.iammusicplayer.listen_to_radio.title"), Component.translatable("advancements.iammusicplayer.listen_to_radio.description"), null, AdvancementType.GOAL, true, true, false)
                .addCriterion("listen_to_radio", new Criterion<>(IMPCriteriaTriggers.LISTEN_TO_MUSIC, ListenToMusicTrigger.TriggerInstance.listen(true, false)))
                .save(advancementConsumer, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_radio").toString());

        AdvancementHolder listenToRemoteMusic = Advancement.Builder.advancement().parent(listenToMusic)
                .display(IMPItems.PARABOLIC_ANTENNA.get(), Component.translatable("advancements.iammusicplayer.listen_to_remote_music.title"), Component.translatable("advancements.iammusicplayer.listen_to_remote_music.description"), null, AdvancementType.GOAL, true, true, false)
                .addCriterion("listen_to_remote_music", new Criterion<>(IMPCriteriaTriggers.LISTEN_TO_MUSIC, ListenToMusicTrigger.TriggerInstance.listen(false, true)))
                .save(advancementConsumer, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_remote_music").toString());

        AdvancementHolder listenToKamesuta = Advancement.Builder.advancement().parent(listenToRemoteMusic)
                .display(IMPItemUtil.createKamesutaAntenna(), Component.translatable("advancements.iammusicplayer.listen_to_kamesuta.title"), Component.translatable("advancements.iammusicplayer.listen_to_kamesuta.description"), null, AdvancementType.CHALLENGE, true, true, true)
                .addCriterion("listen_to_kamesuta", new Criterion<>(IMPCriteriaTriggers.LISTEN_TO_MUSIC, ListenToMusicTrigger.TriggerInstance.listen(false, false, "kamesuta")))
                .save(advancementConsumer, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_kamesuta").toString());

        AdvancementHolder listenToCat = Advancement.Builder.advancement().parent(listenToRemoteMusic)
                .display(Items.CAT_SPAWN_EGG, Component.translatable("advancements.iammusicplayer.listen_to_cat.title"), Component.translatable("advancements.iammusicplayer.listen_to_cat.description"), null, AdvancementType.CHALLENGE, true, true, true)
                .addCriterion("listen_to_cat", new Criterion<>(IMPCriteriaTriggers.LISTEN_TO_MUSIC, ListenToMusicTrigger.TriggerInstance.listen(false, false, "cat")))
                .save(advancementConsumer, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_cat").toString());
    }
}
