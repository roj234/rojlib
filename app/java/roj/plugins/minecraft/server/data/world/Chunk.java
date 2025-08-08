package roj.plugins.minecraft.server.data.world;

import roj.collect.BitArray;
import roj.collect.IntMap;
import roj.config.NBTParser;
import roj.config.serial.ToNBT;
import roj.plugins.minecraft.server.data.Block;
import roj.plugins.minecraft.server.util.Utils;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.IntFunction;

/**
 * @author Roj234
 * @since 2024/3/20 5:37
 */
public final class Chunk {
	private static final IntFunction<ChunkSection> ALLOC = pos -> new ChunkSection(true, pos);

	public int baseHeight, maxHeight;

	final int x, z;
	final IntMap<ChunkSection> cubes = new IntMap<>();
	final BitArray heightmap;
	final IntMap<BlockExtraData> blockExtraData = new IntMap<>();

	public Chunk(int x, int z) {
		this.x = x;
		this.z = z;
		this.baseHeight = 0;
		this.maxHeight = 128;
		this.heightmap = new BitArray(32-Integer.numberOfLeadingZeros(maxHeight - baseHeight), 256);
	}

	public Block getBlock(int x, int y, int z) {
		var cube = cubes.get(y >> 4);
		return cube == null ? Block.AIR : cube.blocks.getBlock(x & 15, y & 15, z & 15);
	}

	public boolean setBlock(int x, int y, int z, Block block) {
		if (block == Block.AIR && !cubes.containsKey(y >> 4)) return false;

		var cube = cubes.computeIfAbsentI(y >> 4, ALLOC);
		boolean modified = cube.setBlock(x &= 15, y & 15, z &= 15, block);
		if (modified) {
			if (cube.nonEmpty == 0) cubes.remove(y >> 4);
			Heightmap.update(heightmap, this, x, y, z, block);
		}
		return modified;
	}

	public ChunkSection getSection(int y) { return cubes.computeIfAbsentI(y, ALLOC); }
	public void updateHeightmap() {
		for (int i = 0; i < 256; i++) {
			Heightmap.update(heightmap, this, i&15, maxHeight, i>>4, Block.AIR);
		}
	}

	public void loadData(InputStream input) {

	}

	public void write(OutputStream out) throws IOException {

	}

	public ByteList getSectionData() {
		var buf = new ByteList();
		int count = (maxHeight - baseHeight) >> 4;
		for (int i = 0; i < count; i++) {
			var section = cubes.get(i);
			if (section == null) ChunkSection.writeEmpty(buf);
			else section.toMinecraftPacket(buf);
		}
		return buf;
	}

	public ByteList createPacket() {
		ByteList buf = new ByteList();
		buf.putInt(x).putInt(z);

		// ChunkData
		//HeightMap
		ToNBT ser = new ToNBT(buf);
		ser.valueMap();
		for (int i = 0; i < Heightmap.SEND_TO_CLIENT_ID; i++) {
			ser.key(Heightmap.VALUES[i].name());

			ser.onValue(NBTParser.LONG_ARRAY);
			int[] data = heightmap.getInternal();
			buf.putInt((data.length+1) / 2);
			Utils.writeFakeLongArray2(buf, data);
		}
		ser.pop();

		//Section
		ByteList data = getSectionData();
		buf.putVarInt(data.readableBytes()).put(data);
		data.release();

		// TileEntity
		buf.putVarInt(0);
		// endChunkData

		//LightData
		buf.putBool(true);
		buf.putVarInt(0);
		buf.putVarInt(0);
		buf.putVarInt(0);
		buf.putVarInt(0);
		buf.putVarInt(0);
		buf.putVarInt(0);
		return buf;
	}
}