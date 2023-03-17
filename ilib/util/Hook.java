package ilib.util;

import roj.collect.MyHashMap;
import roj.util.Helpers;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class Hook {
	public static final String MODEL_REGISTER = "itemModelReg";
	public static final String LANGUAGE_RELOAD = "langReload";

	private final MyHashMap<String, List<Runnable>> hooks = new MyHashMap<>();

	public void add(String name, Runnable func) {
		hooks.computeIfAbsent(name, Helpers.fnArrayList()).add(func);
	}

	public void remove(String name) {
		this.hooks.remove(name);
	}

	public void trigger(String name) {
		List<Runnable> list = this.hooks.get(name);
		if (list == null) return;
		for (int i = 0; i < list.size(); i++) {
			list.get(i).run();
		}
	}

	public void triggerOnce(String name) {
		List<Runnable> list = this.hooks.remove(name);
		if (list == null) return;
		for (int i = 0; i < list.size(); i++) {
			list.get(i).run();
		}
	}
}