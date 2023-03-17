package roj.kscript.type;

import roj.kscript.func.BindThis;
import roj.kscript.func.KFunction;
import roj.kscript.util.opm.ObjectPropMap;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/27 23:41
 */
public final class KInstance extends KObject {
	final KFunction constructor;

	@Override
	public boolean canCastTo(Type type) {
		return type == Type.OBJECT;
	}

	public KInstance(KFunction cst, KObject proto) {
		super(new ObjectPropMap(), proto);
		for (Map.Entry<String, KType> entry : proto.getInternal().entrySet()) {
			String key = entry.getKey();
			KType value = entry.getValue();

			if (value.getType() == Type.FUNCTION) map.put(key, new BindThis(this, value.asFunction()));
		}

		this.constructor = cst;
	}

	@Override
	public KType getOr(String key, KType def) {
		return key.equals("constructor") ? constructor : super.getOr(key, def);
	}
}
