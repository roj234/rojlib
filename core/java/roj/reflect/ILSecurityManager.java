package roj.reflect;

import roj.asm.type.TypeHelper;

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

	public void checkField(Class<?> recv, String name, Class<?> type, Class<?> caller) {
		checkAccess(TypeHelper.class2asm(recv), name, TypeHelper.class2asm(type), caller);
	}
	public void checkMethod(Class<?> recv, String name, Class<?>[] arguments, Class<?> returnType, Class<?> caller) {
		checkAccess(TypeHelper.class2asm(recv), name, TypeHelper.class2asm(arguments, returnType), caller);
	}
	public void checkAccess(String owner, String name, String desc, Class<?> caller) {}

	public void checkKillJigsaw(Class<?> module) {throw new SecurityException("不允许此操作");}
	public void checkOpenModule(Class<?> src_module, String src_package, Class<?> dst_module) {}
}