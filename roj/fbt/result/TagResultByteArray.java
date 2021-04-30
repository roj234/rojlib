package roj.fbt.result;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: TagResultString.java
 */
public class TagResultByteArray implements TagResult {
    public byte[] result;

    public TagResultByteArray(byte[] result) {
        this.result = result;
    }

    @Override
    public byte[] getByteArray() {
        return this.result;
    }

    @Nonnull
    @Override
    public TagResultType getType() {
        return TagResultType.BYTE_ARRAY;
    }
}
