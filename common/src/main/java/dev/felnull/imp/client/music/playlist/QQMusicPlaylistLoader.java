package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.qqMusic.QQMusicUtil;
import dev.felnull.imp.client.qqMusic.resource.QQPlaylistData;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.UUID;

public class QQMusicPlaylistLoader implements IPlaylistLoader{
    @Override
    public String getID() {
        return "QQMusic";
    }

    @Override
    public IPlaylistResolver getResolver(String id) {
        return new Resolver(id);
    }

    private static class Resolver extends IPlaylistResolver{
        public Resolver(String id) {
            super(id);
        }

        @Override
        public void run() {
            try{
                long listID;
                try{
                    listID = Long.parseLong(getInputId());
                } catch (NumberFormatException e) {
                    setFailure(e);
                    return;
                }
                var data = QQMusicUtil.getPlaylistData(listID);
                if(data == null || data.getTitle() == null || data.getSongs() == null){
                    setFailure("Empty List");
                    return;
                }
                var musics = new ArrayList<Music>();
                for (QQPlaylistData.TrackInfo soundInfo: data.getSongs()){
                    musics.add(new Music(
                            UUID.randomUUID(),
                            soundInfo.name,
                            soundInfo.getSingerString(),
                            new MusicSource(
                                    IMPMusicMedias.QQ_MUSIC.getName(),
                                    String.valueOf(soundInfo.id),
                                    soundInfo.length * 1000L
                            ),
                            new ImageInfo(ImageInfo.ImageType.URL, soundInfo.album.getPicUrl()),
                            Minecraft.getInstance().getGameProfile().getId(),
                            System.currentTimeMillis()
                    ));
                }
                setResult(new PlaylistResult(
                        String.valueOf(listID),
                        musics.size(),
                        data.getTitle(),
                        data.getCreatorName(),
                        new ImageInfo(ImageInfo.ImageType.URL, data.getPicUrl()),
                        IPlaylistLoader.createEntriesFromMusics(musics), musics
                ));
            } catch (Exception e) {
                setFailure(e);
            }
        }
    }
}
