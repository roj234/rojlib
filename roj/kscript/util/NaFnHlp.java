package roj.kscript.util;

import roj.kscript.Constants;
import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.func.KFuncNative;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/10/31 0:06
 */
public final class NaFnHlp {
	private final KObject object;
	private final NaFnHlp parent;

	private NaFnHlp(NaFnHlp parent) {
		this.object = new KObject(parent == null ? null : Constants.OBJECT);
		this.parent = parent;
	}

	public static NaFnHlp builder() {
		return new NaFnHlp(null);
	}

	public NaFnHlp returnVoid(String name, Consumer<ArgList> consumer) {
		return with(name, new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				consumer.accept(param);
				return KUndefined.UNDEFINED;
			}
		});
	}

	public NaFnHlp with(String name, KFuncNative func) {
		object.put(name, func);
		return this;
	}

	public NaFnHlp put(String name, KType some) {
		object.put(name, some);
		return this;
	}

	public NaFnHlp sub() {
		return new NaFnHlp(this);
	}

	public NaFnHlp endSub(String name) {
		parent.object.put(name, object);
		return parent;
	}

	public KObject build() {
		return object;
	}
}
