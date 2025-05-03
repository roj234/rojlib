package roj.plugins.minecraft.server.data.world;

/**
 * @author Roj234
 * @since 2024/3/20 6:10
 */
public interface ChunkGenerator {
	Chunk generateChunk(int x, int z);
}