package roj.fbt.tags;

import roj.fbt.result.TagResult;
import roj.fbt.result.TagResultString;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: TagString.java
 */
public class TagString implements Tag {
    public TagString(int maxLength) {
        this.maxLength = maxLength;
        if ((this.maxLength & ~255) != 0) {
            this.len = 1;
        } else if ((this.maxLength & ~65535) != 0) {
            this.len = 2;
        } else {
            this.len = 4;
        }
    }

    final int maxLength;
    final byte len;

    @Nonnull
    public String read0(@Nonnull ByteReader reader) {
        int sLen = 0;
        String string;
        switch (len) {
            case 1:
                sLen = reader.readUByte();
                break;
            case 2:
                sLen = reader.readUnsignedShort();
                break;
            case 4:
                sLen = reader.readInt();
                break;
        }
        if (sLen <= 0) {
            string = "";
            sLen = 0;
        } else {
            byte[] bytes = reader.readBytes(sLen);
            string = new String(bytes, StandardCharsets.UTF_8);
        }
        reader.index += this.maxLength - sLen;
        return string;
    }

    @Nonnull
    @Override
    public TagResult read(@Nonnull ByteReader reader) {
        return new TagResultString(read0(reader));
    }

    public void write(@Nonnull ByteWriter writer, @Nonnull String string) {
        int fullIndex = writer.list.pos() + this.len + this.maxLength;
        byte[] arr = string.getBytes(StandardCharsets.UTF_8);
        if (arr.length == 0) {
            writer.list.pos(fullIndex);
            return;
        }
        if (arr.length > maxLength) {
            throw new StringIndexOutOfBoundsException(arr.length);
        }
        switch (len) {
            case 1:
                writer.writeByte((byte) arr.length);
                break;
            case 2:
                writer.writeShort(arr.length);
                break;
            case 4:
                writer.writeInt(arr.length);
                break;
        }
        writer.writeBytes(arr);
        writer.list.pos(fullIndex);
    }

    @Override
    public void write(@Nonnull ByteWriter writer, @Nonnull TagResult result) {
        write(writer, result.getString());
    }

    @Nonnull
    @Override
    public TagType getType() {
        return TagType.STRING;
    }

    @Override
    public int getLength() {
        return this.len + this.maxLength;
    }
}
