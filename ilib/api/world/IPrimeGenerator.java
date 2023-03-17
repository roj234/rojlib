package ilib.api.world;

import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;

/**
 * @author Roj234
 * @since 2020/8/24 16:58
 */
public interface IPrimeGenerator {
	void generate(World world, int chunkX, int chunkZ, ChunkPrimer chunk);

	default void writeTileData(World world, int chunkX, int chunkZ) {}
}
