package roj.collect;

import roj.util.Helpers;
import roj.util.NativeMemory;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Objects;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2021/5/27 0:8
 */
public class ImmediateWeakReference<K> extends WeakReference<K> implements Runnable {
	public ImmediateWeakReference(K key, XHashSet<K, ? extends ImmediateWeakReference<K>> owner) {
		super(key, null);
		this.owner = Objects.requireNonNull(Helpers.cast(owner));
		this.hash = System.identityHashCode(key);
		cleanerRef = NativeMemory.createCleaner(key, this);
	}

	protected final XHashSet<K, ImmediateWeakReference<K>> owner;
	final int hash;
	final Object cleanerRef;

	public void destroy() {
		NativeMemory.invokeClean(cleanerRef);
	}

	private ImmediateWeakReference<K> _next;

	@Override
	public void run() {
		try {
			synchronized (owner) {owner.removeInternal(this, hash);}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static <K, V extends ImmediateWeakReference<K>> XHashSet.Shape<K, V> shape(Class<V> vType) {
		try {
			Field key = Reference.class.getDeclaredField("referent");
			Field next = ImmediateWeakReference.class.getDeclaredField("_next");
			return new XHashSet.Shape<>(vType, U.objectFieldOffset(next), U.objectFieldOffset(key), Hasher.identity(), null);
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("无法找到字段", e);
		}
	}
}