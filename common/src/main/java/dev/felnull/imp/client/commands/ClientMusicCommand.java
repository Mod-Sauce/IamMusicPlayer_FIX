package dev.felnull.imp.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.cache.AudioCacheManager;
import dev.felnull.imp.client.cache.LyricCacheManager;
import dev.felnull.imp.client.music.lyric.IMPLyricGetter;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientMusicCommand {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(1);
    private static final Logger LOGGER = LogManager.getLogger(ClientMusicCommand.class);
    public static void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher) {
        var node = dispatcher.register(
                LiteralArgumentBuilder.<ClientCommandRegistrationEvent.ClientCommandSourceStack>literal(IamMusicPlayer.MODID + "Client")
                        .then(LiteralArgumentBuilder.<ClientCommandRegistrationEvent.ClientCommandSourceStack>literal("cache")
                                // netease
                                .then(LiteralArgumentBuilder.<ClientCommandRegistrationEvent.ClientCommandSourceStack>literal("netease")
                                        .then(RequiredArgumentBuilder
                                                .<ClientCommandRegistrationEvent.ClientCommandSourceStack, Long>argument("listID", LongArgumentType.longArg())
                                                .executes(ClientMusicCommand::cacheNetease)
                                        )
                                )
                        )
        );

        dispatcher.register(LiteralArgumentBuilder.<ClientCommandRegistrationEvent.ClientCommandSourceStack>literal("impc").redirect(node));
    }

    private static int cacheNetease(CommandContext<ClientCommandRegistrationEvent.ClientCommandSourceStack> commandContext) {
        long id = LongArgumentType.getLong(commandContext, "listID");
        List<Music> musicList;
        try {
            musicList = NetMusicUtil.getMusicList(id);
        } catch (Exception e) {
            commandContext.getSource().arch$sendFailure(Component.translatable("commands.imp.cache.error"));
            return 0;
        }

        EXECUTOR_SERVICE.submit(() -> {
            LOGGER.info("Start caching...");
            for (Music music: musicList){
                var cacheID = "netease:" + music.getSource().getIdentifier();
                if(AudioCacheManager.has(cacheID))continue;
                URL url;
                try{
                    url = NetMusicUtil.getNetMusicUrl(Long.parseLong(music.getSource().getIdentifier()));
                }catch (Exception e) {
                    LOGGER.error("Failed to cache {}:", music.getSource().getIdentifier(), e);
                    continue;
                }
                if(url == null)continue;
                AudioCacheManager.cacheAsync(cacheID,
                        url.toString());

                Lyric lyric;
                try{
                    var getter = IMPLyricGetter.getGetter(music.getSource());
                    if(getter == null)continue;
                    getter.runAndWait(music.getSource());
                    lyric = getter.getLyric();
                    if(lyric == null)continue;
                    LyricCacheManager.cacheAsync("netease_" + music.getSource().getIdentifier(), lyric);
                }catch (Exception e) {
                    LOGGER.error("Failed to cache {} lyric:", music.getSource().getIdentifier(), e);
                }
            }
            LOGGER.info("Finished caching {} musics", musicList.size());
        });
        commandContext.getSource().arch$sendSuccess(() -> Component.translatable("commands.imp.cache.success", musicList.size()),
                false);

        return 1;
    }
}
