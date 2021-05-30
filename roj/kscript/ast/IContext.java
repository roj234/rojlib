package roj.kscript.ast;

import roj.kscript.type.KType;
import roj.kscript.util.JavaException;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/5/28 22:45
 */
public abstract class IContext {
    abstract void put(String key, KType entry);

    @Nonnull
    public KType get(String key) {
        KType base = getEx(key, null);
        if (base == null)
            throw new JavaException("未定义的 " + key);
        return base;
    }

    abstract KType getEx(String keys, KType def);

    KType getIdx(int index) {
        throw new UnsupportedOperationException();
    }
    void putIdx(int index, KType value) {
        throw new UnsupportedOperationException();
    }
}
