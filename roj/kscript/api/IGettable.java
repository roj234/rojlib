package roj.kscript.api;

import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.type.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/27 13:11
 */
public interface IGettable extends KType {
    void put(@Nonnull String key, KType entry);

    @Nullable
    default KType getOrNull(String key) {
        return getOr(key, null);
    }

    @Nonnull
    default KType get(String key) {
        return getOr(key, KUndefined.UNDEFINED);
    }

    default boolean containsKey(@Nullable String key) {
        return getOrNull(key) != null;
    }

    default boolean containsKey(@Nullable String key, @Nonnull Type type) {
        return get(key).canCastTo(type);
    }

    boolean isInstanceOf(IGettable map);

    IGettable getPrototype();

    KType getOr(String id, KType kb);

    int size();

    // todo
    default Map<String, KType> getInternalMap() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    default IGettable asObject() {
        return this;
    }
}
