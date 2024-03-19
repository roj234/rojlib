package roj.minecraft.server.data;

import roj.collect.IntBiMap;

/**
 * @author Roj234
 * @since 2024/3/20 0020 6:55
 */
public class Registry<T> {
	public IntBiMap<T> REGISTRY = new IntBiMap<>();
	private int nextId;

	String name;
	public Registry(String name) {
		this.name = name;
	}

	public int nextId() { return nextId; }
	public T getById(int id) { return REGISTRY.get(id); }
	public int getId(T block) { return REGISTRY.getInt(block); }

	public void register(T t, int id) {
		REGISTRY.putByValue(id, t);
		nextId = Math.max(id+1, nextId);
	}
}