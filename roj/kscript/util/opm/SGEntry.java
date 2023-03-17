package roj.kscript.util.opm;

import roj.kscript.Arguments;
import roj.kscript.func.KFunction;
import roj.kscript.type.KNull;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/26 18:10
 */
public final class SGEntry extends KOEntry {
	public KFunction set;
	Arguments arg;

	// get = value
	SGEntry(String k, KType v) {
		super(k, v);
	}

	@Override
	public KType getValue() {
		if (v == null) return KUndefined.UNDEFINED;

		if (arg == null) {
			arg = new Arguments(new OneList<>(null));
		}
		((OneList<KType>) arg.argv).setEmpty(true);

		return v.asFunction().invoke(KNull.NULL, arg);
	}

	@Override
	public KType setValue(KType latest) {
		if (set == null) return getValue();

		if (arg == null) {
			arg = new Arguments(new OneList<>(null));
		}

		OneList<KType> argv = (OneList<KType>) arg.argv;
		argv.setEmpty(false);
		argv.set(0, latest);

		return set.invoke(KNull.NULL, arg);
	}
}
