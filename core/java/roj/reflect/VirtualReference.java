package roj.reflect;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.math.MathUtils;
import roj.optimizer.FastVarHandle;
import roj.util.Helpers;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static roj.reflect.Unsafe.U;

/**
 * 让ClassLoader能正确的被卸载，防止内存泄露 <pre>
 * 存放在里面的V（无误）是“Virtual”引用的
 * 1. Inherit   在V中存放K(ClassLoader)定义的类不会阻止类加载器的GC回收
 * 2. Strong    即使V不被引用，V也不会被回收
 * 3. Recycle   V只在K和K定义的类被回收时回收
 * </pre>
 * 原理也很简单，通过一个比较黑科技的方式将V的GC Root移到了K名下 <br>
 * 而不是VirtualReference的创建者名下
 * @author Roj234
 * @since 2024/6/4 3:31
 */
@FastVarHandle
public class VirtualReference<V> {
	/*
	 * Java里面的一个类加载器要被卸载，需要同时满足两个条件
	 *   1. 这个类加载器没有被外部引用
	 *   2. 这个类加载器加载的所有类都没有被外部引用
	 * 外部：不是这个类加载器加载的类
	 * 满足上述条件后，这个类加载器和它加载的所有类，会被一起卸载
	 *
	 * 然而问题来了，如果我要保留一个类的引用，应该如何保存
	 * 1. 最简单的方式就是类名->弱引用的类，因为类是弱引用的，可以被卸载
	 * 然而这种方法有问题，也就是类名虽然理论上是唯一的，但实际上“可以”重复
	 *
	 * 2. 所以我们需要一个 弱引用的加载器 -> 类名 -> 弱引用的类
	 * 这确实满足了“保留一个类的引用”，但是问题并未到此结束
	 *
	 * 3. 如果我需要保留一个重量级的、动态生成的、由目标类加载器K加载的对象，让它在K的生命周期内不被回收，应该怎么办？
	 * 上述方法不再成立，因为弱引用的重量级对象可能随时被回收
	 * 如果不使用弱引用，这个对象会一直引用K，造成内存泄露
	 *
	 * 4. 解决方案，就是我的VirtualReference
	 * 它通过修改GC引用根的方式，将3.转换为了2.问题
	 * 【类加载器加载的类只会和加载器一起回收】
	 *  所以我生成了一个类，它只有一个静态字段，由K加载
	 *  它的GC Root就是K，能和K一起回收
	 */
	private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	private static final VarHandle
		ENTRIES = MethodHandles.arrayElementVarHandle(Entry[].class),
		NEXT = Handles.lookup().findVarHandle(Entry.class, "next", Entry.class),
		VALUE = Handles.lookup().findVarHandle(Entry.class, "v", WeakReference.class),
		SIZE = Handles.lookup().findVarHandle(VirtualReference.class, "size", int.class);

	private static final Entry<?> SENTIAL = new Entry<>(null, null);

	public static final class Entry<V> extends WeakReference<ClassLoader> {
		final int hash;
		Entry<V> next;

		public Entry(ReferenceQueue<?> queue, ClassLoader k) {
			super(k, Helpers.cast(queue));
			hash = System.identityHashCode(k);
		}

		// 非常黑科技的方式，来保留一个对value的引用
		private WeakReference<Class<?>> v = Helpers.cast(SENTIAL);
		private long offset;

		@SuppressWarnings("unchecked")
		public V getValue() {
			Class<?> o = v.get();
			return o == null ? null : (V) U.getReference(o, offset);
		}

		public void setValue(V v) {
			if (this.v != null) {
				Class<?> ref = this.v.get();
				if (ref == null) throw new IllegalStateException("key was freed???");
				U.putReference(ref, offset, v);
				return;
			}

			ClassLoader loader = get();
			if (loader == null) throw new IllegalStateException("key was freed???");

			var ref = new ClassNode();
			ref.modifier = Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL;
			ref.name(loader.getClass().getName().replace('.', '/')+"$OwnedRef$"+Reflection.uniqueId());
			ref.newField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "v", "Ljava/lang/Object;");

			Class<?> klass = Reflection.defineClass(loader, ref);

			this.v = new WeakReference<>(klass);
			offset = Unsafe.fieldOffset(klass, "v");
			U.putReference(klass, offset, v);
		}
	}

	private void doEvict() {
		Entry<?>[] array = entries;
		Entry<?> remove;
		while ((remove = (Entry<?>) queue.poll()) != null) {
			int i = remove.hash & (array.length - 1);

			loop:
			for (;;) {
				var entry = array[i];
				if (entry == null) continue;

				Entry<?> prev = null;
				for (;;) {
					if (entry == remove) {
						Object next = NEXT.getAndSet(entry, SENTIAL);
						if (next != SENTIAL) {
							if (prev == null) array[i] = (Entry<?>) next;
							else prev.next = Helpers.cast(next);

							SIZE.getAndAdd(this, -1);
						}

						break loop;
					}

					prev = entry;
					entry = entry.next;
				}
			}
		}
	}

	private Entry<?>[] entries;
	private volatile int size;
	private int length = 1;

	public VirtualReference() { this(4); }
	public VirtualReference(int size) {
		if (size < length) return;
		length = MathUtils.nextPowerOfTwo(size);
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		Entry<?>[] newEntries = new Entry<?>[length];
		int newMask = length-1;

		int i = 0, j = entries.length;
		for (; i < j; i++) {
			Entry<V> entry = (Entry<V>) entries[i];
			if (entry == null) continue;

			do {
				Entry<V> next = entry.next;
				int newKey = entry.hash & newMask;
				entry.next = (Entry<V>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry = next;
			} while (entry != null);
		}

		entries = newEntries;
	}

	@SuppressWarnings("unchecked")
	public Entry<V> getEntry(ClassLoader key) {
		if (key == null) key = VirtualReference.class.getClassLoader();

		lock.readLock().lock();
		try {
			doEvict();

			Entry<?>[] array = entries;
			Entry<V> entry = array == null ? null : (Entry<V>) array[System.identityHashCode(key)&(array.length - 1)];

			while (entry != null) {
				if (key == entry.get()) return entry;
				entry = entry.next;
			}
		} finally {
			lock.readLock().unlock();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public void forEach(Consumer<Entry<V>> consumer) {
		lock.readLock().lock();
		try {
			doEvict();

			for (Entry<?> entry : entries) {
				while (entry != null) {
					consumer.accept((Entry<V>) entry);
					entry = entry.next;
				}
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public final V computeIfAbsent(ClassLoader key, @NotNull Function<? super ClassLoader, ? extends V> mapper) {
		if (key == null) key = VirtualReference.class.getClassLoader();

		if (size == length) {
			lock.writeLock().lock();
			try {
				if (size == length)
					resize();
			} finally {
				lock.writeLock().unlock();
			}
		}

		Entry<V> entry;
		lock.readLock().lock();
		try {
			doEvict();

			entry = getOrCreateEntry(key);
		} finally {
			lock.readLock().unlock();
		}

		while (true) {
			V value = entry.getValue();
			if (value != null) return value;

			if (VALUE.compareAndSet(entry, SENTIAL, null)) {
				SIZE.getAndAdd(this, 1);

				entry.setValue(value = mapper.apply(key));
				return value;
			}
		}
	}

	public final void clear() {
		if (size == 0) return;

		lock.writeLock().lock();

		size = 0;
		Arrays.fill(entries, null);
		doEvict();

		lock.writeLock().unlock();
	}

	@SuppressWarnings("unchecked")
	private Entry<V> getOrCreateEntry(ClassLoader key) {
		Entry<?>[] array = entries;
		if (array == null) {
			lock.readLock().unlock();
			lock.writeLock().lock();
			try {
				array = entries;
				if (array == null) entries = array = new Entry<?>[length];
			} finally {
				lock.writeLock().unlock();
				lock.readLock().lock();
			}
		}

		int i = System.identityHashCode(key)&(array.length-1);
		Entry<V> entry;
		// CAS first
		for (;;) {
			for (;;) {
				entry = (Entry<V>) ENTRIES.getVolatile(array, i);
				if (entry != null) break;

				if (ENTRIES.compareAndSet(array, i, null, SENTIAL)) {
					ENTRIES.setVolatile(array, i, entry = new Entry<>(queue, key));
					return entry;
				}
			}

			while (entry != SENTIAL) {
				if (key == entry.get()) return entry;

				if (entry.next == null) {
					if (NEXT.compareAndSet(entry, null, SENTIAL)) {
						Entry<V> next = new Entry<>(queue, key);
						entry.next = next;
						return next;
					}
				}

				entry = entry.next;
			}
		}
	}
}