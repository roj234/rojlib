package roj.kscript.type;

import roj.kscript.api.IObject;
import roj.kscript.util.opm.ObjectPropMap;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: KNull.java
 */
public final class KNull extends KObject {
    public static final KNull NULL = new KNull();

    private KNull() {
        super(Type.NULL, new ObjectPropMap() {
            @Override
            protected Entry<String, KType> getOrCreateEntry(String id) {
                throw new NullPointerException("null cannot cast to object");
            }

            @Override
            public Entry<String, KType> getEntry(String id) {
                throw new NullPointerException("null cannot cast to object");
            }
        }, null);
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

    @Override
    public boolean isInstanceOf(IObject map) {
        return false;
    }

    @Override
    public void put(@Nonnull String key, KType entry) {
        throw new NullPointerException("null cannot cast to object");
    }

    @Override
    public KType getOr(String id, KType def) {
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

    @Override
    public KType copy() {
        return this;
    }
}
