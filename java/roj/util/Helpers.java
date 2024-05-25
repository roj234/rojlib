package roj.util;

import org.jetbrains.annotations.NotNull;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.text.Escape;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
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
	public static File getJarByClass(Class<?> clazz) {
		URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
		if (location == null) return null;
		String loc = location.getPath();
		if (loc.startsWith("file:")) loc = loc.substring(5);
		int i = loc.lastIndexOf('!');
		loc = loc.substring(loc.startsWith("/")?1:0, i<0?loc.length():i);
		try {
			return new File(Escape.decodeURI(loc).toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 在不可能的地方丢出异常 <BR>
	 * athrow(new IOException());
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Throwable> void athrow(Throwable e) throws T {throw (T) e;}

	/**
	 * 强制转换
	 */
	@SuppressWarnings("unchecked")
	public static <T> T cast(Object c) {return (T) c;}

	private static Object field;
	@NotNull
	public static <T> T nonnull() {return cast(field);}
	public static <T> T maybeNull() {return cast(field);}

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