package roj.kscript.func;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/27 13:01
 */
public abstract class KFuncNative extends KFunction {
	public KFuncNative() {
		clazz = getClass().getName();
	}

	@Override
	public abstract KType invoke(@Nonnull IObject $this, ArgList param);

	@Override
	public StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append("function ").append(name).append("(){ [Native code] }");
	}
}
