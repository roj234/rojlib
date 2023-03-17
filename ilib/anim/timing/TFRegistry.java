package ilib.anim.timing;

import roj.collect.MyHashMap;

import java.util.Set;

/**
 * @author Roj234
 * @since 2021/5/27 22:23
 */
public class TFRegistry {
	// Config key
	public static final int CONFIG_EASE = 0;

	// Config value
	public static final int EASE_IN = 0, EASE_OUT = 1, EASE_IN_OUT = 2;

	static final MyHashMap<String, Timing.Factory> registry = new MyHashMap<>();

	public static Set<String> keys() {
		return registry.keySet();
	}

	public static Timing.Factory get(String name) {
		return registry.get(name);
	}

	public static void register(Timing.Factory func) {
		registry.put(func.name(), func);
	}

	//interpolate.linear=线性
	public static final Timing LINEAR = new Linear();
	//interpolate.quad=二次方
	public static final Timing X2_IN = new XnIn(2), X2_OUT = new XnOut(2), X2_IN_OUT = new XnHalf(1);
	//interpolate.cubic=三次方
	public static final Timing X3_IN = new XnIn(3), X3_OUT = new XnOut(3), X3_IN_OUT = new XnHalf(2);

	public static void init() {
		register(LINEAR.factory());
	}
}
