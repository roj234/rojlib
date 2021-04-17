package roj.kscript.type;

import javax.annotation.Nonnull;
import java.util.Collections;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLNull.java
 */
public final class KNull extends KObject {
    public static final KNull NULL = new KNull();

    private KNull() {
        super(Type.NULL, Collections.emptyMap(), null);
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("null");
    }

    @Override
    public boolean equals(Object obj) {
        return obj == NULL;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    public void put(@Nonnull String key, KType entry) {
        throw new NullPointerException("null is not object");
    }

    protected KType getPrototyped(String keys, KType defaultValue) {
        throw new NullPointerException("null is not object");
    }

    @Override
    public boolean asBoolean() {
        return false;
    }

    @Override
    public KType copy() {
        return this;
    }
}
