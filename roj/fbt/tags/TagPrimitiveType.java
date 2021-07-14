/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.fbt.tags;

import roj.fbt.result.*;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class TagPrimitiveType implements Tag {
    public TagPrimitiveType(TagType type) {
        this.type = type;
    }

    final TagType type;

    @Nonnull
    @Override
    public TagResult read(@Nonnull ByteReader reader) {
        switch (type) {
            case BYTE:
                return new TagResultByte(reader.readByte());
            case UBYTE:
                return new TagResultShort(reader.readUByte());
            case SHORT:
                return new TagResultShort(reader.readShort());
            case USHORT:
                return new TagResultInt(reader.readUnsignedShort());
            case INT:
                return new TagResultInt(reader.readInt());
            case CHAR:
                return new TagResultChar((char) reader.readUnsignedShort());
            case LONG:
                return new TagResultLong(reader.readLong());
            case UINT:
                return new TagResultLong(reader.readUInt());
            case FLOAT:
                return new TagResultFloat(reader.readFloat());
            case DOUBLE:
                return new TagResultDouble(reader.readDouble());
            default:
                throw new IllegalArgumentException("Unsupported primitive type " + type);
        }
    }

    @Override
    public void write(@Nonnull ByteWriter writer, @Nonnull TagResult result) {
        switch (type) {
            case BYTE:
            case UBYTE:
                writer.writeByte(result.getByte());
                break;
            case SHORT:
            case USHORT:
            case CHAR:
                writer.writeShort(result.getShort());
                break;
            case INT:
            case UINT:
                writer.writeInt(result.getInt());
                break;
            case LONG:
                writer.writeLong(result.getLong());
                break;
            case FLOAT:
                writer.writeFloat(result.getFloat());
                break;
            case DOUBLE:
                writer.writeDouble(result.getDouble());
        }
    }

    @Nonnull
    @Override
    public TagType getType() {
        return type;
    }

    @Override
    public int getLength() {
        return type.getLength();
    }
}
