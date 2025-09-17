package roj.reflect;

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

	public static Handles lookup() {return instance;}

	public static Class<?> findClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public VarHandle findVarHandle(Class<?> recv, String name, Class<?> type) {
		try {
			//ILSecurityManager sm = ILSecurityManager.getSecurityManager();
			//if (sm != null) sm.checkAccess(recv, name, type, Reflection.getCallerClass(3));
			return lookup.findVarHandle(recv, name, type);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public VarHandle findStaticVarHandle(Class<?> recv, String name, Class<?> type) {
		try {
			//ILSecurityManager sm = ILSecurityManager.getSecurityManager();
			//if (sm != null) sm.checkAccess(recv, name, type, Reflection.getCallerClass(3));
			return lookup.findStaticVarHandle(recv, name, type);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public static void fullFence() {VarHandle.fullFence();}
	public static void loadFence() {VarHandle.acquireFence();}
	public static void storeFence() {VarHandle.releaseFence();}
}
