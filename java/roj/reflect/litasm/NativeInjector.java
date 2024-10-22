package roj.reflect.litasm;

import roj.asm.type.TypeHelper;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/10/12 0012 17:11
 */
final class NativeInjector implements CodeInjector {
	@Override public void injectCode(Method method, byte[] asm, int len) throws Exception {
		injectCode0(method.getDeclaringClass(), method.getName(), TypeHelper.class2asm(method.getParameterTypes(), method.getReturnType()), asm, len);
	}
	private static native void injectCode0(Class<?> owner, String name, String desc, byte[] asm, int len) throws Exception;
}
