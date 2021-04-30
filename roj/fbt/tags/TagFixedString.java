package roj.fbt.tags;

import roj.fbt.result.TagResult;
import roj.fbt.result.TagResultString;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: TagFixedString.java
 */
public class TagFixedString implements Tag {
    public TagFixedString(int maxLength) {
        this.length = maxLength;
    }

    final int length;

    @Nonnull
    @Override
    public TagResult read(@Nonnull ByteReader reader) {
        return new TagResultString(new String(reader.readBytes(length), StandardCharsets.UTF_8));
    }

    @Override
    public void write(@Nonnull ByteWriter writer, @Nonnull TagResult result) {
        String string = result.getString();
        int fullIndex = writer.list.pos() + this.length;
        byte[] arr = string.getBytes(StandardCharsets.UTF_8);
        writer.writeBytes(arr);
        writer.list.pos(fullIndex);
    }

    @Nonnull
    @Override
    public TagType getType() {
        return TagType.STRING;
    }

    @Override
    public int getLength() {
        return this.length;
    }
}
