package ilib.api.registry;

import ilib.api.registry.Indexable.Impl;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;

import java.util.function.IntFunction;

/**
 * @author Roj234
 * @since 2020/8/22 13:40
 */
public class Registry<T extends Indexable> implements IRegistry<T> {
	IntBiMap<T> values = new IntBiMap<>();
	MyHashMap<String, T> nameIndex = new MyHashMap<>();
	T[] arr;
	IntFunction<T[]> arrayGet;

	public Registry(IntFunction<T[]> arrayGet) {
		this.arrayGet = arrayGet;
	}

	@Override
	public T[] values() {
		if (arr == null) {
			T[] arr = this.arr = arrayGet.apply(values.size());
			for (int i = 0; i < values.size(); i++) {
				arr[i] = values.get(i);
			}
		}
		return arr;
	}

	public int appendValue(T t) {
		int i;
		this.values.putInt(i = values.size(), t);
		this.nameIndex.put(t.getName(), t);
		if (t instanceof Impl) ((Impl) t).index = i;
		this.arr = null;
		return i;
	}

	@Override
	public T byId(int meta) {
		return values.get(meta);
	}

	public T byName(String meta) {
		return nameIndex.get(meta);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<T> getValueClass() {
		return (Class<T>) values.get(0).getClass();
	}

	public int idFor(T t) {
		return values.getInt(t);
	}
}
