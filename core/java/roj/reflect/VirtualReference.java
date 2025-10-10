package roj.reflect;

import org.jetbrains.annotations.NotNull;
import roj.optimizer.FastVarHandle;

import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 提供了一种将强引用值 {@code V} 与特定 {@link ClassLoader} 关联的机制，
 * 同时尊重 {@code ClassLoader} 的生命周期，以允许其在不再需要时被正确卸载。
 *
 * <p>此类的行为类似于 {@link java.util.Map Map}，其中的值 {@code V} 并非由
 * {@code VirtualReference} 实例直接引用，而是通过其关联的 {@code ClassLoader}
 * 引用。因此，{@code V} 的生命周期与对应的 {@code ClassLoader} 紧密绑定：
 * 只要 {@code ClassLoader} 处于活动状态，{@code V} 就会被保留。
 * 当 {@code ClassLoader} 被垃圾回收时，与之关联的 {@code V} 会自动被回收。
 *
 * <p>这个机制解决了在动态加载代码（例如，插件系统、热重载或使用 ASM 等工具动态生成类）
 * 时，正确管理 {@code ClassLoader} 生命周期的一个常见挑战。
 *
 * <h3>ClassLoader 卸载的挑战</h3>
 * 为了让一个 {@code ClassLoader} 及其加载的所有类能够被垃圾回收，必须同时满足两个主要条件：
 * <ol>
 *     <li>不存在指向 {@code ClassLoader} 实例本身的任何外部强引用。</li>
 *     <li>不存在指向该 {@code ClassLoader} 加载的任何 {@code Class} 实例的外部强引用，
 *         也不存在指向这些类的任何实例的强引用。</li>
 * </ol>
 * 这里的“外部引用”指的是来自非该 {@code ClassLoader} 所加载的类或对象的引用链，
 * 或者来自 GC 根 (GC Root) 的引用。如果这些条件未能满足，即使 {@code ClassLoader}
 * 表面上不再被使用，它及其加载的所有类和对象也会持续占用内存，导致内存泄漏。
 *
 * <p>然而，在实际应用中，我们常常需要将一些数据（例如，为某个 {@code ClassLoader}
 * 生成的缓存、反射数据或单例实例）与特定的 {@code ClassLoader} 关联起来。
 * 传统的做法常常面临以下问题：
 *
 * <ol>
 *     <li><b>使用 {@code WeakReference} 存储引用：</b> 如果使用 {@code WeakReference<V>}
 *         来存储值 {@code V}，那么 {@code V} 可能会随时被垃圾回收，这对于需要其持久存储
 *         直到 {@code ClassLoader} 卸载为止的场景是不可接受的。</li>
 *     <li><b>使用普通 {@code Map} 存储强引用：</b> 直接在一个外部的 {@code Map<ClassLoader, V>}
 *         中存储强引用 {@code V}，会导致 {@code VirtualReference} 实例本身或者包含它的
 *         外部 {@code Map} 成为一个 GC 根，从而阻止 {@code ClassLoader} 的卸载。
 *         这形成了典型的内存泄漏。</li>
 * </ol>
 *
 * @author Roj234
 * @since 2024/6/4
 * @revised 2025/10/17
 * @see ClassValue
 */
@FastVarHandle
public final class VirtualReference<V> {
	/*
	 * 旧版本的 VirtualReference 在目标 ClassLoader 中动态生成一个带有静态字段的辅助类，
	 * 并将值存储在该静态字段中来解决这个问题。这种方法确实能将数据的 GC 根移入 ClassLoader，
	 * 但具有如下缺陷：
	 *  1. 代码复杂，依赖 ASM 进行字节码生成；
	 *  2. 依赖一个关键假设：staticFieldBase(T.field) == T.class，而它通常仅在 HotSpot 虚拟机上成立；
	 *  3. 强依赖 Unsafe 和 long fieldOffset (VarHandle 或 Field 会强引用生成的 Class 对象)
	 *
	 * 作为RojLib逐步删除编译期Unsafe使用，并通过ASM转换器在运行时优化代码计划的一部分，这个类现已重构
	 *
	 * 重构后的 VirtualReference 极大地简化了解决方案，它利用了 ClassLoader 类内部一个私有但稳定的字段：
	 *   private final ConcurrentHashMap<String, NamedPackage> packages;
	 * 该字段在多个 Java 版本 (7-25+) 和 JVM 实现中展现出了卓越的稳定性，因 Package 缓存确有意义.
	 * 不仅如此，我们还有其它候选字段，包括但不限于 package2certs, parallelLockMap 等。
	 * 而且，由于 ConcurrentHashMap 本身就是线程安全的，我们也不再需要之前的同步和锁了。
	 *
	 * 因为 Java 的泛型擦除，我们可以在这些内部 Map 中存储非 key 的类型，
	 * 例如键是 VirtualReference 实例，值是类型 V，而不干扰其原始用途。
	 *
	 * 新的方法提供了和旧版本相同的引用语义，同时
	 *  1. 显著降低了代码的复杂性；
	 *  2. 提高了在不同 JVM 实现之间的兼容性；
	 *  3. 消除了动态类生成和复杂的 Unsafe 操作的需要。
	 */
	private static final VarHandle PACKAGES = Telescope.trustedLookup().findVarHandle(ClassLoader.class, "packages", ConcurrentHashMap.class);

	public VirtualReference() {}

	/**
	 * @see ConcurrentHashMap#get(Object)
	 */
	@SuppressWarnings("unchecked")
	public V get(ClassLoader key) {
		// 如果 key 为 null （系统类），则使用加载此类的 ClassLoader 作为默认值 (通常是应用类加载器)。
		if (key == null) key = VirtualReference.class.getClassLoader();

		// 访问 ClassLoader 内部的 'packages' map。
		// 由于类型擦除，尽管其内部类型是 <String, NamedPackage>，
		// 但我们可以将其视为 <VirtualReference<V>, V>，从而将其用于我们的目的。
		var map = (ConcurrentHashMap<VirtualReference<V>, V>) PACKAGES.get(key);

		return map.get(this);
	}

	/**
	 * @see ConcurrentHashMap#computeIfAbsent(Object, Function)
	 */
	@SuppressWarnings("unchecked")
	public V computeIfAbsent(ClassLoader key, @NotNull Function<? super ClassLoader, ? extends V> mapper) {
		if (key == null) key = VirtualReference.class.getClassLoader();
		var map = (ConcurrentHashMap<VirtualReference<V>, V>) PACKAGES.get(key);

		ClassLoader finalKey = key;
		Objects.requireNonNull(mapper);
		return map.computeIfAbsent(this, ignored -> mapper.apply(finalKey));
	}

	/**
	 * @see ConcurrentHashMap#remove(Object)
	 */
	@SuppressWarnings("unchecked")
	public final void remove(ClassLoader key) {
		if (key == null) key = VirtualReference.class.getClassLoader();
		var map = (ConcurrentHashMap<VirtualReference<V>, V>) PACKAGES.get(key);
		map.remove(this);
	}

	/**
	 * @see ConcurrentHashMap#put(Object, Object)
	 */
	@SuppressWarnings("unchecked")
	public void put(ClassLoader key, V val) {
		if (key == null) key = VirtualReference.class.getClassLoader();
		var map = (ConcurrentHashMap<VirtualReference<V>, V>) PACKAGES.get(key);
		map.put(this, val);
	}
}