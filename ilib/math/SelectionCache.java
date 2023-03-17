package ilib.math;

import roj.collect.LongMap;

import net.minecraft.util.math.BlockPos;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class SelectionCache {
	private static final LongMap<Arena> map = new LongMap<>();

	public static Arena set(long id, int s, BlockPos pos) {
		Arena arena = map.get(id);
		if (arena == null) map.putLong(id, arena = new Arena());
		if (s == 1) {arena.setPos1(pos);} else arena.setPos2(pos);
		return arena;
	}

	public static boolean remove(long id) {
		return null != map.remove(id);
	}

	public static Arena get(long id) {
		return map.get(id);
	}

	public static boolean has(long id) {
		return map.containsKey(id);
	}
}