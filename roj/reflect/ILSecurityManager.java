package roj.reflect;

import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.ByteList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2023/12/29 0029 18:00
 */
public class ILSecurityManager {
	private static volatile ILSecurityManager inst;
	public static ILSecurityManager getSecurityManager() { return inst; }
	public static void setSecurityManager(ILSecurityManager am) {
		if (inst != null) inst.checkReplace(am);
		inst = am;
	}

	protected void checkReplace(ILSecurityManager am) { if (this != am) throw new SecurityException("cannot replace existed "+getClass().getName()+" to "+am.getClass().getName()); }

	public ByteList checkDefineClass(String name, ByteList buf) { return buf; }

	public boolean checkAccess(Field field) { return true; }
	public boolean checkInvoke(Method field) throws NoSuchMethodException { return true; }
	public boolean checkConstruct(Constructor<?> field) { return true; }

	public void checkAccess(String owner, String name, String desc) {}

	public void filterMethods(MyHashSet<Method> methods) {}
	public void filterFields(SimpleList<Field> fields) {}
}