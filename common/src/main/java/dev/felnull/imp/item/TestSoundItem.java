package dev.felnull.imp.item;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import dev.felnull.imp.client.music.MusicEngine;
import dev.felnull.imp.client.util.LavaPlayerUtil;
import dev.felnull.imp.music.MusicManager;
import dev.felnull.imp.music.MusicPlaybackInfo;
import dev.felnull.imp.music.MusicRinger;
import dev.felnull.imp.music.resource.*;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class TestSoundItem extends Item {

    public TestSoundItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);

        if (level.isClientSide()) {
            UUID id = UUID.randomUUID();
            MusicEngine me = MusicEngine.getInstance();
            var apm = LavaPlayerUtil.createAudioPlayerManager();
            apm.registerSourceManager(new YoutubeAudioSourceManager());
            apm.registerSourceManager(new HttpAudioSourceManager());
            var idi = itemStack.getHoverName().getString();
            var track = LavaPlayerUtil.loadTrackNonThrow(apm, idi);
            var plb = new MusicPlaybackInfo(MusicRinger.FIXED_TRACKER, MusicRinger.createFixedTracker(player.position()), 1, 30);
            if (track.isPresent()) {
                var ms = new MusicSource("", idi, track.get().getInfo().isStream ? -1 : track.get().getDuration());
                me.loadAddMusicPlayer(id, plb, ms, 0, (result, time, player1, retry) -> {
                    player.displayClientMessage(new TextComponent(result + ":" + time + ":" + retry), false);
                    me.playMusicPlayer(id, 0);
                });
            }
           /* var shpe = TentativeVoxelShapeGenerator.generate(OEVoxelShapeUtil.getShapeFromResource(new ResourceLocation(IamMusicPlayer.MODID, "boombox")), OEVoxelShapeUtil.getShapeFromResource(new ResourceLocation(IamMusicPlayer.MODID, "boombox_base_shape")));
            try {
                Files.writeString(Paths.get("shape.json"), new Gson().toJson(shpe));
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        } else {
           /* var ms = new MusicSource("youtube", "FdBVX6bQpCs", 114514);
            var im = new ImageInfo(ImageInfo.ImageType.URL, "https://i.ytimg.com/vi/FdBVX6bQpCs/maxresdefault.jpg");
            var m = new Music(UUID.randomUUID(), "Kame", ms, im, player.getGameProfile().getId());
            MusicManager.getInstance().addMusic(m);

            var ar = new AuthorityInfo(true, player.getUUID());

            var pl = new MusicPlayList(UUID.randomUUID(), "KamePl", im, ar, Lists.newArrayList(m.getUuid()));
            MusicManager.getInstance().addPlayList(pl);*/
        }
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }
}
