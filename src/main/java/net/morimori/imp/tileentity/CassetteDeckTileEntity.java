package net.morimori.imp.tileentity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.LockableTileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.PacketDistributor;
import net.morimori.imp.IamMusicPlayer;
import net.morimori.imp.block.CassetteDeckBlock;
import net.morimori.imp.block.CassetteDeckStates;
import net.morimori.imp.container.CassetteDeckContainer;
import net.morimori.imp.file.PlayList;
import net.morimori.imp.packet.CassetteDeckSyncMessage;
import net.morimori.imp.packet.PacketHandler;
import net.morimori.imp.sound.ISoundPlayer;
import net.morimori.imp.sound.SoundPlayer;
import net.morimori.imp.sound.SoundPos;
import net.morimori.imp.sound.SoundWaitThread;
import net.morimori.imp.sound.WorldPlayListSoundData;
import net.morimori.imp.util.ItemHelper;
import net.morimori.imp.util.PlayerHelper;
import net.morimori.imp.util.SoundHelper;

public class CassetteDeckTileEntity extends LockableTileEntity implements ITickableTileEntity, ISoundPlayer {

	protected NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
	public int rotationPitch;//縦方向最大90度
	public int rotationYaw;//横方向最大360度
	private boolean inversionPitch;
	public Map<String, String> playerstager = new HashMap<String, String>();
	public Set<String> lisnFinishedPlayers = new HashSet<String>();
	public int recodingPrograse;
	public int copyingPrograse;
	public int deletingPrograse;

	private String foldername;
	private String filename;

	public CassetteDeckTileEntity() {
		super(IMPTileEntityTypes.CASSETTE_DECK);
		foldername = "null";
		filename = "null";

	}

	public ItemStack getPlayCassette() {
		return this.getWriteCassette();
	}

	public ItemStack getCopyingCassete() {
		return this.getItems().get(2);
	}

	public String getFolderName() {

		return this.foldername.isEmpty() ? "null" : this.foldername;
	}

	public String getFileName() {

		return this.filename.isEmpty() ? "null" : this.filename;
	}

	public void setFolderNameAndFileName(String folder, String name) {
		this.foldername = folder;
		this.filename = name;
	}

	public void finishRecoding() {

		if (SoundHelper.canWriteCassette(this.getWriteCassette())) {

			if (PlayList.getWorldPlaylistNBTDataString(
					this.getWorld().getServer(), this.foldername, this.filename, "UUID") != null
					&& PlayList.getWorldPlaylistNBTDataSoundData(
							this.getWorld().getServer(), this.foldername, this.filename, "SoundData") != null) {
				setWriteCassette(SoundHelper.writeSoundOnCassette(this.getWriteCassette(),
						new WorldPlayListSoundData(this.filename, this.foldername, PlayList.getWorldPlaylistNBTDataString(
								this.getWorld().getServer(), this.foldername, this.filename, "UUID"),
								PlayList.getWorldPlaylistNBTDataSoundData(
										this.getWorld().getServer(), this.foldername, this.filename, "SoundData"))));
			}

		}
		recodingPrograse = 0;
		world.setBlockState(this.pos,
				this.getBlockState().with(CassetteDeckBlock.CASSETTE_DECK_STATES,
						CassetteDeckStates.NONE));
	}

	public void finishCopying() {

		if (SoundHelper.canWriteCassette(this.getWriteCassette()) && SoundHelper.isWritedSound(getCopyingCassete())) {
			setWriteCassette(SoundHelper.writeSoundOnCassette(this.getWriteCassette(),
					WorldPlayListSoundData.getWorldPlayListData(getCopyingCassete())));
		}
		copyingPrograse = 0;
		world.setBlockState(this.pos,
				this.getBlockState().with(CassetteDeckBlock.CASSETTE_DECK_STATES,
						CassetteDeckStates.NONE));
	}

	public void finishDeleting() {

		if (ItemHelper.isCassette(getWriteCassette()) && SoundHelper.isWritedSound(getWriteCassette())) {
			setWriteCassette(SoundHelper.deleteSound(getWriteCassette()));
		}
		deletingPrograse = 0;
		world.setBlockState(this.pos,
				this.getBlockState().with(CassetteDeckBlock.CASSETTE_DECK_STATES,
						CassetteDeckStates.NONE));
	}

	public int getRecodingTotalTime() {
		return 200;
	}

	public int getCoppyTotalTime() {
		return 100;
	}

	public int getDeleteTotalTime() {
		return 150;
	}

	@Override
	public void tick() {
		if (!world.isRemote) {

			if (this.getBlockState().get(CassetteDeckBlock.CASSETTE_DECK_STATES) == CassetteDeckStates.DELETE) {

				if (ItemHelper.isCassette(getWriteCassette()) && SoundHelper.isWritedSound(getWriteCassette())) {
					if (deletingPrograse < getDeleteTotalTime()) {
						deletingPrograse++;
					}
					if (deletingPrograse == getDeleteTotalTime()) {
						finishDeleting();
					}
				}

			} else {
				deletingPrograse = 0;
			}

			if (this.getBlockState().get(CassetteDeckBlock.CASSETTE_DECK_STATES) == CassetteDeckStates.COPY) {
				if (SoundHelper.canWriteCassette(getWriteCassette()) && SoundHelper.isWritedSound(getCopyingCassete())
						&& !WorldPlayListSoundData.getWorldPlayListData(getWriteCassette())
								.equals(WorldPlayListSoundData.getWorldPlayListData(getCopyingCassete()))) {
					if (copyingPrograse < getCoppyTotalTime()) {
						copyingPrograse++;
					}
					if (copyingPrograse == getCoppyTotalTime()) {
						finishCopying();
					}
				} else {
					copyingPrograse = 0;
					world.setBlockState(this.pos,
							this.getBlockState().with(CassetteDeckBlock.CASSETTE_DECK_STATES,
									CassetteDeckStates.NONE));
				}
			} else {
				copyingPrograse = 0;
			}
			if (this.getBlockState().get(CassetteDeckBlock.CASSETTE_DECK_STATES) == CassetteDeckStates.RECORD) {
				if (SoundHelper.canWriteCassette(getWriteCassette())) {
					if (recodingPrograse < getRecodingTotalTime()) {
						recodingPrograse++;
					}
					if (recodingPrograse == getRecodingTotalTime()) {
						finishRecoding();
					}
				} else {
					recodingPrograse = 0;
					world.setBlockState(this.pos,
							this.getBlockState().with(CassetteDeckBlock.CASSETTE_DECK_STATES,
									CassetteDeckStates.NONE));
				}
				if (getAntenna().isEmpty() && this.foldername.isEmpty() && this.filename.isEmpty()) {
					world.setBlockState(this.pos,
							this.getBlockState().with(CassetteDeckBlock.CASSETTE_DECK_STATES,
									CassetteDeckStates.NONE));
				}
			} else {
				recodingPrograse = 0;
			}

			if (!getWriteCassette().isEmpty()) {
				world.setBlockState(this.pos,
						this.getBlockState().with(CassetteDeckBlock.ON, true));
			} else {
				world.setBlockState(this.pos,
						this.getBlockState().with(CassetteDeckBlock.ON, false));
			}

			if (!this.getBlockState().get(CassetteDeckBlock.ON)) {
				world.setBlockState(this.pos,
						this.getBlockState().with(CassetteDeckBlock.CASSETTE_DECK_STATES, CassetteDeckStates.NONE));

			} else {
				if (ItemHelper.isAntenna(getAntenna())) {
					this.rotationYaw += 2;
					while (this.rotationYaw > 360) {
						this.rotationYaw -= 360;
					}
					if (!inversionPitch) {
						if (50 >= rotationPitch) {
							this.rotationPitch += 2;
						} else {
							this.inversionPitch = true;
						}
					} else {
						if (-50 <= rotationPitch) {
							this.rotationPitch -= 2;
						} else {
							this.inversionPitch = false;
						}
					}
				}
			}

			if (isSoundStop()) {
				lisnFinishedPlayers.clear();
			}

			sendClientSyncPacket();
		} else {
			playSound();
		}

	}

	public NonNullList<ItemStack> getItems() {
		return this.items;
	}

	public ItemStack getAntenna() {
		return this.getItems().get(0);
	}

	public void setAntenna(ItemStack item) {
		this.setItem(0, item);
	}

	public ItemStack getWriteCassette() {
		return this.getItems().get(1);
	}

	public void setWriteCassette(ItemStack item) {
		this.setItem(1, item);
	}

	public void setItem(int id, ItemStack item) {
		this.items.set(id, item);
		this.markDirty();
	}

	@Override
	public void read(CompoundNBT tag) {
		super.read(tag);
		this.rotationPitch = tag.getInt("RotationPitch");
		this.rotationYaw = tag.getInt("RotationYaw");
		this.inversionPitch = tag.getBoolean("InversionPitch");
		this.foldername = tag.getString("SelectedFolder");
		this.filename = tag.getString("SelectedFile");

		this.recodingPrograse = tag.getInt("RecodingPrograse");
		this.copyingPrograse = tag.getInt("CopyingPrograse");
		this.deletingPrograse = tag.getInt("DeletingPrograse");

		ItemStackHelper.loadAllItems(tag, items);

		CompoundNBT ptmnbt = tag.getCompound("PlayersTager");
		for (String key : ptmnbt.keySet()) {
			playerstager.put(key, ptmnbt.getString(key));
		}

		CompoundNBT pfmnbt = tag.getCompound("LisnFinishedPlayers");
		lisnFinishedPlayers.clear();
		for (String key : pfmnbt.keySet()) {
			lisnFinishedPlayers.add(pfmnbt.getString(key));
		}
	}

	@Override
	public CompoundNBT write(CompoundNBT tag) {
		super.write(tag);
		tag.putInt("RotationPitch", this.rotationPitch);
		tag.putInt("RotationYaw", this.rotationYaw);
		tag.putBoolean("InversionPitch", this.inversionPitch);
		tag.putString("SelectedFolder", this.foldername);
		tag.putString("SelectedFile", this.filename);

		tag.putInt("RecodingPrograse", this.recodingPrograse);
		tag.putInt("CopyingPrograse", this.copyingPrograse);
		tag.putInt("DeletingPrograse", this.deletingPrograse);

		CompoundNBT ptmnbt = new CompoundNBT();
		for (Entry<String, String> ent : playerstager.entrySet()) {
			ptmnbt.putString(ent.getKey(), ent.getValue());
		}
		tag.put("PlayersTager", ptmnbt);

		CompoundNBT tag2 = ItemStackHelper.saveAllItems(tag, items);

		CompoundNBT ptamnbt = new CompoundNBT();
		int cont = 0;
		for (String uuid : lisnFinishedPlayers) {
			ptamnbt.putString(String.valueOf(cont), uuid);
			cont++;
		}
		tag.put("LisnFinishedPlayers", ptamnbt);

		return tag2;
	}

	@Override
	public int getSizeInventory() {
		return 3;
	}

	@Override
	public boolean isEmpty() {

		return this.getItems().stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStackInSlot(int var1) {

		return this.getItems().get(var1);
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {

		if (index == 0)
			return ItemHelper.isAntenna(stack);

		if (index == 1)
			return ItemHelper.isCassette(stack);

		return true;
	}

	@Override
	public ItemStack decrStackSize(int index, int count) {

		return ItemStackHelper.getAndSplit(this.items, index, count);
	}

	@Override
	public boolean isUsableByPlayer(PlayerEntity pl) {

		if (this.world.getTileEntity(this.pos) != this) {
			return false;
		} else {
			return pl.getDistanceSq((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D,
					(double) this.pos.getZ() + 0.5D) <= 64.0D;
		}
	}

	@Override
	public ItemStack removeStackFromSlot(int index) {
		return ItemStackHelper.getAndRemove(this.items, index);
	}

	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
		ItemStack itemstack = this.items.get(index);
		boolean flag = !stack.isEmpty() && stack.isItemEqual(itemstack)
				&& ItemStack.areItemStackTagsEqual(stack, itemstack);
		this.items.set(index, stack);
		if (stack.getCount() > this.getInventoryStackLimit()) {
			stack.setCount(this.getInventoryStackLimit());
		}
		if (flag)
			this.markDirty();
	}

	@Override
	protected ITextComponent getDefaultName() {

		return new TranslationTextComponent("block.iammusicplayer.cassette_deck");
	}

	@Override
	protected Container createMenu(int id, PlayerInventory player) {
		return new CassetteDeckContainer(id, player, this, this.getPos());
	}

	@Override
	public void clear() {
		items.clear();
	}

	public void sendClientSyncPacket() {
		Chunk ch = (Chunk) this.world.getChunk(pos);
		PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> ch),
				new CassetteDeckSyncMessage(this.world.dimension.getDimension().getType().getId(), this.pos,
						this.items, this.rotationPitch, this.rotationYaw, this.foldername, this.filename,
						this.playerstager, this.recodingPrograse, this.lisnFinishedPlayers, this.copyingPrograse,
						this.deletingPrograse));
	}

	public void clientSync(CassetteDeckSyncMessage message) {
		this.items = message.items;
		this.rotationPitch = message.pitch;
		this.rotationYaw = message.yaw;
		setFolderNameAndFileName(message.selectedfolder, message.selectfile);

		this.playerstager = message.playerstager;
		this.recodingPrograse = message.recordingPrograse;
		this.lisnFinishedPlayers = message.lisnFinishedPlayers;
		this.copyingPrograse = message.copyingPrograse;
		this.deletingPrograse = message.deletingPrograse;
	}

	@Override
	public boolean isSoundStop() {

		return !(SoundHelper.canPlay(getWriteCassette())
				&& this.getBlockState().get(CassetteDeckBlock.CASSETTE_DECK_STATES) == CassetteDeckStates.PLAY);
	}

	@Override
	public boolean isLoopPlay() {

		return this.world.isBlockPowered(this.pos);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void playSound() {
		if (world.isRemote) {
			if (SoundHelper.canPlay(getWriteCassette())
					&& this.getBlockState().get(CassetteDeckBlock.CASSETTE_DECK_STATES) == CassetteDeckStates.PLAY) {
				net.minecraft.client.Minecraft mc = IamMusicPlayer.proxy.getMinecraft();
				if (!lisnFinishedPlayers.contains(PlayerHelper.getUUID(mc.player))) {
					if (new SoundPos(this.pos).canLisn(15)) {
						if (!SoundWaitThread.posplayMap.containsKey(this.pos)) {
							SoundWaitThread.posplayMap.put(this.pos,
									new SoundPlayer(WorldPlayListSoundData.getWorldPlayListData(getWriteCassette()),
											new SoundPos(this.pos), 1, 15));
						}
					}
				}
			}
		}
	}
}
