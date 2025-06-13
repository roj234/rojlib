package roj.reflect;

import roj.util.Helpers;

import java.util.Collections;
import java.util.function.BiFunction;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/6/4 6:09
 */
public final class GetCallerInstance {
	private static final long LOCALS;
	private static final StackWalker LIVE;

	static {
		try {
			LOCALS = Unaligned.fieldOffset(Class.forName("java.lang.LiveStackFrameInfo"), "locals");

			// simple java.lang.LiveStackFrame.getStackWalker() without SM checks
			var optionType = Class.forName("java.lang.StackWalker$ExtendedOption");
			var localsAndOperands = U.getObject(optionType, Unaligned.fieldOffset(optionType, "LOCALS_AND_OPERANDS"));
			BiFunction<Object, Object, StackWalker> fn = Helpers.cast(Bypass.builder(BiFunction.class).weak().delegate_o(StackWalker.class, "newInstance", "apply").build());
			LIVE = fn.apply(Collections.emptySet(), localsAndOperands);
		} catch (Exception e) {
			throw new IllegalStateException("非常抱歉，由于使用了大量内部API，这个类无法兼容你的JVM", e);
		}
	}

	/**
	 * 获取方法调用者的实例，如果对应方法是静态的，那么结果是未定义的
	 *
	 * @param skip 往回数的栈帧数目
	 * @return 调用者的实例
	 */
	public static Object getCallerInstance(int skip) {
		return LIVE.walk(stream -> {
			var frame = stream.skip(skip).findFirst().orElse(null);
			return frame == null ? null : ((Object[]) U.getObject(frame, LOCALS))[0];
		});
	}
}
