package ilib.command.nextgen;

import roj.collect.RingBuffer;
import roj.collect.WeakMyHashMap;

import net.minecraft.entity.Entity;

import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:38
 */
public class TagId {
	private String name;

	public TagId(String name) {this.name = name;}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		TagId id = (TagId) o;

		return name.equals(id.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	static final class TagCache extends WeakMyHashMap<TagId, HashSet<Entity>> {
		RingBuffer<Entry<HashSet<Entity>>> pool;

		TagCache() {
			pool = new RingBuffer<>(10, 100);
		}

		void setCapacity(int cap) {
			ensureCapacity(cap);
			// todo LFU
			while (pool.size() > cap) {
				Entry<HashSet<Entity>> entry = pool.removeFirst();
			}
			pool.setCapacity(cap);
		}

		@Override
		public HashSet<Entity> get(Object key) {
			return super.get(key);
		}

		@Override
		public HashSet<Entity> put(TagId key, HashSet<Entity> val) {
			return super.put(key, val);
		}
	}
	static final TagCache tagCache = new TagCache();

	private static final TagId sl = new TagId("");
	private static TagId _tag(String tag) {
		sl.name = tag;
		return sl;
	}

	public static Collection<Entity> getMonitoredEntities(String source) {
		return null;
	}

	public static void onEntityJoin(EntityJoinWorldEvent event) {

	}
	public static void onEntityRemove() {

	}

	public static void onTagAdd(Entity entity, String tag) {
		HashSet<Entity> set = tagCache.get(_tag(tag));
		if (set != null) set.add(entity);
	}
	public static void onTagRemove(Entity entity, String tag) {
		HashSet<Entity> set = tagCache.get(_tag(tag));
		if (set != null) set.remove(entity);

	}
}
