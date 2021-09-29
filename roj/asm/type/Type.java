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
import roj.collect.CharMap;
import roj.concurrent.OperationDone;
import roj.text.CharList;

import static roj.asm.type.NativeType.*;

/**
 * 类型
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class Type implements IType, IGeneric {
    private static final CharMap<Type> STD = new CharMap<>(9);

    public static synchronized Type std(char c) {
        Type type = STD.get(c);
        if(type == null) {
            STD.put(c, type = new Type(c));
        }
        if(type.array != 0) {
            throw new IllegalArgumentException("Std type " + c + " has been changed.");
        }
        return type;
    }

    /**
     * Array正常不会出现
     */
    public final char type;
    public String owner;
    public int array;

    public Type(char type) {
        this(type, 0);
    }

    /**
     * TYPE_OTHER
     */
    public Type(char type, int array) {
        this.type = NativeType.validate(type);
        this.array = array;
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
        this.array = array;
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
    public void appendString(CharList sb) {
        if (this.owner != null) {
            sb.append(owner);
        } else {
            sb.append(NativeType.toString(type));
        }
        for (int i = 0; i < this.array; i++)
            sb.append("[]");
    }

    public int length() {
        return (array == 0 && (type == NativeType.LONG || type == NativeType.DOUBLE)) ? 2 : 1;
    }

    public String nativeName() {
        switch (type) {
            case CLASS:
                return "A";
            case VOID:
                return "";//'V';
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
        CharList sb = new CharList();
        appendString(sb);
        return sb.toString();
    }

    public Class<?> toJavaClass() throws ClassNotFoundException {
        switch (type) {
            case CLASS:
                return Class.forName(ParamHelper.normalize(owner, array), false, null);
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
}
