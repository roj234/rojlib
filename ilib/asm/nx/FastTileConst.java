package ilib.asm.nx;

import ilib.Config;
import ilib.ImpLib;
import ilib.asm.Loader;
import ilib.asm.util.ICreator;
import ilib.misc.MCHooks;
import ilib.util.freeze.FreezedTileEntity;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashMap;
import roj.collect.ToIntMap;
import roj.reflect.DirectAccessor;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.FMLLog;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

/**
 * @author Roj234
 * @since 2020/8/20 17:56
 */
@Nixim("/")
abstract class FastTileConst extends TileEntity {
	@Copy
	static Map<String, ICreator> tileEntityCreator;
	@Copy(staticInitializer = "initTC")
	static RandomAccessFile tileCache;

	static void initTC() {
		tileEntityCreator = new MyHashMap<>();
		try {
			tileCache = new RandomAccessFile("Implib_FTC.bin", "rw");
			ToIntMap<String> map = new ToIntMap<>();
			ICreator creator = (ICreator) MCHooks.batchGenerate(tileCache, false, map);
			if (creator != null) {
				for (ToIntMap.Entry<String> entry : map.selfEntrySet()) {
					ICreator c = (ICreator) creator.clone();
					c.setId(entry.v);
					tileEntityCreator.put(entry.k, c);
				}
				Loader.logger.info("使用BatchGen节省了 " + map.size() + " 个无用的class");
			}
			tileCache.seek(0);
			tileCache.writeInt(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Shadow
	private static RegistryNamespaced<ResourceLocation, Class<? extends TileEntity>> REGISTRY;

	@Inject
	public static void register(String id, Class<? extends TileEntity> clazz) {
		if (tileCache != null) {
			try {
				MCHooks.batchAdd(tileCache, id, clazz);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		REGISTRY.putObject(new ResourceLocation(id), clazz);
	}

	@Inject
	public static TileEntity create(World worldIn, NBTTagCompound tag) {
		String id = tag.getString("id");

		if (Config.disableTileEntities.contains(id)) {
			ImpLib.logger().warn("已根据配置文件 *删除* 了位于 " + tag.getInteger("x") + ',' + tag.getInteger("y") + ',' + tag.getInteger("z") + " 的实体的所有数据!");
			return null;
		}

		TileEntity tile = null;
		try {
			ICreator supplier = tileEntityCreator.get(id);
			if (supplier == null) {
				Class<? extends TileEntity> tileCz = REGISTRY.getObject(new ResourceLocation(id));
				if (tileCz != null) {
					tileEntityCreator.put(id, supplier = DirectAccessor.builder(ICreator.class).construct(tileCz, "get").build());
				}
			}
			if (supplier != null) {
				tile = supplier.get();
				if (tile == null) throw new InternalError(supplier.toString());
			} else {
				if (Config.freezeUnknownEntries.contains("tile")) {
					FMLLog.log.debug("冻结不存在的Tile {}", id);
					return new FreezedTileEntity(tag);
				}
			}
		} catch (Throwable var7) {
			FMLLog.log.error("一个 Tile {}({}) 无法加载NBT数据, 这是一个bug!", id, getTEClass(id), var7);
		}

		if (tile != null) {
			try {
				tile.setWorldCreate(worldIn);
				tile.readFromNBT(tag);
			} catch (Throwable var6) {
				FMLLog.log.error("一个 Tile {}({}) 无法加载NBT数据, 这是一个bug!", id, getTEClass(id), var6);
				tile = null;
			}
		} else {
			FMLLog.log.warn("跳过不存在的Tile {}", id);
		}

		return tile;
	}

	@Copy
	private static String getTEClass(String id) {
		Class<? extends TileEntity> clazz = REGISTRY.getObject(new ResourceLocation(id));
		return clazz == null ? "null" : clazz.getName();
	}
}
