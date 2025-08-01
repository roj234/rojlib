package roj.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.UnknownNullability;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;

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

	@UnknownNullability public static <T> T maybeNull() {return null;}

	private static final Predicate<?> alwaystrue = (a) -> true, alwaysfalse = alwaystrue.negate();
	public static <T> Predicate<T> alwaysTrue() {return cast(alwaystrue);}
	public static <T> Predicate<T> alwaysFalse() {return cast(alwaysfalse);}

	private static final Function<?, ?> arraylistfn = (a) -> new ArrayList<>(), HashMapfn = (a) -> new HashMap<>(), HashSetfn = (a) -> new HashSet<>();
	public static <T, R> Function<T, List<R>> fnArrayList() {return cast(arraylistfn);}
	public static <T, R, E> Function<T, Map<R, E>> fnHashMap() {return cast(HashMapfn);}
	public static <T, R> Function<T, Set<R>> fnHashSet() {return cast(HashSetfn);}
}