package roj.kscript.type;

import roj.kscript.api.IGettable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/27 22:51
 */
public interface IArray extends Iterable<KType>, KType, IGettable {

    int size();

    @Nonnull
    Iterator<KType> iterator();

    IArray add(@Nullable KType entry);

    void set(int index, @Nullable KType entry);

    @Nonnull
    KType get(int index);

    @Nonnull
    default IArray asArray() {
        return this;
    }

    void addAll(IArray list);

    default List<KType> getRawList() {
        throw new UnsupportedOperationException();
    }

    void clear();
}
