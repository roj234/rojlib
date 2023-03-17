package roj.kscript.func.gen;

import roj.asm.tree.attr.AttrCode;
import roj.concurrent.OperationDone;
import roj.kscript.api.IObject;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2020/10/17 18:12
 */
public final class JavaBridge {
	static AtomicInteger clazzId = new AtomicInteger();


	private void initHolder() {

	}

	private void forReuse(String className, String methodOwner) {
	}

	public IObject createJavaFn(Class<?> clazz) {
		return null;
	}

	private void addMethod(Method value, AttrCode call, int id) {

	}

	public static IObject createReflectiveJavaFunction(Class<?> clazz) {
		throw OperationDone.NEVER;
	}
}
