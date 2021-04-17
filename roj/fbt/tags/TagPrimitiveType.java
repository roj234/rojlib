package roj.fbt.tags;

import roj.fbt.result.*;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: TagPrimitiveType.java
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
