package ilib.api.registry;

import roj.collect.MyHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/2 23:45
 */
public final class RegistryBuilder {
	@Deprecated
	public RegistryBuilder(int start, int end) {
		for (int i = start; i < end; i++) {
			append(String.valueOf(i));
		}
	}

	public static class Std extends Indexable.Impl implements Propertied<Std> {
		Std(String name, int id) {
			super(name, id);
		}

		public Object prop(String name) {
			return null;
		}

		public void prop(String name, Object property) {}
	}

	public static final class StdProp extends Std {
		public final MyHashMap<String, Object> prop = new MyHashMap<>(1, 1);

		@Override
		public Object prop(String name) {
			return prop.get(name);
		}

		@Override
		public void prop(String name, Object property) {
			prop.put(name, property);
		}

		StdProp(String name, int index) {
			super(name, index);
		}
	}

	private final List<Std> list = new ArrayList<>();

	public RegistryBuilder() {
	}

	public RegistryBuilder(String... names) {
		addAll(names);
	}

	public RegistryBuilder append(String name) {
		list.add(new Std(name, list.size()));
		return this;
	}

	public RegistryBuilder prop(String name, Object property) {
		Std std = list.get(list.size() - 1);
		if (std.getClass() != StdProp.class) {
			list.set(list.size() - 1, std = new StdProp(std.name, std.index));
		}
		std.prop(name, property);
		return this;
	}

	public RegistryBuilder addAll(String... list) {
		for (String s : list)
			append(s);
		return this;
	}

	public RegistryBuilder addAll(List<String> tmp) {
		for (int i = 0; i < tmp.size(); i++) {
			append(tmp.get(i));
		}
		return this;
	}

	public int size() {
		return list.size();
	}

	public Std[] values() {
		return this.list.toArray(new Std[list.size()]);
	}

	public IRegistry<Std> build() {
		return RegistrySimple.from(values());
	}
}