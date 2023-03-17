package ilib.world.saver;

import ilib.ImpLib;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/8/4 20:14
 */
public class WriteOnlySaveHandler extends SaveHandler implements ISaveHandler, IPlayerFileData {
	private final File worldDirectory;
	private final File playersDirectory;
	private final File mapDataDir;
	private final WorldInfo worldInfo;

	public static final int VERSION_1_12_2 = 19133;

	public WriteOnlySaveHandler(File worldDir, WorldInfo worldInfo) {
		super(worldDir, "", true, ChunkSavingProvider.DEAFULTFIXER);
		this.worldDirectory = worldDir;
		this.worldDirectory.mkdirs();

		this.playersDirectory = new File(this.worldDirectory, "playerdata");
		//this.playersDirectory.mkdirs();

		this.mapDataDir = new File(this.worldDirectory, "data");
		//this.mapDataDir.mkdirs();
		this.worldInfo = worldInfo;
	}

	private void setSessionLock() {
	}

	@Nonnull
	public File getWorldDirectory() {
		return this.worldDirectory;
	}

	public void checkSessionLock() {
	}

	@Nonnull
	public IChunkLoader getChunkLoader(@Nonnull WorldProvider par1) {
		throw new RuntimeException("Old Chunk Storage is no longer supported.");
	}

	@Nullable
	public WorldInfo loadWorldInfo() {
		return this.worldInfo;
	}

	@Override
	public void saveWorldInfoWithPlayer(@Nonnull WorldInfo info, @Nullable NBTTagCompound tag) {
		saveData0(info, tag, null);
	}

	protected void saveData0(WorldInfo info, NBTTagCompound tag, NBTTagCompound tag3) {
		NBTTagCompound tag2 = info.cloneNBTCompound(tag);
		//if(tag3 != null) {
		//    tag2.merge(tag3);
		//}
		tag2.setInteger("version", 19133);
		NBTTagCompound data = tag3 == null ? new NBTTagCompound() : tag3;
		data.setTag("Data", tag2);
		//FMLCommonHandler.instance().handleWorldDataSave(this, info, data);

		try {
			File f = new File(this.worldDirectory, "level.dat");
			File f2 = new File(this.worldDirectory, "level.dat_new");
			CompressedStreamTools.writeCompressed(data, new FileOutputStream(f2));
			if (f.exists()) {
				f.delete();
			}
			f2.renameTo(f);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void saveWorldInfo(@Nonnull WorldInfo info) {
		this.saveWorldInfoWithPlayer(info, null);
	}

	@Override
	public void writePlayerData(@Nonnull EntityPlayer player) {
		try {
			NBTTagCompound tag = player.writeToNBT(new NBTTagCompound());
			File file1 = new File(this.playersDirectory, player.getCachedUniqueIdString() + ".dat.tmp");
			File file2 = new File(this.playersDirectory, player.getCachedUniqueIdString() + ".dat");
			CompressedStreamTools.writeCompressed(tag, new FileOutputStream(file1));
			if (file2.exists()) {
				file2.delete();
			}

			file1.renameTo(file2);
			ForgeEventFactory.firePlayerSavingEvent(player, this.playersDirectory, player.getUniqueID().toString());
		} catch (Exception e) {
			ImpLib.logger().warn("Failed to save player data for {}", player.getName());
		}

	}

	@Nullable
	public NBTTagCompound readPlayerData(@Nonnull EntityPlayer player) {
		return null;
	}

	@Nonnull
	public IPlayerFileData getPlayerNBTManager() {
		return this;
	}

	@Nonnull
	public String[] getAvailablePlayerDat() {
		return new String[] {};
	}

	public void flush() {
	}

	@Nonnull
	public File getMapFileFromName(@Nonnull String s) {
		return new File(this.mapDataDir, s + ".dat");
	}

	@Nonnull
	public TemplateManager getStructureTemplateManager() {
		return null;
	}

	@Nonnull
	public NBTTagCompound getPlayerNBT(EntityPlayerMP p) {
		return null;
	}
}
