package roj.net;

import roj.ci.annotation.Public;
import roj.collect.ArrayList;
import roj.optimizer.FastVarHandle;
import roj.reflect.Bypass;
import roj.reflect.Handles;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Roj233
 * @since 2022/1/24 11:32
 */
@FastVarHandle
final class MySelector extends ArrayList<SelectionKey> implements Set<SelectionKey> {
	private MySelector() {super(100);}

	public static Set<SelectionKey> getIterable(Selector sel) {
		var v = impl.getSet(sel);
		return v instanceof HashSet<SelectionKey> set ? impl.getMap(set).keySet() : v;
	}

	@Public
	private interface H {
		Set<SelectionKey> getSet(Selector selector);
		HashMap<SelectionKey, Object> getMap(HashSet<SelectionKey> set);
	}

	private static volatile H impl;
	private static VarHandle selectedKeys, publicSelectedKeys, publicKeys;
	public static Selector open() throws IOException {
		var t = Selector.open();
		if (impl == null) {
			synchronized (H.class) {
				if (impl == null) {
					impl = Bypass.builder(H.class).unchecked()
							.access(t.getClass(), "keys", "getSet", null)
							.access(HashSet.class, "map", "getMap", null)
							.build();
					selectedKeys = Handles.lookup().findVarHandle(t.getClass(), "selectedKeys", Set.class);
					publicSelectedKeys = Handles.lookup().findVarHandle(t.getClass(), "publicSelectedKeys", Set.class);
					publicKeys = Handles.lookup().findVarHandle(t.getClass(), "publicKeys", HashSet.class);
				}
			}
		}

		var set = new MySelector();
		selectedKeys.setVolatile(t, set);
		publicSelectedKeys.setVolatile(t, set);
		//字段类型直接是HashSet 没法改了...
		publicKeys.setVolatile(t, impl.getSet(t));
		return t;
	}
}