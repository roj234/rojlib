package roj.staging;

import roj.reflect.Reflection;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author Roj234
 * @since 2024/1/21 23:19
 */
public class ModuleKiller {
	public static void main(String[] args) throws Throwable {
		Class<?> c = Class.forName(args[0]);
		Reflection.killJigsaw(c);

		String[] args2 = new String[args.length-1];
		System.arraycopy(args, 1, args2, 0, args2.length);
		MethodHandles.lookup().findStatic(c, "main", MethodType.methodType(void.class, String[].class)).invoke(args2);
	}
}