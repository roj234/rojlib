package ilib.world.schematic;

import roj.collect.LongMap;
import roj.math.Mat4x3f;
import roj.math.Vec4f;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class Schematic {
	public String name;

	protected final short width, height, length;

	protected IBlockState[] blocks;

	protected LongMap<NBTTagCompound> tiles;
	protected List<NBTTagCompound> entities;

	protected Schematic(short width, short height, short length) {
		this.width = width;
		this.height = height;
		this.length = length;
		this.entities = null;
		this.tiles = new LongMap<>();
	}

	protected Schematic(NBTTagList entities, NBTTagList tiles, short width, short height, short length, IBlockState[] data) {
		this.width = width;
		this.height = height;
		this.length = length;
		this.blocks = data;

		if (entities != null) {
			this.entities = Arrays.asList(new NBTTagCompound[entities.tagCount()]);
			this.reloadEntityMap(entities);
		} else {
			this.entities = null;
		}

		if (tiles != null) {
			this.tiles = new LongMap<>(tiles.tagCount());
			this.reloadTileMap(tiles);
		} else {
			this.tiles = null;
		}
	}

	private void reloadTileMap(NBTTagList nbt) {
		this.tiles.clear();

		for (int i = 0; i < nbt.tagCount(); ++i) {
			NBTTagCompound tag = nbt.getCompoundTagAt(i);
			this.tiles.putLong(mergeTileIndex(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")), tag);
		}
	}

	private void reloadEntityMap(NBTTagList entities) {
		for (int i = 0; i < entities.tagCount(); ++i) {
			NBTTagCompound tag = entities.getCompoundTagAt(i);
			this.entities.set(i, tag);
		}
	}

	public IBlockState getBlockState(BlockPos pos) {
		return getBlockState(pos.getX(), pos.getY(), pos.getZ());
	}

	public IBlockState getBlockState(int x, int y, int z) {
		return this.blocks[getIndexFromCoordinates(x, y, z)];
	}

	public NBTTagCompound getTileData(int x, int y, int z, int worldX, int worldY, int worldZ) {
		NBTTagCompound tag = this.getTileData(x, y, z);
		if (tag != null) {
			tag.setInteger("x", worldX);
			tag.setInteger("y", worldY);
			tag.setInteger("z", worldZ);
			return tag;
		} else {
			return null;
		}
	}

	public List<NBTTagCompound> getEntities() {
		return entities == null ? Collections.emptyList() : entities;
	}

	public NBTTagCompound getTileData(int x, int y, int z) {
		return tiles == null ? null : tiles.get(mergeTileIndex(x, y, z));
	}

	public int length() {
		return length & 0xffff;
	}

	public int width() {
		return width & 0xffff;
	}

	public int height() {
		return height & 0xffff;
	}

	public Schematic withRotation(Rotation rot) {
		Mat4x3f M = new Mat4x3f().rotate(0, 1, 0, (float) (rot.ordinal() * Math.PI / 2));

		Vec4f V = new Vec4f(width, height, length);
		M.mul(V, V);
		Schematic t = new Schematic((short) V.x, (short) V.y, (short) V.z);

		IBlockState[] blocks = this.blocks;
		IBlockState[] tBlocks = t.blocks;
		for (int x = width - 1; x >= 0; x--) {
			for (int y = height - 1; y >= 0; y--) {
				for (int z = length - 1; z >= 0; z--) {
					IBlockState data = blocks[getIndexFromCoordinates(x, y, z)];
					if (data != null) data = data.withRotation(rot);

					M.mul(V.set(x, y, z, 1), V);
					tBlocks[t.getIndexFromCoordinates((int) V.x, (int) V.y, (int) V.z)] = data;
				}
			}
		}

		return t;
	}

	protected static long mergeTileIndex(int x, int y, int z) {
		return (long) x << 32 | (long) y << 16 | (long) z;
	}

	protected final int getIndexFromCoordinates(int x, int y, int z) {
		return y * this.width * this.length + z * this.width + x;
	}
}
