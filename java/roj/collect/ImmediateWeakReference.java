package roj.collect;

import roj.reflect.Unaligned;
import roj.util.Helpers;
import roj.util.NativeMemory;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/27 0:8
 */
public class ImmediateWeakReference<K> extends WeakReference<K> implements Runnable {
	public ImmediateWeakReference(K key, XashMap<K, ? extends ImmediateWeakReference<K>> owner) {
		super(key, null);
		this.owner = Objects.requireNonNull(Helpers.cast(owner));
		this.hash = System.identityHashCode(key);
		cleanerRef = NativeMemory.createCleaner(key, this);
	}

	protected final XashMap<K, ImmediateWeakReference<K>> owner;
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

	private static final long _NEXT = Unaligned.fieldOffset(ImmediateWeakReference.class, "_next");
	private static final long REFERENT = Unaligned.fieldOffset(Reference.class, "referent");
	public static <K, V extends ImmediateWeakReference<K>> XashMap.Builder<K, V> shape(Class<V> vType) {
		return new XashMap.Builder<>(vType, _NEXT, REFERENT, Hasher.identity(), null);
	}
}