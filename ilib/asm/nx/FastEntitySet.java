package ilib.asm.nx;

import ilib.Config;
import ilib.asm.util.IEntityList;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.*;
import roj.util.Helpers;

import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;

import java.util.*;

/**
 * @author Roj233
 * @since 2022/5/25 2:02
 */
@Nixim(value = "/", copyItf = true)
class FastEntitySet<T> extends ClassInheritanceMultiMap<T> implements IEntityList {
	@Shadow
	private static Set<Class<?>> ALL_KNOWN;
	@Shadow
	private Map<Class<?>, List<T>> map;
	@Shadow
	private Set<Class<?>> knownKeys;
	@Copy(unique = true)
	private AbstractIterator<Class<?>> keyItr;
	@Shadow
	private Class<T> baseClass;
	@Shadow
	private List<T> values;

	FastEntitySet() {
		super(null);
	}

	@Inject(value = "<init>", at = Inject.At.REPLACE)
	public void init(Class<T> clazz) {
		$$$CONSTRUCTOR();
		baseClass = clazz;

		map = new MyHashMap<>();
		values = new SimpleList<>();
		MyHashSet<Class<?>> keys = new IdentitySet<>();
		knownKeys = keys;
		keyItr = keys.setItr();

		knownKeys.add(clazz);
		map.put(clazz, values);

		if (Config.inheritEntityType) {
			for (Class<?> clz : ALL_KNOWN) {
				createLookup(clz);
			}
		}
	}

	private void $$$CONSTRUCTOR() {}

	@Inject("/")
	protected void createLookup(Class<?> clazz) {
		if (knownKeys.add(clazz)) {
			ALL_KNOWN.add(clazz);
			for (int i = 0; i < values.size(); i++) {
				T t = values.get(i);
				if (clazz.isAssignableFrom(t.getClass())) {
					this.addForClass(t, clazz);
				}
			}
		}
	}

	@Inject("/")
	public boolean add(T t) {
		AbstractIterator<Class<?>> itr = keyItr;
		itr.reset();

		while (itr.hasNext()) {
			Class<?> clz = itr.next();
			if (clz.isAssignableFrom(t.getClass())) {
				addForClass(t, clz);
			}
		}

		return true;
	}

	@Inject("")
	private void addForClass(T value, Class<?> type) {
		List<T> list = map.get(type);
		if (list == null) {
			map.put(type, list = new SimpleList<>(2));
		}
		list.add(value);
	}

	@SuppressWarnings("unchecked")
	@Inject("/")
	public boolean remove(Object obj) {
		T t = (T) obj;

		boolean flag = false;

		AbstractIterator<Class<?>> itr = keyItr;
		itr.reset();

		while (itr.hasNext()) {
			Class<?> clz = itr.next();
			if (clz.isAssignableFrom(t.getClass())) {
				List<T> list = map.get(clz);
				if (list != null && list.remove(t)) {
					flag = true;
				}
			}
		}

		return flag;
	}

	@SuppressWarnings("unchecked")
	@Inject("/")
	public boolean contains(Object o) {
		List<Object> value = (List<Object>) getByClass(o.getClass());
		for (int i = 0; i < value.size(); i++) {
			Object o1 = value.get(i);
			if (o.equals(o1)) return true;
		}
		return false;
	}

	@Inject("/")
	public <S> Iterable<S> getByClass(Class<S> clazz) {
		return Helpers.cast(map.getOrDefault(initializeClassLookup(clazz), Collections.emptyList()));
	}

	@Inject("/")
	public Iterator<T> iterator() {
		return values.iterator();
	}

	@Override
	@Copy
	public List<Entity> getValue() {
		return Helpers.cast(values);
	}

	@Override
	@Copy
	public <E extends Entity> List<E> getValue(Class<E> clazz) {
		try {
			return Helpers.cast(getByClass(clazz));
		} catch (ClassCastException e) {
			if (e.getMessage().contains("foam")) {
				throw new IllegalStateException("实体获取优化与FoamFix功能冲突（而且没我做的好）请在它的配置文件中关闭 fasterEntityLookup");
			}
			throw e;
		}
	}
}