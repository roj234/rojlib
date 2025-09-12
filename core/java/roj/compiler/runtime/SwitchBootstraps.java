package roj.compiler.runtime;

import roj.ci.annotation.ReferenceByGeneratedClass;

import java.lang.invoke.*;

/**
 * @author Roj234
 * @since 2025/09/12 12:28
 */
public class SwitchBootstraps {
	static final MethodHandle DO_TYPE_SWITCH;
	static {
		try {
			DO_TYPE_SWITCH = MethodHandles.lookup().findStatic(SwitchBootstraps.class, "get", MethodType.methodType(int.class, Object.class, int.class, SwitchMap.class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	public static int get(Object o, int i, SwitchMap map) {
		assert i == 0;
		return map.get(o);
	}

	@ReferenceByGeneratedClass
	public static CallSite typeSwitch(MethodHandles.Lookup lookup,
									  String invocationName,
									  MethodType invocationType,
									  Object... labels) {
		if (invocationType.parameterCount() != 2
				|| (!invocationType.returnType().equals(int.class))
				|| invocationType.parameterType(0).isPrimitive()
				|| !invocationType.parameterType(1).equals(int.class))
			throw new IllegalArgumentException("Illegal invocation type " + invocationType);

		var builder = SwitchMap.Builder.builder(labels.length, false);
		for (int i = 0; i < labels.length; i++) builder.add(labels[i], i);
		var map = builder.build();

		MethodHandle target = MethodHandles.insertArguments(DO_TYPE_SWITCH, 2, map);
		return new ConstantCallSite(target);
	}
}
