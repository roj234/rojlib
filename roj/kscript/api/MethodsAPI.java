package roj.kscript.api;

import roj.kscript.parser.ast.DedicatedMethod;
import roj.kscript.parser.ast.Method;
import roj.kscript.type.KType;

import java.util.List;

/**
 * @author Roj234
 * @since 2020/10/14 22:46
 */
public abstract class MethodsAPI {
	public static final ThreadLocal<MethodsAPI> PF = new ThreadLocal<>();

	public static DedicatedMethod getDedicated(CharSequence name, Method method) {
		MethodsAPI inst = PF.get();
		if (inst == null) {
			return null;
		} else {
			Computer computer = inst.getDedicated0(name);
			return computer == null ? null : new DedicatedMethod(method.args, computer);
		}
	}

	public static KType preCompute(CharSequence name, List<KType> cst) {
		MethodsAPI inst = PF.get();
		return inst == null ? null : inst.preCompute0(name, cst);
	}

	protected abstract Computer getDedicated0(CharSequence name);

	protected abstract KType preCompute0(CharSequence name, List<KType> cst);
}
