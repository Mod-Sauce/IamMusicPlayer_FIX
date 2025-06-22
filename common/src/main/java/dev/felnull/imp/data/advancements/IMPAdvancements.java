package dev.felnull.imp.data.advancements;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.advancements.AddMusicTrigger;
import dev.felnull.imp.advancements.ListenToMusicTrigger;
import dev.felnull.imp.advancements.WriteCassetteTapeTrigger;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.item.IMPItems;
import dev.felnull.imp.util.IMPItemUtil;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.FrameType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.modsauce.otyacraftenginerenewed.advancement.ModInvolvementTrigger;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.AdvancementSubProviderWrapper;

import java.util.function.Consumer;

public class IMPAdvancements extends AdvancementSubProviderWrapper {
  protected IMPAdvancements(CrossDataGeneratorAccess crossDataGeneratorAccess) {
    super(crossDataGeneratorAccess);
  }

  @Override
  public void generate(Consumer<AdvancementHolder> advancementConsumer) {
    AdvancementHolder root = Advancement.Builder.advancement()
        .display(IMPBlocks.BOOMBOX.get(), Component.translatable("advancements.iammusicplayer.root.title"),
            Component.translatable("advancements.iammusicplayer.root.description"),
            new ResourceLocation("iammusicplayer:textures/gui/advancements/backgrounds/imp.png"), FrameType.TASK, false,
            false, false)
        .addCriterion(IamMusicPlayer.MODID, ModInvolvementTrigger.TriggerInstance.involvedMod(IamMusicPlayer.MODID))
        .save(advancementConsumer,
            new ResourceLocation(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/root").toString());

    AdvancementHolder addMusic = Advancement.Builder.advancement().parent(root)
        .display(IMPBlocks.MUSIC_MANAGER.get(), Component.translatable("advancements.iammusicplayer.add_music.title"),
            Component.translatable("advancements.iammusicplayer.add_music.description"), null, FrameType.TASK, true,
            true, false)
        .addCriterion("add_music", AddMusicTrigger.TriggerInstance.addMusic())
        .save(advancementConsumer,
            new ResourceLocation(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/add_music").toString());

    AdvancementHolder writeCassetteTape = Advancement.Builder.advancement().parent(addMusic)
        .display(IMPBlocks.CASSETTE_DECK.get(),
            Component.translatable("advancements.iammusicplayer.write_cassette_tape.title"),
            Component.translatable("advancements.iammusicplayer.write_cassette_tape.description"), null, FrameType.TASK,
            true, true, false)
        .addCriterion("write_cassette_tape", WriteCassetteTapeTrigger.TriggerInstance.writeCassetteTape())
        .save(advancementConsumer,
            new ResourceLocation(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/write_cassette_tape").toString());

    AdvancementHolder listenToMusic = Advancement.Builder.advancement().parent(writeCassetteTape)
        .display(IMPBlocks.BOOMBOX.get(), Component.translatable("advancements.iammusicplayer.listen_to_music.title"),
            Component.translatable("advancements.iammusicplayer.listen_to_music.description"), null, FrameType.TASK,
            true, true, false)
        .addCriterion("listen_to_music", ListenToMusicTrigger.TriggerInstance.listen(false, false, false))
        .save(advancementConsumer,
            new ResourceLocation(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_music").toString());

    AdvancementHolder listenToRadio = Advancement.Builder.advancement().parent(listenToMusic)
        .display(IMPItems.RADIO_ANTENNA.get(),
            Component.translatable("advancements.iammusicplayer.listen_to_radio.title"),
            Component.translatable("advancements.iammusicplayer.listen_to_radio.description"), null, FrameType.GOAL,
            true, true, false)
        .addCriterion("listen_to_radio", ListenToMusicTrigger.TriggerInstance.listen(true, false, false))
        .save(advancementConsumer,
            new ResourceLocation(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_radio").toString());

    AdvancementHolder listenToRemoteMusic = Advancement.Builder.advancement().parent(listenToMusic)
        .display(IMPItems.PARABOLIC_ANTENNA.get(),
            Component.translatable("advancements.iammusicplayer.listen_to_remote_music.title"),
            Component.translatable("advancements.iammusicplayer.listen_to_remote_music.description"), null,
            FrameType.GOAL, true, true, false)
        .addCriterion("listen_to_remote_music", ListenToMusicTrigger.TriggerInstance.listen(false, true, false))
        .save(advancementConsumer,
            new ResourceLocation(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_remote_music").toString());

    AdvancementHolder listenToKamesuta = Advancement.Builder.advancement().parent(listenToRemoteMusic)
        .display(IMPItemUtil.createKamesutaAntenna(),
            Component.translatable("advancements.iammusicplayer.listen_to_kamesuta.title"),
            Component.translatable("advancements.iammusicplayer.listen_to_kamesuta.description"), null,
            FrameType.CHALLENGE, true, true, true)
        .addCriterion("listen_to_kamesuta", ListenToMusicTrigger.TriggerInstance.listen(false, false, true))
        .save(advancementConsumer,
            new ResourceLocation(IamMusicPlayer.MODID, IamMusicPlayer.MODID + "/listen_to_kamesuta").toString());
  }
}
