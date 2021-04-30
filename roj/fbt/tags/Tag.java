package roj.fbt.tags;

import roj.fbt.result.TagResult;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: Tag.java
 */
public interface Tag {
    @Nonnull
    TagResult read(@Nonnull ByteReader reader);

    void write(@Nonnull ByteWriter writer, @Nonnull TagResult result);

    @Nonnull
    TagType getType();

    int getLength();
}
