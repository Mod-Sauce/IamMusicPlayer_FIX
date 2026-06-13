package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.components.PlayListMusicsFixedListWidget;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.playlist.IMPPlaylistLoaders;
import dev.felnull.imp.client.music.playlist.IPlaylistLoader;
import dev.felnull.imp.client.music.playlist.IPlaylistResolver;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.otyacraftengine.client.util.OERenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public abstract class ImportPlayListBaseMMMonitor extends MusicManagerMonitor {
    private static final ResourceLocation IMPORT_PLAY_LIST_TEXTURE = new ResourceLocation(IamMusicPlayer.MODID, "textures/gui/container/music_manager/monitor/import_play_list.png");
    private static final Component BACK_TEXT = Component.translatable("gui.back");
    private static final Component LOADING_TEXT = Component.translatable("imp.text.playlistLoading");
    private List<PlayListEntry> playListEntries = new ArrayList<>();
    private List<Music> musics = new ArrayList<>();
    private SmartButton importButton;
    private EditBox playlistIdentifierEditBox;
    private PlayListMusicsFixedListWidget playListMusicsFixedButtonsList;
    private IPlaylistLoader playlistLoader;
    private IPlaylistResolver playlistLoaderThread;

    private ImageInfo importImageInfo = ImageInfo.EMPTY;

    public ImportPlayListBaseMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
        if(screen == null){
            playlistLoader = null;
            return;
        }
        this.playlistLoader = IMPPlaylistLoaders.getLoader(getScreen().playlistLoaderType);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        this.playlistLoader = IMPPlaylistLoaders.getLoader(getScreen().playlistLoaderType);

        addRenderWidget(new SmartButton(getStartX() + 5, getStartY() + 180, 87, 15, BACK_TEXT, n -> {
            if (getParentType() != null)
                insMonitor(getParentType());
            resetImport();
        }));

        this.importButton = addRenderWidget(new SmartButton(getStartX() + 95, getStartY() + 180, 87, 15, CreatePlayListMMMonitor.IMPORT_TEXT, n -> onImport()));
        this.importButton.active = canImport();

        this.playlistIdentifierEditBox = addRenderWidget(new EditBox(mc.font, getStartX() + 6, getStartY() + 164, 175, 12,
                playlistLoader == null ? Component.empty() : Component.translatable(String.format("imp.editBox.%sPlaylistIdentifier", playlistLoader.getID()))){
            @Override
            public boolean keyPressed(int i, int j, int k) {
                if (this.isActive() && this.isFocused() && Screen.isPaste(i)) {
                    var text = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if(playlistLoader == null){
                        insertText(text);
                        return true;
                    }
                    var auto = playlistLoader.autoPasteFromClipboard(text);
                    if(auto.isPresent()) {
                        insertText(auto.get());
                    }else{
                        insertText(text);
                    }
                    return true;
                }
                return super.keyPressed(i, j, k);
            }
        });
        this.playlistIdentifierEditBox.setMaxLength(300);
        this.playlistIdentifierEditBox.setResponder(this::startPlayListLoad);
        this.playlistIdentifierEditBox.setValue(getImportPlayList());

        this.playListMusicsFixedButtonsList = addRenderWidget(new PlayListMusicsFixedListWidget(getStartX() + 1, getStartY() + 10, 368, 148,
                Component.translatable(String.format("imp.fixedList.%sPlayListMusics", playlistLoader == null ? "" : playlistLoader.getID())),
                4, playListEntries, this.playListMusicsFixedButtonsList));

        if(tryAutoFillID() && playlistLoader != null)
            startPlayListLoad(playlistIdentifierEditBox.getValue());
    }

    private boolean tryAutoFillID(){
        if(playlistLoader == null)return false;
        var text = Minecraft.getInstance().keyboardHandler.getClipboard();
        var r = playlistLoader.autoPasteFromClipboard(text);
        if(r.isPresent()) {
            playlistIdentifierEditBox.setValue(r.get());
            return true;
        }
        return false;
    }

    abstract protected void onImport();

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        OERenderUtils.drawTexture(IMPORT_PLAY_LIST_TEXTURE, guiGraphics.pose(), getStartX(), getStartY(), 0f, 0f, width, height, width, height);
        if (isPlayListLoading()) {
            drawSmartText(guiGraphics, LOADING_TEXT, getStartX() + 2, getStartY() + 11);
        }

        drawSmartText(guiGraphics, Component.literal(getImportPlayListName()), getStartX() + 200, getStartY() + 167);
        drawSmartText(guiGraphics, Component.literal(getImportPlayListAuthor()), getStartX() + 200, getStartY() + 183);
    }

    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;
        OERenderUtils.renderTextureSprite(IMPORT_PLAY_LIST_TEXTURE, poseStack, multiBufferSource, 0, 0, OERenderUtils.MIN_BREADTH * 2, 0, 0, 0, monitorWidth, monitorHeight, 0, 0, width, height, width, height, i, j);

        renderSmartButtonSprite(poseStack, multiBufferSource, 5, 180, OERenderUtils.MIN_BREADTH * 4, 87, 15, i, j, onPxW, onPxH, monitorHeight, BACK_TEXT, true);
        renderSmartButtonSprite(poseStack, multiBufferSource, 95, 180, OERenderUtils.MIN_BREADTH * 4, 87, 15, i, j, onPxW, onPxH, monitorHeight, CreatePlayListMMMonitor.IMPORT_TEXT, true, !canImport(blockEntity));

        renderSmartEditBoxSprite(poseStack, multiBufferSource, 6, 164, OERenderUtils.MIN_BREADTH * 4, 175, 12, i, j, onPxW, onPxH, monitorHeight, getImportPlayList(blockEntity));

        renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(getImportPlayListName(blockEntity)), 200, 167, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);
        renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(getImportPlayListAuthor(blockEntity)), 200, 183, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);

        renderScrollbarSprite(poseStack, multiBufferSource, 360, 10, OERenderUtils.MIN_BREADTH * 2, 148, i, j, onPxW, onPxH, monitorHeight, 1, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if(playlistLoader == null) {
            insMonitor(getParentType() == null ? MusicManagerBlockEntity.MonitorType.PLAY_LIST : getParentType());
            return;
        }
        if(playlistLoaderThread != null && !playlistLoaderThread.isAlive()){
            if(playlistLoaderThread.isFailure())
                resetImport();
            else{
                var r = playlistLoaderThread.getResult();
                if(r == null)resetImport();
                else {
                    setImportPlayList(r.id());
                    setImportPlayListAuthor(r.author());
                    setImportPlayListName(r.name());
                    setImportPlayListMusicCount(r.count());
                    setImportPlayListIco(r.ico());
                    playListEntries.clear();
                    playListEntries.addAll(r.musicEntries());
                    musics.clear();
                    musics.addAll(r.musics());
                }
            }
        }
        this.importButton.active = canImport();
    }

    protected int getImportPlayListMusicCount() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getImportPlayListMusicCount(musicManagerBlockEntity);
        return 0;
    }

    protected int getImportPlayListMusicCount(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getImportPlayListMusicCount(mc.player);
    }


    protected String getImportPlayListAuthor() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getImportPlayListAuthor(musicManagerBlockEntity);
        return "";
    }

    protected String getImportPlayListAuthor(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getImportPlayListAuthor(mc.player);
    }

    protected String getImportPlayList() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getImportPlayList(musicManagerBlockEntity);
        return "";
    }

    protected String getImportPlayList(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getImportIdentifier(mc.player);
    }

    protected String getImportPlayListName() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getImportPlayListName(musicManagerBlockEntity);
        return "";
    }

    protected String getImportPlayListName(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getImportPlayListName(mc.player);
    }

    protected boolean canImport() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return canImport(musicManagerBlockEntity);
        return false;
    }

    protected boolean canImport(MusicManagerBlockEntity blockEntity) {
        return !getImportPlayList(blockEntity).isEmpty() && getImportPlayListMusicCount(blockEntity) > 0;
    }

    protected boolean isPlayListLoading() {
        return playlistLoaderThread != null && playlistLoaderThread.isAlive();
    }

    protected void setImportPlayListAuthor(String author) {
        getScreen().insImportPlayListAuthor(author);
    }

    protected void setImportPlayListMusicCount(int count) {
        getScreen().insImportPlayListMusicCount(count);
    }

    protected void setImportPlayListName(String name) {
        getScreen().insImportPlayListName(name);
    }

    protected void setImportPlayList(String id) {
        getScreen().insImportIdentifier(id);
    }

    protected void setImportPlayListIco(ImageInfo imageInfo){
        this.importImageInfo = imageInfo;
    }

    public ImageInfo getImportImageInfo() {
        return importImageInfo;
    }

    @Override
    protected void onBackParent() {
        super.onBackParent();
        resetImport();
    }

    protected void resetImport() {
        setImportPlayList("");
        setImportPlayListAuthor("");
        setImportPlayListName("");
        setImportPlayListMusicCount(0);
        playlistLoaderThread = null;
    }

    protected void startPlayListLoad(String id) {
        if(playlistLoader == null)return;
        stopPlayListLoad();
        playListEntries.clear();
        musics.clear();
        resetImport();
        playlistLoaderThread = playlistLoader.getResolver(id);
        playlistLoaderThread.start();
    }

    protected void stopPlayListLoad() {
        if (playlistLoaderThread != null) {
            playlistLoaderThread.stopped();
            playlistLoaderThread = null;
        }
    }

    public record PlayListEntry(String name, String artist, MusicSource source, ImageInfo imageInfo) {
    }

    public IPlaylistLoader getPlaylistLoader() {
        return playlistLoader;
    }

    public List<Music> getMusics() {
        return musics;
    }
}
