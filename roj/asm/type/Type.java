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
package roj.asm.type;

import roj.asm.util.IGeneric;
import roj.asm.util.IType;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.text.CharList;

import javax.annotation.Nonnull;

/**
 * 类型
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Type implements IType, IGeneric {
    public static final char ARRAY = '[', CLASS = 'L', VOID = 'V', BOOLEAN = 'Z', BYTE = 'B', CHAR = 'C', SHORT = 'S', INT = 'I', FLOAT = 'F', DOUBLE = 'D', LONG = 'J';

    @Nonnull
    public static String toDesc(int type) {
        switch (type) {
            case ARRAY:
                return "[";
            case CLASS:
                return "L";
            case VOID:
                return "V";
            case BOOLEAN:
                return "Z";
            case BYTE:
                return "B";
            case CHAR:
                return "C";
            case SHORT:
                return "S";
            case INT:
                return "I";
            case FLOAT:
                return "F";
            case DOUBLE:
                return "D";
            case LONG:
                return "J";
        }
        // noinspection all
        return null;
    }

    @Nonnull
    public static String toString(byte type) {
        switch (type) {
            case ARRAY:
                return "array";
            case CLASS:
                return "class";
            case VOID:
                return "void";
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case CHAR:
                return "char";
            case SHORT:
                return "short";
            case INT:
                return "int";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case LONG:
                return "long";
        }
        // noinspection all
        return null;
    }

    public static boolean isValid(int c) {
        // noinspection all
        return toDesc(c) != null;
    }

    public static byte validate(int c) {
        // noinspection all
        if (toDesc(c) == null)
            throw new IllegalArgumentException("Illegal native type desc " + c);
        return (byte) c;
    }

    private static final Type[] STD = new Type[] {
            new Type(VOID), new Type(BOOLEAN), new Type(BYTE), new Type(CHAR), new Type(SHORT),
            new Type(INT), new Type(FLOAT), new Type(DOUBLE), new Type(LONG)
    };

    public static Type std(int c) {
        switch (c) {
            case VOID:
                return STD[0];
            case BOOLEAN:
                return STD[1];
            case BYTE:
                return STD[2];
            case CHAR:
                return STD[3];
            case SHORT:
                return STD[4];
            case INT:
                return STD[5];
            case FLOAT:
                return STD[6];
            case DOUBLE:
                return STD[7];
            case LONG:
                return STD[8];
            default:
                return null;
        }
    }

    /**
     * Array正常不会出现
     */
    public final byte type;
    public String owner;
    public char array;

    public Type(char type) {
        this(type, 0);
    }

    /**
     * TYPE_OTHER
     */
    public Type(int type, int array) {
        this.type = validate(type);
        this.array = (char) array;
    }

    public Type(String type) {
        this(type, 0);
    }

    /**
     * TYPE_CLASS
     */
    public Type(String owner, int array) {
        this.type = CLASS;
        this.owner = owner;
        this.array = (char) array;
    }

    @Override
    public boolean isGeneric() {
        return false;
    }

    @Override
    public String toGeneric() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void appendGeneric(CharList sb) {
        ParamHelper.getOne(this, sb);
    }

    @Override
    public void appendString(CharList sb, Signature parent) {
        if (this.owner != null) {
            sb.append(owner);
        } else {
            sb.append(toString(type));
        }
        for (int i = 0; i < this.array; i++)
            sb.append("[]");
    }

    public int length() {
        return (array == 0 && (type == LONG || type == DOUBLE)) ? 2 : 1;
    }

    public String nativeName() {
        if (array > 0) return "A";
        switch (type) {
            case CLASS:
                return "A";
            case VOID:
                return "";
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
                return "I";
            case FLOAT:
                return "F";
            case DOUBLE:
                return "D";
            case LONG:
                return "L";
        }
        throw OperationDone.NEVER;
    }

    public String toString() {
        CharList sb = IOUtil.getSharedCharBuf();
        appendString(sb, null);
        return sb.toString();
    }

    public Class<?> toJavaClass() throws ClassNotFoundException {
        switch (type) {
            case CLASS:
                String cn = ParamHelper.asm2class(owner, array);
                try {
                    return Class.forName(cn, false, null);
                } catch (ClassNotFoundException e) {
                    return Class.forName(cn, false, getClass().getClassLoader());
                }
            case VOID:
                return void.class;
            case BOOLEAN:
                return boolean.class;
            case BYTE:
                return byte.class;
            case CHAR:
                return char.class;
            case SHORT:
                return short.class;
            case INT:
                return int.class;
            case FLOAT:
                return float.class;
            case DOUBLE:
                return double.class;
            case LONG:
                return long.class;
        }
        throw new IllegalArgumentException("?");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Type type1 = (Type) o;

        if (type != type1.type) return false;
        if (array != type1.array) return false;
        return type != CLASS || owner.equals(type1.owner);
    }

    @Override
    public int hashCode() {
        int result = type;
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + array;
        return result;
    }
}
