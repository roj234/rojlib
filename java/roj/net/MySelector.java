package roj.net;

import roj.collect.SimpleList;
import roj.reflect.Bypass;
import roj.reflect.Java22Workaround;
import roj.reflect.ReflectionUtils;

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
final class MySelector extends SimpleList<SelectionKey> implements Set<SelectionKey> {
	private MySelector() {super(100);}

	public static Set<SelectionKey> getIterable(Selector sel) {
		var v = getter.getSet(sel);
		return v instanceof HashSet<SelectionKey> set ? getter.getMap(set).keySet() : v;
	}

	@Java22Workaround
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
					try {
						oSelectedSet = U.objectFieldOffset(ReflectionUtils.getField(t.getClass(), "selectedKeys"));
						oPublicSelectedSet = U.objectFieldOffset(ReflectionUtils.getField(t.getClass(), "publicSelectedKeys"));
						oPublicSet = U.objectFieldOffset(ReflectionUtils.getField(t.getClass(), "publicKeys"));
					} catch (NoSuchFieldException e){
						e.printStackTrace();
					}
				}
			}
		}

		var sel = new MySelector();
		U.putObjectVolatile(t, oSelectedSet, sel);
		U.putObjectVolatile(t, oPublicSelectedSet, sel);
		//字段类型直接是HashSet 没法改了...
		U.putObjectVolatile(t, oPublicSet, getter.getSet(t));
		return t;
	}
}