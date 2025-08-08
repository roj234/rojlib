package roj.plugins.minecraft.server.data.world;

import roj.collect.BitArray;
import roj.plugins.minecraft.server.data.Block;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/3/20 7:12
 */
public class ChunkSection {
	final Cube<Block> blocks = new Cube<>(8, 4, Block.STATE_ID);
	//final Cube<Biome> biomes = new Cube<>(3, 4, null);
	final BitArray light;
	final short pos;
	int nonEmpty;

	ChunkSection(boolean doSkyLight, int pos) {
		light = new BitArray(4, doSkyLight ? 8192 : 4096);
		this.pos = (short) pos;
	}

	public boolean setBlock(int x, int y, int z, Block block) {
		Block prev = blocks.setBlock(x, y, z, block);
		if (prev == Block.AIR) nonEmpty++;
		else if (block == Block.AIR) nonEmpty--;
		return prev != block;
	}

	public void fill(Block block) {
		nonEmpty = block == Block.AIR ? 0 : 4096;
		blocks.setAll(block);
	}

	public void toMinecraftPacket(DynByteBuf buf) {
		buf.writeShort(nonEmpty);
		blocks.toMCChunkData_Full(buf);
		// 0bits element[0]=[ID1 => plains] data.length=0
		buf.put(0).putVarInt(1).putVarInt(0);
	}

	public static void writeEmpty(DynByteBuf buf) {
		buf.putShort(0)
		   // 0bits element[0]=[ID0 => air] data.length=0
		   .put(0).putVarInt(0).putVarInt(0)
		   // 0bits element[0]=[ID0 => void] data.length=0
		   .put(0).putVarInt(0).putVarInt(0);
	}
}