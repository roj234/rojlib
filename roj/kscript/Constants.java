package roj.kscript;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.func.KFuncNative;
import roj.kscript.func.KFunction;
import roj.kscript.type.*;
import roj.kscript.util.opm.ObjectPropMap;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/21 22:45
 */
public final class Constants {
	public static final KObject OBJECT = new KObject(null);

	public static final KObject FUNCTION = new KObject(OBJECT);

	public static final KFunction ARRAY = new KFuncNative() {
		@Override
		public KType invoke(@Nonnull IObject $this, ArgList param) {
			return new KArray(param.getOr(0, 0));
		}
	};

	public static final KFunction TYPEOF = new KFuncNative() {
		@Override
		public KType invoke(@Nonnull IObject $this, ArgList param) {
			return KString.valueOf(param.getOr(0, KUndefined.UNDEFINED).getType().typeof());
		}
	};

	static {
		//OBJECT.put("toString", new KMethodAST(AST.builder().op(LOAD_OBJECT, 0).op(INVOKE_SPECIAL_METHOD, "toString").returnStack().build()));
		OBJECT.put("defineProperty", new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				if (param.size() < 2) throw new IllegalArgumentException("Need 2, get " + param.size());
				ObjectPropMap.Object_defineProperty($this.asKObject(), param.get(0).asString(), param.get(1).asObject());
				return KUndefined.UNDEFINED;
			}
		});
	}
}
