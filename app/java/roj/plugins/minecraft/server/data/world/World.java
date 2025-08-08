package roj.plugins.minecraft.server.data.world;

import org.jetbrains.annotations.NotNull;
import roj.collect.LongMap;
import roj.plugins.minecraft.server.data.Block;

import java.util.Collection;

/**
 * @author Roj234
 * @since 2024/3/19 16:51
 */
public class World {
	private final LongMap<Chunk> chunks = new LongMap<>();
	private String id, type;
	private ChunkLoader loader;
	private ChunkGenerator generator;

	public World() {
		id = "custom:test";
		type = "custom:the_end";
		loader = null;
		generator = Chunk::new;
	}

	public String getId() { return id; }
	public String getType() { return type; }

	public void createPhysicalBorder(int xzRadius, int minY, int maxY, Block barrier) {
		int blockRadius = xzRadius*16;
		for (int x = -blockRadius-1; x <= blockRadius; x++) {
			for (int z = -blockRadius-1; z <= blockRadius; z++) {
				setBlock(x, minY, z, barrier);
				setBlock(x, maxY, z, barrier);
			}
		}
		for (int xz = -xzRadius-1; xz <= xzRadius; xz++) {
			fillChunk(xz, -xzRadius-1, barrier);
			fillChunk(xz, xzRadius, barrier);
			fillChunk(-xzRadius-1, xz, barrier);
			fillChunk(xzRadius, xz, barrier);
		}
	}

	@NotNull
	public Chunk getChunk(int x, int z) {
		Chunk chunk = chunks.get(chunkPos(x, z));
		if (chunk == null) {
			if (loader != null) chunk = loader.loadChunk(x, z);
			if (chunk == null) chunk = generator.generateChunk(x, z);
			chunks.put(chunkPos(x, z), chunk);
		}
		return chunk;
	}
	@NotNull
	public Chunk getChunkNoGenerate(int x, int z) {
		Chunk chunk = chunks.get(chunkPos(x, z));
		if (chunk == null) {
			if (loader != null) chunk = loader.loadChunk(x, z);
			if (chunk == null) return new Chunk(x, z);
		}
		return chunk;
	}
	private static long chunkPos(int x, int z) { return ((long) x << 32) | (z&0xFFFFFFFFL); }

	@NotNull
	public Block getBlock(int x, int y, int z) { return getChunk(x >> 4, z >> 4).getBlock(x, y, z); }
	public boolean setBlock(int x, int y, int z, Block block) { return getChunk(x >> 4, z >> 4).setBlock(x, y, z, block); }

	public void fillChunk(int x, int z, Block block) {
		Chunk chunk = getChunk(x, z);
		for (int i = 0; i < getSectionCount(); i++) {
			chunk.getSection(i).fill(block);
			chunk.updateHeightmap();
		}
	}

	public int getSectionCount() {return 128/16;}

	public Collection<Chunk> getChunks() {return chunks.values();}
}