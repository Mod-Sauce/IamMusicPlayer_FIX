package dev.felnull.imp.create.client.screen;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.create.block_entity.BoomboxControllerBlockEntity;
import dev.felnull.imp.create.block_entity.data.BoomboxControllerData;
import dev.felnull.imp.create.client.menu.BoomboxControllerMenu;
import dev.felnull.imp.create.client.screen.widget.FitterSlotWidget;
import dev.felnull.imp.create.client.screen.widget.FunctionSelectWidget;
import dev.felnull.imp.create.client.screen.widget.SignalList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.modsauce.otyacraftenginerenewed.client.gui.screen.OEBEContainerBasedScreen;

import java.util.UUID;

public class BoomboxControllerScreen extends OEBEContainerBasedScreen<BoomboxControllerMenu> {
    public static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/boombox_controller/boombox_controller_base.png");
    public static final Component EGG_TITLE = Component.translatable("block.iammusicplayer.boombox_controller.egg");
    public static final UUID GLY = UUID.fromString("91bd580f-5f17-4e30-872f-2e480dd9a220");

    public SignalList signalList;
    public AbstractButton removeButton;
    public AbstractButton addButton;
    public FitterSlotWidget fitterSlotLeft;
    public FitterSlotWidget fitterSlotRight;
    public FunctionSelectWidget functionSelectWidget;

    private BoomboxControllerData data;

    private boolean isEgg = false;
    public BoomboxControllerScreen(BoomboxControllerMenu abstractContainerMenu, Inventory inventory, Component component) {
        super(abstractContainerMenu, inventory, component);
        imageWidth = 380;
        imageHeight = 176;
        bgTextureWidth = 512;
        bgTextureHeight = 256;
        titleLabelX = 213;
        inventoryLabelX = 213;
        inventoryLabelY += 10;

        if(Minecraft.getInstance().getGameProfile().getId().equals(GLY))
            isEgg = true;
    }

    @Override
    protected void init() {
        super.init();

        if(getBlockEntity() instanceof BoomboxControllerBlockEntity blockEntity)
            data = blockEntity.getBoomboxControllerData();

        signalList = new SignalList(leftPos + 6, topPos + 6, 202, 164, this::onSelect);
        signalList.setData(data);
        addRenderableWidget(signalList);
        removeButton = Button.builder(Component.translatable("imp.text.boombox_controller.delete"), p -> remove())
                .pos(leftPos + 216 + 18 + 5, topPos + 47)
                .size(130 / 2, 20)
                .build();
        addButton = Button.builder(Component.translatable("imp.text.boombox_controller.add"), p -> add())
                .pos(leftPos + 216 + 18 + 5 + 130 / 2, topPos + 47)
                .size(130 / 2, 20)
                .build();
        fitterSlotLeft = new FitterSlotWidget(leftPos + 216, topPos + 50 - 18, this::edit);
        fitterSlotLeft.setFGColor(0X55FF0000);
        fitterSlotRight = new FitterSlotWidget(leftPos + 216, topPos + 50, this::edit);
        fitterSlotRight.setFGColor(0X550000FF);
        functionSelectWidget = new FunctionSelectWidget(leftPos + 216 + 18 + 5, topPos + 50 - 18, 130, 13, BoomboxControllerData.Mode.NONE, this::edit);
        addRenderableWidget(removeButton);
        addRenderableWidget(addButton);
        addRenderableWidget(fitterSlotLeft);
        addRenderableWidget(fitterSlotRight);
        addRenderableWidget(functionSelectWidget);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        var selected = signalList.getSelected() != null;
        removeButton.active = selected;
        fitterSlotLeft.visible = selected;
        fitterSlotRight.visible = selected;
        functionSelectWidget.visible = selected;
        super.render(guiGraphics, i, j, f);
    }

    @Override
    protected ResourceLocation getBackGrandTexture() {
        return BG_TEXTURE;
    }

    @Override
    protected void renderLabels(GuiGraphics arg, int i, int j) {
        arg.drawString(this.font, isEgg ? EGG_TITLE : title, this.titleLabelX, this.titleLabelY, 4210752, false);
        arg.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    @Override
    public void onInstructionReturn(String name, CompoundTag data) {
        super.onInstructionReturn(name, data);
//        if(getBlockEntity() instanceof BoomboxControllerBlockEntity blockEntity)
//            this.data = blockEntity.getBoomboxControllerData();
//        signalList.setData(this.data);
    }

    public void remove(){
        var data = new CompoundTag();
        data.putInt("index", signalList.getSelectedIndex());
        instruction("remove", data);
        if(getBlockEntity() instanceof BoomboxControllerBlockEntity blockEntity) {
            blockEntity.removeSignal(signalList.getSelectedIndex());
            this.data = blockEntity.getBoomboxControllerData();
            signalList.setData(this.data);
        }
    }

    public void add(){
        instruction("add", new CompoundTag());
        if(getBlockEntity() instanceof BoomboxControllerBlockEntity blockEntity) {
            blockEntity.addSignal();
            this.data = blockEntity.getBoomboxControllerData();
            signalList.setData(this.data);
        }
    }

    public void edit(){
        if(signalList.getSelected() == null)return;
        var index = signalList.getSelectedIndex();
        var data = new CompoundTag();
        var s = new BoomboxControllerData.Signal(
                fitterSlotLeft.getItemStack(),
                fitterSlotRight.getItemStack(),
                functionSelectWidget.getMode()
        );
        data.putInt("index", index);
        BoomboxControllerData.Signal.saveToNbt(data, s, "signal");
        instruction("edit", data);
        if(getBlockEntity() instanceof BoomboxControllerBlockEntity blockEntity) {
            blockEntity.editSignal(index, s);
            this.data = blockEntity.getBoomboxControllerData();
            signalList.setData(this.data);
        }
    }

    public void onSelect(BoomboxControllerData.Signal signal){
        fitterSlotLeft.setItemStack(signal.left());
        fitterSlotRight.setItemStack(signal.right());
        functionSelectWidget.setModeWithoutUpdate(signal.mode());
    }
}
