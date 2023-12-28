package roj.net.ch;

import roj.collect.SimpleList;
import roj.reflect.DirectAccessor;
import roj.reflect.FieldAccessor;
import roj.reflect.Java9Compat;
import roj.reflect.ReflectionUtils;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Roj233
 * @since 2022/1/24 11:32
 */
public final class MySelector extends SimpleList<SelectionKey> implements Set<SelectionKey> {
	private MySelector() {
		super(100);
	}

	public static Set<SelectionKey> getForeacher(Selector sel) {
		Set<SelectionKey> v = getter.getSet(sel);
		if (v.getClass() == HashSet.class) return getter.getMap((HashSet<SelectionKey>) v).keySet();
		return v;
	}

	private interface H {
		Set<SelectionKey> getSet(Selector selector);
		HashMap<SelectionKey, Object> getMap(HashSet<SelectionKey> set);
	}

	private static volatile H getter;
	private static FieldAccessor setSelectedSet1, setPublicSelectedSet1, setPublicSet1;
	public static Selector open() throws IOException {
		Selector t = Selector.open();
		if (getter == null) {
			synchronized (H.class) {
				if (getter == null) {
					getter = DirectAccessor.builder(H.class).unchecked().access(t.getClass(), "keys", "getSet", null)
										   .access(HashSet.class, "map", "getMap", null).build();
					try {
						if (ReflectionUtils.JAVA_VERSION > 8)
							Java9Compat.OpenModule(HashSet.class, "sun.nio.ch", MySelector.class);

						setSelectedSet1 = ReflectionUtils.access(ReflectionUtils.getField(t.getClass(), "selectedKeys"));
						setPublicSelectedSet1 = ReflectionUtils.access(ReflectionUtils.getField(t.getClass(), "publicSelectedKeys"));
						setPublicSet1 = ReflectionUtils.access(ReflectionUtils.getField(t.getClass(), "publicKeys"));
					} catch (NoSuchFieldException ignored) {}
				}
			}
		}

		MySelector set = new MySelector();
		setSelectedSet1.setObject(t, set);
		setPublicSelectedSet1.setObject(t, set);
		//字段类型直接是HashSet 没法改了...
		setPublicSet1.setObject(t, getter.getSet(t));
		return t;
	}
}