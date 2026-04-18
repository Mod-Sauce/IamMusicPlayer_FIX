package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import dev.felnull.imp.client.music.netmusic.URLType;
import dev.felnull.imp.client.music.netmusic.api.pojo.NetEaseMusicList;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.networking.IMPPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.modsauce.otyacraftenginerenewed.networking.existence.BlockEntityExistence;
import org.modsauce.otyacraftenginerenewed.util.FlagThread;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ImportNetEasePlayListMusicsMMMonitor extends ImportNetEasePlayListBaseMMMonitor {
    protected static final Component IMPORTING_TEXT = Component.translatable("imp.text.importing");
    protected static final Component IMPORT_FAILURE_TEXT = Component.translatable("imp.text.importFailure");
    private ImportMusicLoader importMusicLoader;
    private boolean failureImportPlayList;


    public ImportNetEasePlayListMusicsMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        Component ipTx = null;
        if (isImporting()) {
            ipTx = IMPORTING_TEXT;
        } else if (failureImportPlayList) {
            ipTx = IMPORT_FAILURE_TEXT;
        } else if (getImportPlayListMusicCount() > 0) {
            ipTx = Component.translatable("imp.text.importMusicCount", getImportPlayListMusicCount());
        }
        if (ipTx != null)
            drawSmartText(guiGraphics, ipTx, getStartX() + width - 95 + 7, getStartY() + 184);
    }

    @Override
    protected void onImport() {
        if (isImporting()) return;
        var ipl = getImportPlayList();
        if (getImportPlayListMusicCount() > 0 && !ipl.isEmpty())
            startImportMusicLoader(ipl);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.IMPORT_MUSICS_SELECT;
    }

    private boolean isImporting() {
        return importMusicLoader != null && importMusicLoader.isAlive();
    }

    private void startImportMusicLoader(String id) {
        stopImportMusicLoader();
        failureImportPlayList = false;
        importMusicLoader = new ImportMusicLoader(id);
        importMusicLoader.start();
    }

    private void stopImportMusicLoader() {
        if (importMusicLoader != null) {
            importMusicLoader.stopped();
            importMusicLoader = null;
        }
    }

    private class ImportMusicLoader extends FlagThread {
        private final String id;

        private ImportMusicLoader(String id) {
            this.id = id;
        }

        @Override
        public void run() {
            if (isStopped()) return;
            try {
                long listID;
                try {
                    listID = Long.parseLong(id);
                } catch (NumberFormatException e) {
                    if(URLType.SONG.isMatch(id)){
                        try {
                            listID = Long.parseLong(Objects.requireNonNull(URLType.SONG.getMatch(id)));
                        } catch (NumberFormatException | NullPointerException e1) {
                            throw new RuntimeException("Illegal argument:", e1);
                        }
                    }else throw new RuntimeException("Illegal argument:", e);
                }

                NetEaseMusicList.PlayList data;
                try {
                    data = NetMusicUtil.getMusicListInfo(listID);
                    if(data == null) throw new RuntimeException();
                } catch (Exception e) {
                    throw new RuntimeException("解析失败：", e);
                }

                if (isStopped()) return;

                List<Music> musics;
                try {
                    musics = new ArrayList<>(NetMusicUtil.getMusicList(listID));
                } catch (Exception e) {
                    throw new RuntimeException("解析失败：", e);
                }

                if (isStopped()) return;

                setImportPlayList(String.valueOf(listID));
                setImportPlayListMusicCount(musics.size());
                setImportPlayListName(data.getName());
                setImportPlayListAuthor(data.getCreator().getNickname());
                setImportPlayListIco(new ImageInfo(ImageInfo.ImageType.URL, data.getCoverImgUrl()));

                if (isStopped()) return;
                mc.submit(() -> {
                    if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlock)
                        NetworkManager.sendToServer(IMPPackets.MULTIPLE_MUSIC_ADD, new IMPPackets.MultipleMusicAddMessage(musicManagerBlock.getSelectedPlayList(mc.player), musics, BlockEntityExistence.getByBlockEntity(getScreen().getBlockEntity())).toRFBB());
                    insMonitor(MusicManagerBlockEntity.MonitorType.PLAY_LIST);
                });
            } catch (Exception ex) {
                failureImportPlayList = true;
            }
        }

    }
}
