package ilib.math;

import roj.collect.IntMap;
import roj.collect.LongMap;
import roj.collect.MyHashSet;
import roj.math.Rect3i;

import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class FastPath<V extends PositionProvider>/* implements Iterable<V>*/ {
	private final IntMap<LongMap<Collection<V>>> fastPath = new IntMap<>(2);

	private final int shift;

	public FastPath() {
		this(4);
	}

	public FastPath(int shift) {
		this.shift = shift;
	}

	public V get(int world, BlockPos pos) {
		LongMap<Collection<V>> map = fastPath.get(world);
		if (map != null) {
			long chunkPos = ((long) (pos.getX() >>> shift) << 42) | ((long) pos.getY() >>> shift) << 21 | pos.getZ() >>> shift;
			Collection<V> list = map.get(chunkPos);
			if (list != null) {
				for (V data : list) {
					if (data.getSection().contains(pos) && data.contains(pos)) return data;
				}
			}
		}
		return null;
	}

	public boolean collidesWith(int world, Rect3i section) {
		LongMap<Collection<V>> map = fastPath.get(world);
		if (map != null) {
			for (int x = section.xmin, xm = section.xmax; x < xm; x += 1 << shift) {
				for (int y = section.ymin, ym = section.ymax; y < ym; y += 1 << shift) {
					for (int z = section.zmin, zm = section.zmax; z < zm; z += 1 << shift) {
						long chunkPos = ((long) (x >>> shift) << 42) | ((long) y >>> shift) << 21 | z >>> shift;
						Collection<V> list = map.get(chunkPos);
						if (list != null) {
							for (V data : list) {
								if (data.getSection().intersects(section)) return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	public Set<V> getAll(int world, Rect3i sect, Set<V> result) {
		LongMap<Collection<V>> map = fastPath.get(world);
		if (map != null) {
			for (int x = sect.xmin >> shift, xend = sect.xmax >> shift; x <= xend; x++) {
				for (int y = sect.ymin >> shift, yend = sect.ymax >> shift; y <= yend; y++) {
					for (int z = sect.zmin >> shift, zend = sect.zmax >> shift; z <= zend; z++) {
						long chunkPos = ((long) x << 42) | (long) y << 21 | (long) z;
						Collection<V> list = map.get(chunkPos);
						if (list != null) {
							for (V data : list) {
								if (data.getSection().intersects(sect)) {
									if (result == null) result = new MyHashSet<>();
									result.add(data);
								}
							}
						}
					}
				}
			}
			if (result != null) return result;
		}
		return Collections.emptySet();
	}

	public void put(V data) {
		data.handle(this);
		LongMap<Collection<V>> map = fastPath.get(data.getWorld());
		if (map == null) {
			fastPath.putInt(data.getWorld(), map = new LongMap<>());
		}
		Section sect = data.getSection();
		for (int x = sect.xmin >> shift, xend = sect.xmax >> shift; x <= xend; x++) {
			for (int y = sect.ymin >> shift, yend = sect.ymax >> shift; y <= yend; y++) {
				for (int z = sect.zmin >> shift, zend = sect.zmax >> shift; z <= zend; z++) {
					long chunkPos = ((long) x << 42) | (long) y << 21 | (long) z;
					Collection<V> list = map.get(chunkPos);
					if (list == null) {
						map.putLong(chunkPos, list = newStorage());
					}
					if (!list.contains(data)) list.add(data);
				}
			}
		}
	}

	protected Collection<V> newStorage() {
		return new MyHashSet<>(2);
	}

	public void remove(V data) {
		LongMap<Collection<V>> worldMap = fastPath.get(data.getWorld());
		if (worldMap != null) {
			Section sect = data.getSection();
			for (int x = sect.xmin >> shift, xend = sect.xmax >> shift; x <= xend; x++) {
				for (int y = sect.ymin >> shift, yend = sect.ymax >> shift; y <= yend; y++) {
					for (int z = sect.zmin >> shift, zend = sect.zmax >> shift; z <= zend; z++) {
						long chunkPos = ((long) x << 42) | (long) y << 21 | (long) z;
						Collection<V> list = worldMap.get(chunkPos);
						if (list != null) {
							list.remove(data);
							if (list.isEmpty()) {
								worldMap.remove(chunkPos);
							}
						}
					}
				}
			}
		}
	}

	public void clear() {
		for (LongMap<Collection<V>> map : fastPath.values()) {
			map.clear();
		}
	}

	public Collection<V> i_get(int world, long off) {
		LongMap<Collection<V>> map = fastPath.get(world);
		return map == null ? Collections.emptyList() : map.getOrDefault(off, Collections.emptyList());
	}

	public void putAll(FastPath<V> fastPath) {
		for (IntMap.Entry<LongMap<Collection<V>>> entry0 : fastPath.fastPath.selfEntrySet()) {
			LongMap<Collection<V>> map = this.fastPath.get(entry0.getIntKey());
			if (map == null) {
				this.fastPath.putInt(entry0.getIntKey(), map = new LongMap<>());
			}

			for (LongMap.Entry<Collection<V>> entry1 : entry0.getValue().selfEntrySet()) {
				Collection<V> copy = newStorage();
				copy.addAll(entry1.getValue());

				map.putLong(entry1.getLongKey(), entry1.getValue());
			}
		}
	}
}
