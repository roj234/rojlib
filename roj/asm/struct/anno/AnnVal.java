/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValue.java
 */
package roj.asm.struct.anno;

import roj.asm.cst.*;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.text.StringPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

public abstract class AnnVal {
    AnnVal(char type) {
        this.type = type;
    }

    public final char type;

    public static AnnVal parse(ConstantPool pool, ByteReader r) {
        char type = AnnotationType.verify(r.readUByte());

        switch (type) {
            case AnnotationType.BOOLEAN:
            case AnnotationType.BYTE:
            case AnnotationType.SHORT:
            case AnnotationType.CHAR:
            case AnnotationType.INT:
            case AnnotationType.DOUBLE:
            case AnnotationType.FLOAT:
            case AnnotationType.LONG:
            case AnnotationType.STRING:
            case AnnotationType.CLASS:
                return parsePrimitive(pool, r, type);
            case AnnotationType.ENUM:
                return new roj.asm.struct.anno.AnnValEnum(((CstUTF) pool.get(r)).getString(), ((CstUTF) pool.get(r)).getString());
            case AnnotationType.ANNOTATION:
                return new AnnValAnnotation(Annotation.deserialize(pool, r));
            case AnnotationType.ARRAY:
                int len = r.readUnsignedShort();
                List<AnnVal> annos = new ArrayList<>();
                while (len > 0) {
                    annos.add(parse(pool, r));
                    len--;
                }
                return new AnnValArray(annos);
        }
        return null;
    }

    public static AnnVal parsePrimitive(ConstantPool pool, ByteReader r, char type) {
        Constant c = pool.get(r);
        switch (type) {
            case AnnotationType.BOOLEAN:
            case AnnotationType.BYTE:
            case AnnotationType.SHORT:
            case AnnotationType.CHAR:
            case AnnotationType.INT:
                return new AnnValInt(type, ((CstInt) c).value);
            case AnnotationType.DOUBLE:
                return new AnnValDouble(((CstDouble) c).value);
            case AnnotationType.FLOAT:
                return new AnnValFloat(((CstFloat) c).value);
            case AnnotationType.LONG:
                return new AnnValLong(((CstLong) c).value);
            case AnnotationType.STRING:
                return new AnnValString(((CstUTF) c).getString());
            case AnnotationType.CLASS:
                return new AnnValClass(((CstUTF) c).getString());
        }
        return null;
    }

    public static AnnVal deserialize(StringPool pool, ByteReader r) {
        char type = AnnotationType.verify(r.readUByte());

        switch (type) {
            case AnnotationType.BOOLEAN:
            case AnnotationType.BYTE:
            case AnnotationType.SHORT:
            case AnnotationType.CHAR:
            case AnnotationType.INT:
                return new AnnValInt(type, r.readVarInt());
            case AnnotationType.DOUBLE:
                return new AnnValDouble(r.readDouble());
            case AnnotationType.FLOAT:
                return new AnnValFloat(r.readFloat());
            case AnnotationType.LONG:
                return new AnnValLong(r.readLong());
            case AnnotationType.STRING:
                return new AnnValString(pool.readString(r));
            case AnnotationType.CLASS:
                return new AnnValClass(pool.readString(r));
            case AnnotationType.ENUM:
                return new AnnValEnum(pool.readString(r), pool.readString(r));
            case AnnotationType.ANNOTATION:
                return new AnnValAnnotation(Annotation.deserialize(pool, r));
            case AnnotationType.ARRAY:
                int len = r.readVarInt(false);
                List<AnnVal> entries = new ArrayList<>(len);
                while (len > 0) {
                    entries.add(deserialize(pool, r));
                    len--;
                }
                return new AnnValArray(entries);
        }
        return null;
    }

    public final void toByteArray(ConstantWriter pool, ByteWriter w) {
        _toByteArray(pool, w.writeByte((byte) type));
    }

    public final void toByteArray(StringPool pool, ByteWriter w) {
        _toByteArray(pool, w.writeByte((byte) type));
    }

    abstract void _toByteArray(StringPool pool, ByteWriter w);

    abstract void _toByteArray(ConstantWriter pool, ByteWriter w);

    public abstract String toString();
}