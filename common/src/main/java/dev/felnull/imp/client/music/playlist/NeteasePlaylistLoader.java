package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import dev.felnull.imp.client.music.netmusic.URLType;
import dev.felnull.imp.client.music.netmusic.api.pojo.NetEaseMusicList;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class NeteasePlaylistLoader implements IPlaylistLoader{
    @Override
    public String getID() {
        return "netease";
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
            if (isStopped()) return;
            try {
                long listID;
                try {
                    listID = Long.parseLong(getInputId());
                } catch (NumberFormatException e) {
                    if(URLType.SONG.isMatch(getInputId())){
                        try {
                            listID = Long.parseLong(Objects.requireNonNull(URLType.SONG.getMatch(getInputId())));
                        } catch (NumberFormatException | NullPointerException e1) {
                            setFailure(e1);
                            return;
                        }
                    }else {
                        setFailure(e);
                        return;
                    }
                }

                NetEaseMusicList.PlayList data;
                try {
                    data = NetMusicUtil.getMusicListInfo(listID);
                    if(data == null) throw new RuntimeException();
                } catch (Exception e) {
                    setFailure(e);
                    return;
                }

                if (isStopped()) return;

                List<Music> musics;
                try {
                    musics = new ArrayList<>(NetMusicUtil.getMusicList(listID));
                } catch (Exception e) {
                    setFailure(e);
                    return;
                }

                if (isStopped()) return;

                var importPlayList = String.valueOf(listID);
                var importPlayListMusicCount = musics.size();
                var importPlayListName = data.getName();
                var importPlayListAuthor = data.getCreator().getNickname();
                var importPlayListIco = new ImageInfo(ImageInfo.ImageType.URL, data.getCoverImgUrl());

                if (isStopped()) return;

                setResult(new PlaylistResult(importPlayList, importPlayListMusicCount, importPlayListName, importPlayListAuthor, importPlayListIco, IPlaylistLoader.createEntriesFromMusics(musics), musics));

            } catch (Exception ex) {
                setFailure(ex);
            }
        }
    }

    @Override
    public Optional<String> autoPasteFromClipboard(String input) {
        return Optional.ofNullable(URLType.SONG_LIST.getMatch(input));
    }
}
