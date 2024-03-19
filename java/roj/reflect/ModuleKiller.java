package roj.reflect;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/1/21 0021 23:19
 */
public class ModuleKiller {
	public static void main(String[] args) throws Exception {
		KillModuleSince();
		Class<?> c = Class.forName(args[0]);
		Method m = c.getMethod("main", String[].class);
		String[] args2 = new String[args.length-1];
		System.arraycopy(args, 1, args2, 0, args2.length);
		m.invoke(null, (Object) args2);
	}

	public static void KillModuleSince() {
		Module module = Object.class.getModule();
		for (Module module_ : module.getLayer().modules()) {
			for (String package_ : module_.getDescriptor().packages()) {
				VMInternals.OpenModule(module_, package_, ModuleKiller.class.getModule());
			}
		}
	}
}