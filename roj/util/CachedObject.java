/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: CachedObject.java
 */
package roj.util;

import javax.annotation.Nonnull;
import java.lang.ref.SoftReference;
import java.util.function.Supplier;

public class CachedObject<E> {
    private final Supplier<E> callable;
    private SoftReference<E> reference;

    public CachedObject(Supplier<E> getter) {
        this.callable = getter;
        this.reference = new SoftReference<>(null);
    }

    @Nonnull
    public E get() {
        E t = this.reference.get();
        if (t == null) {
            t = this.callable.get();

            if (t == null) {
                throw new IllegalArgumentException();
            }

            this.reference = new SoftReference<>(t);
        }

        return t;
    }
}
