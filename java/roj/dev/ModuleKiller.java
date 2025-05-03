package roj.dev;

import roj.reflect.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/1/21 23:19
 */
public class ModuleKiller {
	public static void main(String[] args) throws Exception {
		Class<?> c = Class.forName(args[0]);
		ReflectionUtils.killJigsaw(c);
		Method m = c.getMethod("main", String[].class);
		String[] args2 = new String[args.length-1];
		System.arraycopy(args, 1, args2, 0, args2.length);
		m.invoke(null, (Object) args2);
	}
}