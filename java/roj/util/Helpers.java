package roj.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

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
	@Contract("_ -> fail")
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

	private static final Predicate<?> alwaystrue = (a) -> true, alwaysfalse = alwaystrue.negate();
	public static <T> Predicate<T> alwaysTrue() {return cast(alwaystrue);}
	public static <T> Predicate<T> alwaysFalse() {return cast(alwaysfalse);}

	private static final Function<?, ?> arraylistfn = (a) -> new SimpleList<>(), myhashmapfn = (a) -> new MyHashMap<>(), myhashsetfn = (a) -> new MyHashSet<>();
	public static <T, R> Function<T, List<R>> fnArrayList() {return cast(arraylistfn);}
	public static <T, R, E> Function<T, Map<R, E>> fnMyHashMap() {return cast(myhashmapfn);}
	public static <T, R> Function<T, Set<R>> fnMyHashSet() {return cast(myhashsetfn);}
}