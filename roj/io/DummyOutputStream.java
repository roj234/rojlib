package roj.io;

import javax.annotation.Nonnull;
import java.io.OutputStream;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: DummyOutputStream.java
 */
public final class DummyOutputStream extends OutputStream {
    public static final DummyOutputStream INSTANCE = new DummyOutputStream();

    public DummyOutputStream() {

    }

    @Override
    public void write(int i) {

    }

    public void write(@Nonnull byte[] var1, int var2, int var3) {

    }
}
