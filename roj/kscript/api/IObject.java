package roj.kscript.api;

import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.type.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/10/27 13:11
 */
public interface IObject extends KType {
	void put(String key, KType entry);

	@Nullable
	default KType getOrNull(String key) {
		return getOr(key, null);
	}

	@Nonnull
	default KType get(String key) {
		return getOr(key, KUndefined.UNDEFINED);
	}

	default boolean chmod(String key, boolean configurable, boolean enumerable, KFunction getter, KFunction setter) {
		return false;
	}

	default boolean delete(String key) {
		return false;
	}

	default boolean containsKey(@Nullable String key) {
		return getOr(key, null) != null;
	}

	default boolean containsKey(@Nullable String key, @Nonnull Type type) {
		return get(key).canCastTo(type);
	}

	boolean isInstanceOf(IObject obj);

	IObject getProto();

	KType getOr(String key, KType def);

	int size();

	default Object getInternal() {
		throw new UnsupportedOperationException();
	}

	@Nonnull
	@Override
	default String asString() {
		return "[object " + getClass().getSimpleName() + ']';
	}

	@Nonnull
	@Override
	default IObject asObject() {
		return this;
	}
}
