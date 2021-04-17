package ilib.world.schematic;

import ilib.ImpLib;
import ilib.util.BlockHelper;
import ilib.util.NBTType;
import ilib.util.Registries;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.util.ByteList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class SchematicLoader {
	private final File file;
	private Schematic schematic;

	public SchematicLoader(File file) {
		this.file = file;
	}

	public Schematic getSchematic() {
		if (schematic == null) {
			schematic = load(file);
			schematic.name = file.getName();
		}
		return schematic;
	}

	public String getName() {
		return schematic == null ? file.getName() : schematic.name;
	}

	public static Schematic load(File file) {
		try {
			return load(new FileInputStream(file));
		} catch (IOException e) {
			e.printStackTrace();
			ImpLib.logger().fatal("Error loading schematic at: " + file);
			return null;
		}
	}

	public static Schematic load(String path) {
		InputStream in = SchematicLoader.class.getResourceAsStream(path);
		if (in == null) return null;
		return load(in);
	}

	public static Schematic load(InputStream in) {
		try {
			NBTTagCompound tag = CompressedStreamTools.readCompressed(in);
			return deserialize(tag);
		} catch (Throwable e) {
			ImpLib.logger().error("Error loading schematic", e);
			return null;
		} finally {
			try {
				in.close();
			} catch (IOException ignored) {}
		}
	}

	public static Schematic deserialize(NBTTagCompound nbt) {
		NBTTagList tiles = nbt.getTagList("TileEntities", 10);
		NBTTagList entities = nbt.hasKey("Entities", 10) ? nbt.getTagList("Entities", 10) : null;

		int width = nbt.getShort("Width") & 0xFFFF;
		int height = nbt.getShort("Height") & 0xFFFF;
		int length = nbt.getShort("Length") & 0xFFFF;

		if (nbt.hasKey("Blocks", NBTType.INT_ARRAY)) {
			return readLegacy(nbt);
		}
		IBlockState[] blocks = new IBlockState[width * height * length];

		NBTTagList palette = nbt.getTagList("BlockData", NBTType.COMPOUND);

		SimpleList<IBlockState> map = new SimpleList<>(palette.tagCount() + 1);
		map.add(BlockHelper.AIR_STATE);

		for (int i = 0; i < palette.tagCount(); ++i) {
			NBTTagCompound tag = palette.getCompoundTagAt(i);
			if (tag.getSize() == 0) {
				map.add(null);
			} else {
				Block b = Registries.block().getValue(new ResourceLocation(tag.getString("n"), tag.getString("p")));
				// noinspection all
				map.add(b.getStateFromMeta(tag.getInteger("m")));
			}
		}

		ByteList blockArray = new ByteList(nbt.getByteArray("Blocks"));
		for (int i = 0; i < blocks.length; ++i) {
			blocks[i] = map.get(blockArray.readVarInt(false));
		}

		return new Schematic(entities, tiles, (short) width, (short) height, (short) length, blocks);
	}

	private static Schematic readLegacy(NBTTagCompound nbt) {
		NBTTagList tiles = nbt.getTagList("TileEntities", 10);

		int width = nbt.getShort("Width") & 0xFFFF;
		int height = nbt.getShort("Height") & 0xFFFF;
		int length = nbt.getShort("Length") & 0xFFFF;

		int[] blockArray = nbt.getIntArray("Blocks");

		IBlockState[] blocks = new IBlockState[width * height * length];
		byte[] metadata = nbt.getByteArray("Data");

		IntMap<Block> map = new IntMap<>();
		map.putInt(-1, Blocks.AIR);

		NBTTagList palette = nbt.getTagList("BlockData", NBTType.COMPOUND);
		for (int i = 0; i < palette.tagCount(); ++i) {
			NBTTagCompound tag = palette.getCompoundTagAt(i);
			map.putInt(tag.getInteger("p"), Registries.block().getValue(new ResourceLocation(tag.getString("m"), tag.getString("b"))));
		}

		for (int i = 0; i < blockArray.length; ++i) {
			Block block = map.get(blockArray[i]);
			blocks[i] = block == null ? null : block.getStateFromMeta(metadata[i]);
		}

		return new Schematic(null, tiles, (short) width, (short) height, (short) length, blocks);
	}

	public static NBTTagCompound write(World w, BlockPos pos, BlockPos length, boolean entities) {
		SchematicToWrite s = new SchematicToWrite((short) length.getX(), (short) length.getY(), (short) length.getZ());
		s.readFrom(w, pos, entities);
		return serialize(s);
	}

	public static NBTTagCompound serialize(SchematicToWrite s) {
		NBTTagCompound nbt = new NBTTagCompound();

		nbt.setShort("Width", (short) s.width());
		nbt.setShort("Height", (short) s.height());
		nbt.setShort("Length", (short) s.length());

		if (s.tileTags.tagCount() > 0) nbt.setTag("TileEntities", s.tileTags);
		if (s.entityTags.tagCount() > 0) nbt.setTag("Entities", s.entityTags);

		ByteList blockIds = IOUtil.getSharedByteBuf();
		IBlockState[] blocks = s.blocks;
		blockIds.ensureCapacity(blocks.length);

		NBTTagList palette = new NBTTagList();
		ToIntMap<IBlockState> map = new ToIntMap<>();

		for (IBlockState state : blocks) {
			if (state.getMaterial() != Material.AIR) {
				int meta = state.getBlock().getMetaFromState(state);
				state = state.getBlock().getStateFromMeta(meta);
				if (!map.containsKey(state)) {
					ResourceLocation rl = Registries.block().getKey(state.getBlock());
					if (rl == null) {
						blockIds.put((byte) 0);
						continue;
					}

					map.putInt(map.size() + 1, state);

					NBTTagCompound tag = new NBTTagCompound();
					if (state.getMaterial() != Material.STRUCTURE_VOID) {
						tag.setString("n", rl.getNamespace());
						tag.setString("p", rl.getPath());
						if ((byte) meta == meta) {
							tag.setByte("m", (byte) meta);
						} else {
							tag.setInteger("m", meta);
						}
					}
					palette.appendTag(tag);
				}

				blockIds.putVarInt(map.getInt(state), false);
			} else {
				blockIds.put((byte) 0);
			}
		}

		nbt.setByteArray("Blocks", blockIds.toByteArray());
		nbt.setTag("BlockData", palette);
		return nbt;
	}
}