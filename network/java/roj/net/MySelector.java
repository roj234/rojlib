package roj.net;

import roj.collect.ArrayList;
import roj.reflect.Bypass;
import roj.ci.annotation.Public;
import roj.reflect.Unaligned;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj233
 * @since 2022/1/24 11:32
 */
final class MySelector extends ArrayList<SelectionKey> implements Set<SelectionKey> {
	private MySelector() {super(100);}

	public static Set<SelectionKey> getIterable(Selector sel) {
		var v = getter.getSet(sel);
		return v instanceof HashSet<SelectionKey> set ? getter.getMap(set).keySet() : v;
	}

	@Public
	private interface H {
		Set<SelectionKey> getSet(Selector selector);
		HashMap<SelectionKey, Object> getMap(HashSet<SelectionKey> set);
	}

	private static volatile H getter;
	private static long oSelectedSet, oPublicSelectedSet, oPublicSet;
	public static Selector open() throws IOException {
		var t = Selector.open();
		if (getter == null) {
			synchronized (H.class) {
				if (getter == null) {
					getter = Bypass.builder(H.class).unchecked().access(t.getClass(), "keys", "getSet", null)
								   .access(HashSet.class, "map", "getMap", null).build();
					oSelectedSet = Unaligned.fieldOffset(t.getClass(), "selectedKeys");
					oPublicSelectedSet = Unaligned.fieldOffset(t.getClass(), "publicSelectedKeys");
					oPublicSet = Unaligned.fieldOffset(t.getClass(), "publicKeys");
				}
			}
		}

		var sel = new MySelector();
		U.putReferenceVolatile(t, oSelectedSet, sel);
		U.putReferenceVolatile(t, oPublicSelectedSet, sel);
		//字段类型直接是HashSet 没法改了...
		U.putReferenceVolatile(t, oPublicSet, getter.getSet(t));
		return t;
	}
}