package roj.kscript.type;

import roj.kscript.api.IObject;
import roj.kscript.util.opm.ObjectPropMap;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 17:19
 */
public final class KNull extends KObject {
	public static final KNull NULL = new KNull();

	private KNull() {
		super(new ObjectPropMap() {
			@Override
			public Entry<String, KType> getOrCreateEntry(String id) {
				throw new NullPointerException("null cannot cast to object");
			}

			@Override
			public Entry<String, KType> getEntry(String id) {
				throw new NullPointerException("null cannot cast to object");
			}
		}, null);
	}

	@Override
	public Type getType() {
		return Type.NULL;
	}

	@Override
	public StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append("<null>");
	}

	@Override
	public KType copy() {
		return this;
	}

	@Override
	public boolean equals(Object obj) {
		return obj == NULL;
	}

	@Override
	public int hashCode() {
		return 0;
	}

	@Override
	public boolean isInstanceOf(IObject obj) {
		return false;
	}

	@Override
	public void put(@Nonnull String key, KType entry) {
		throw new NullPointerException("null cannot cast to object");
	}

	@Override
	public KType getOr(String key, KType def) {
		throw new NullPointerException("null cannot cast to object");
	}

	@Override
	public IObject getProto() {
		throw new NullPointerException("null cannot cast to object");
	}

	@Override
	public boolean asBool() {
		return false;
	}
}
