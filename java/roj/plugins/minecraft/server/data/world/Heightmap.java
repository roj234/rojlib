package roj.plugins.minecraft.server.data.world;

import roj.collect.BitArray;
import roj.plugins.minecraft.server.data.Block;

import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2024/3/20 0020 7:30
 */
public enum Heightmap {
	WORLD_SURFACE(b -> b == Block.AIR),
	OCEAN_FLOOR(Block::isCollible),
	MOTION_BLOCKING(b -> b.isCollible() || b.changesMovement());

	final Predicate<Block> predicate;
	Heightmap(Predicate<Block> predicate) { this.predicate = predicate; }

	static final Heightmap[] VALUES = values();
	public static final int LENGTH = VALUES.length;
	public static final int SEND_TO_CLIENT_ID = 3;

	public static void update(BitArray map, Chunk chunk, int x, int y, int z, Block to) {
		for (int i = 0; i < LENGTH; i++) {
			int j = (x << 4) | z;
			int height = map.get(j);
			Predicate<Block> predicate = VALUES[i].predicate;
			if (y >= height && !predicate.test(to)) {
				int y1 = y;
				while (y1 > chunk.baseHeight) {
					y1--;
					if (predicate.test(chunk.getBlock(x, y1, z))) break;
				}
				map.set(j, y1-chunk.baseHeight);
			}
		}
	}
}