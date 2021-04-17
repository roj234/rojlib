package roj.dev.hr;

import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:35
 */
public abstract class HContext {
	private static final HKlass NULL = new HKlass();
	private final MyHashMap<String, HKlass> managedClasses = new MyHashMap<>();

	private HLoader methodLoader = newMethodLoader();
	private HLoader newMethodLoader() {
		return new HLoader(HContext.class.getClassLoader());
	}

	protected abstract ConstantData loadManagedClass(String name);

	public HKlass getManagedClass(String name) {
		HKlass klass = managedClasses.get(name);
		if (klass == NULL) return null;
		if (klass != null) return klass;

		ConstantData info = loadManagedClass(name);
		if (info == null) {
			managedClasses.put(name, NULL);
			return null;
		}
		klass = new HKlass();
		klass.init(this, info);

		managedClasses.put(name, klass);
		return klass;
	}

	public Class<?> tryLoadHKlass(String name) {
		getManagedClass(name).newInstance();
		return null;
	}
}
