package roj.reflect.litasm;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/10/12 0012 16:18
 */
interface CodeInjector {
	void injectCode(Method method, byte[] asm, int len) throws Exception;
}