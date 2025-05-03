package roj.reflect.litasm;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/10/12 16:18
 */
public interface CodeInjector {
	void injectCode(Method method, byte[] asm, int len) throws Exception;
}
