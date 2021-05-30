package roj.kscript.api;

import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/27 22:51
 */
public interface IArray extends Iterable<KType>, KType, IObject {
    //int size();

    //@Nonnull
    //Iterator<KType> iterator();

    void add(@Nullable KType entry);

    void set(int index, @Nullable KType entry);

    @Nonnull
    KType get(int index);

    @Nonnull
    default IArray asArray() {
        return this;
    }

    void addAll(IArray list);

    default List<KType> getInternal() {
        throw new UnsupportedOperationException();
    }

    void clear();
}
