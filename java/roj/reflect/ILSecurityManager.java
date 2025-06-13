package roj.reflect;

import roj.util.ByteList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2023/12/29 18:00
 */
public class ILSecurityManager {
	private static volatile ILSecurityManager inst;
	public static ILSecurityManager getSecurityManager() { return inst; }
	public static void setSecurityManager(ILSecurityManager am) {
		if (inst != null) inst.checkReplace(am);
		inst = am;
	}

	protected void checkReplace(ILSecurityManager am) { if (this != am) throw new SecurityException("cannot replace existing "+getClass().getName()+" to "+am.getClass().getName()); }

	public ByteList preDefineClass(ByteList buf) { return buf; }

	public boolean checkAccess(Field field, Class<?> caller) { return true; }
	public boolean checkInvoke(Method field, Class<?> caller) throws NoSuchMethodException { return true; }
	public boolean checkConstruct(Constructor<?> field, Class<?> caller) { return true; }

	public void checkAccess(String owner, String name, String desc, Class<?> caller) throws SecurityException {}

	public void checkKillJigsaw(Class<?> module) {throw new SecurityException("不允许此操作");}
	public void checkOpenModule(Class<?> src_module, String src_package, Class<?> dst_module) {}
}