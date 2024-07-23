package roj.plugins.minecraft.server.data;

import roj.collect.SimpleList;
import roj.collect.ToIntMap;

/**
 * @author Roj234
 * @since 2024/3/20 0020 6:55
 */
public class Registry<T> {
	public ToIntMap<T> REGISTRY = new ToIntMap<>();
	public SimpleList<T> VALUES = new SimpleList<>();
	private int nextId;

	final String name;
	public Registry(String name) {this.name = name;}
	public Registry(String name, int count) {
		this.name = name;
		this.REGISTRY.ensureCapacity(count);
		this.VALUES.ensureCapacity(count);
	}

	public int nextId() { return nextId; }
	public T getById(int id) { return id < 0 || id >= VALUES.size() ? null : VALUES.get(id); }
	public int getId(T block) { return REGISTRY.getInt(block); }

	public void register(T t, int id) {
		REGISTRY.putInt(t, id);
		if (VALUES.size() <= id) {
			VALUES.ensureCapacity(id+1);
			VALUES._setSize(id+1);
		}
		VALUES.getInternalArray()[id] = t;
		nextId = Math.max(id+1, nextId);
	}

	public void remove(int id) {
		Integer remove = REGISTRY.remove(VALUES.set(id, null));
		if (remove == null || remove != id)
			throw new IllegalStateException("Not registered");

		if (id == nextId-1) {
			do {
				nextId--;
			} while (nextId > 0 && VALUES.get(nextId-1) == null);
			VALUES._setSize(nextId);
		}
	}
}