package roj.kscript.func;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/27 13:01
 */
public abstract class KInitializer extends KFunction {
	public KInitializer() {
		super();
		clazz = getClass().getName();
	}

	@Override
	public KType invoke(@Nonnull IObject $this, ArgList param) {
		return KUndefined.UNDEFINED;
	}

	@Override
	public StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append("function ").append(name).append("(){ [Native code] }");
	}

	@Override
	public abstract KType createInstance(ArgList args);
}
