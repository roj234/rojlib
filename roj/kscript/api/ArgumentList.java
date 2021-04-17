package roj.kscript.api;

import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/27 22:44
 */
public interface ArgumentList {
    @Nonnull
    KType get(int i);

    @Nullable
    KFunction caller();

    int getOr(int i, int def);

    double getOr(int i, double def);

    String getOr(int i, String def);

    boolean getOr(int i, boolean def);

    KType getOr(int i, KType def);

    <T> T getObject(int i, Class<T> t, T def);

    <T> T getObject(int i, Class<T> t);

    StackTraceElement[] trace();
}
