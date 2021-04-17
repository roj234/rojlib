package roj.fbt.tags;

import roj.fbt.result.TagResult;
import roj.fbt.result.TagResultByteArray;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: TagByteArray.java
 */
public class TagByteArray implements Tag {
    public TagByteArray(int length) {
        this.length = length;
    }

    final int length;

    @Nonnull
    @Override
    public TagResult read(@Nonnull ByteReader reader) {
        return new TagResultByteArray(reader.readBytes(length));
    }

    @Override
    public void write(@Nonnull ByteWriter writer, @Nonnull TagResult result) {
        byte[] bytes = result.getByteArray();
        if (bytes.length != this.length) {
            bytes = Arrays.copyOf(bytes, this.length);
        }
        writer.writeBytes(bytes);
    }

    @Nonnull
    @Override
    public TagType getType() {
        return TagType.BYTE_ARRAY;
    }

    @Override
    public int getLength() {
        return length;
    }
}
