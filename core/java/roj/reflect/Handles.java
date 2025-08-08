package roj.reflect;

import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.OperationDone;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * @author Roj234
 * @since 2025/09/07 02:50
 */
public class Handles {
	private static final Handles instance = new Handles(Reflection.IMPL_LOOKUP);

	private final MethodHandles.Lookup lookup;
	public Handles(MethodHandles.Lookup lookup) {this.lookup = lookup;}

	public static Handles getInstance() {return instance;}

	public VarHandle findVarHandle(Class<?> recv, String name, Class<?> type) {
		try {
			return lookup.findVarHandle(recv, name, type);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public VarHandle findStaticVarHandle(Class<?> recv, String name, Class<?> type) {
		try {
			return lookup.findStaticVarHandle(recv, name, type);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public void ensureClassInitialized(Class<?> klass) {
		try {
			lookup.ensureInitialized(klass);
		} catch (IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	/**
	 * 在Class及其实例被GC时，自动从VM中卸载这个类
	 */
	public Class<?> defineWeakClass(ByteList b) {
		if (Debug.CLASS_DUMP) Debug.dump("weak", b);
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) b = sm.preDefineClass(b);
		try {
			return lookup.defineHiddenClass(b.toByteArray(), false).lookupClass();
		} catch (IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	/**
	 * Ensures that loads and stores before the fence will not be reordered
	 * with
	 * loads and stores after the fence.
	 *
	 * @apiNote Ignoring the many semantic differences from C and C++, this
	 * method has memory ordering effects compatible with
	 * {@code atomic_thread_fence(memory_order_seq_cst)}
	 */
	public static void fullFence() {VarHandle.fullFence();}
	/**
	 * Ensures that loads before the fence will not be reordered with loads and
	 * stores after the fence.
	 *
	 * @apiNote Ignoring the many semantic differences from C and C++, this
	 * method has memory ordering effects compatible with
	 * {@code atomic_thread_fence(memory_order_acquire)}
	 */
	public static void loadFence() {VarHandle.acquireFence();}
	/**
	 * Ensures that loads and stores before the fence will not be
	 * reordered with stores after the fence.
	 *
	 * @apiNote Ignoring the many semantic differences from C and C++, this
	 * method has memory ordering effects compatible with
	 * {@code atomic_thread_fence(memory_order_release)}
	 */
	public static void storeFence() {VarHandle.releaseFence();}
	/**
	 * Ensures that loads before the fence will not be reordered with
	 * loads after the fence.
	 */
	public static void loadLoadFence() {VarHandle.loadLoadFence();}
	/**
	 * Ensures that stores before the fence will not be reordered with
	 * stores after the fence.
	 */
	public static void storeStoreFence() {VarHandle.storeStoreFence();}
}
