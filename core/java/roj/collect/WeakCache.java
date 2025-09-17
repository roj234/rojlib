package roj.collect;

import roj.reflect.Unsafe;
import roj.util.Helpers;
import roj.util.NativeMemory;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * 这是一个特制的类似WeakHashMap的map，更省内存
 * 甚至支持自动清除被回收的项目，即便自身不被访问
 * @author Roj234
 * @since 2021/5/27 0:8
 */
public class WeakCache<K> extends WeakReference<K> implements Runnable {
	public WeakCache(K key, XashMap<K, ? extends WeakCache<K>> owner) {
		super(key, null);
		this.owner = Objects.requireNonNull(Helpers.cast(owner));
		this.hash = System.identityHashCode(key);
		cleanerRef = NativeMemory.createCleaner(key, this);
	}

	protected final XashMap<K, WeakCache<K>> owner;
	final int hash;
	final Object cleanerRef;

	public void destroy() {
		NativeMemory.invokeClean(cleanerRef);
	}

	private WeakCache<K> _next;

	@Override
	public void run() {
		try {
			synchronized (owner) {owner.removeInternal(this, hash);}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static final long _NEXT = Unsafe.fieldOffset(WeakCache.class, "_next");
	private static final long REFERENT = Unsafe.fieldOffset(Reference.class, "referent");
	public static <K, V extends WeakCache<K>> XashMap.Builder<K, V> shape(Class<V> vType) {
		return new XashMap.Builder<>(vType, _NEXT, REFERENT, Hasher.identity(), null);
	}
}