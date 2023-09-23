package roj.util;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.net.URIUtil;

import javax.annotation.Nonnull;
import java.io.File;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 小工具
 *
 * @author Roj234
 * @since 2021/6/17 1:43
 */
public class Helpers {
	/**
	 * 在不可能的地方丢出异常 <BR>
	 * athrow(new IOException());
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Throwable> void athrow(Throwable e) throws T {
		throw (T) e;
	}

	public static File getJarByClass(Class<?> clazz) {
		String loc = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
		int i = loc.lastIndexOf('!');
		loc = loc.substring(loc.startsWith("/")?1:0, i<0?loc.length():i);
		try {
			return new File(URIUtil.decodeURI(loc).toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public interface Node {
		Node next();
	}

	public static boolean hasCircle(Node begin) {
		if (begin == null) return false;

		Node slow = begin, fast1, fast2 = begin;

		while ((fast1 = fast2.next()) != null && (fast2 = fast1.next()) != null) {
			if (slow == fast1 || slow == fast2) return true;
			slow = slow.next();
		}

		return false;
	}

	/**
	 * 强制转换
	 */
	@SuppressWarnings("unchecked")
	public static <T> T cast(Object c) {
		return (T) c;
	}

	@Deprecated
	public static <K, V> Map<K, V> subMap(Map<K, V> map, int start, int end) {
		int i = -1;
		Map<K, V> subMap = new MyHashMap<>();

		for (K key : map.keySet()) {
			if (i++ < start) continue;
			subMap.put(key, map.get(key));
			if (i == end) break;
		}

		return subMap;
	}

	private static Object field;
	@Nonnull
	public static <T> T nonnull() {
		// noinspection all
		return null;
	}
	public static <T> T maybeNull() { return cast(field); }

	public static final Predicate<?> alwaystrue = (a) -> true;
	public static final Predicate<?> alwaysfalse = (a) -> false;
	public static final Function<?, ?> arraylistfn = (a) -> new SimpleList<>();
	public static final Function<?, ?> linkedlistfn = (a) -> new LinkedList<>();
	public static final Function<?, ?> myhashmapfn = (a) -> new MyHashMap<>();
	public static final Function<?, ?> myhashsetfn = (a) -> new MyHashSet<>();

	public static <T> Predicate<T> alwaysTrue() {
		return cast(alwaystrue);
	}
	public static <T> Predicate<T> alwaysFalse() {
		return cast(alwaysfalse);
	}

	public static <T, R> Function<T, List<R>> fnArrayList() {
		return cast(arraylistfn);
	}
	public static <T, R> Function<T, List<R>> fnLinkedList() {
		return cast(linkedlistfn);
	}

	public static <T, R, E> Function<T, Map<R, E>> fnMyHashMap() {
		return cast(myhashmapfn);
	}
	public static <T, R> Function<T, Set<R>> fnMyHashSet() {
		return cast(myhashsetfn);
	}
}
