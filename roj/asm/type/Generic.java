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
import roj.text.CharList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 泛型类型
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class Generic implements IGeneric {
    public static final byte TYPE_TYPE_PARAM = 0,
            TYPE_INTERFACE = 1,
            TYPE_CLASS = 2,
            TYPE_SUB_CLASS = 3,

            EX_NONE = 0,
            EX_SUPERS = 1,
            EX_EXTENDS = -1;

    public final byte type;
    @Nonnull
    public String owner;
    @Nullable
    public Generic subClass;
    public int array;
    public byte extendType;
    @Nullable
    public List<IGeneric> children;

    public Generic(byte type, @Nonnull String owner, int array, byte extendType) {
        this.type = type;
        this.owner = owner;
        this.array = array;
        this.extendType = extendType;
    }

    public void addChild(IGeneric child) {
        if (children == null) {
            children = new LinkedList<>();
        }
        //if(child.arrayLevel != 0)
        //    throw new IllegalArgumentException("Child couldn't be array");
        //if(child.subClass)
        //    throw new IllegalArgumentException("Child couldn't be subClass");

        children.add(child);
    }

    public void appendGeneric(CharList sb) {
        if (owner.equals("*")) {
            sb.append('*');
        } else {
            sb.append(getCatDesc());
            sb.append(owner);
            if (children != null && !children.isEmpty()) {
                sb.append('<');
                for (IGeneric child : children) {
                    child.appendGeneric(sb);
                }
                sb.append('>');
            }
            if (subClass != null) {
                subClass.appendGeneric(sb);
            } else {
                sb.append(';');
            }
        }
    }

    private char[] getCatDesc() {
        if (type == TYPE_SUB_CLASS)
            return new char[]{'.'};
        int arrLen = array + 1 + (this.type == TYPE_INTERFACE ? 1 : 0) + (extendType != 0 ? 1 : 0);
        char[] chars = new char[arrLen];

        int i = 0;
        if (extendType != 0)
            chars[i++] = getClassMark();
        if (type == TYPE_INTERFACE)
            chars[i++] = ':';
        for (; i < arrLen - 1; i++) {
            chars[i] = '[';
        }

        switch (type) {
            case TYPE_CLASS:
            case TYPE_INTERFACE:
                chars[arrLen - 1] = 'L';
                return chars;
            case TYPE_TYPE_PARAM:
                chars[arrLen - 1] = 'T';
                return chars;
        }
        throw new IllegalArgumentException(String.valueOf(type));
    }

    private char getClassMark() {
        switch (extendType) {
            case EX_SUPERS:
                return '-';
            case EX_EXTENDS:
                return '+';
        }
        throw new IllegalArgumentException("classType");
    }

    protected void rename(UnaryOperator<String> renameFunction) {
        if (type != TYPE_TYPE_PARAM & type != TYPE_SUB_CLASS)
            owner = renameFunction.apply(owner);
        if (subClass != null) {
            String tmp = renameFunction.apply(owner + '$' + subClass.owner);
            int idx = tmp.indexOf('$');
            owner = tmp.substring(0, idx);
            subClass.owner = tmp.substring(idx + 1);
        }
        if (children != null) {
            for (IGeneric value : children) {
                Signature.rename0(renameFunction, value);
            }
        }
    }

    public void appendString(CharList sb) {
        switch (extendType) {
            case EX_SUPERS:
                sb.append("? supers ");
                break;
            case EX_EXTENDS:
                sb.append("? extends ");
                break;
        }
        sb.append(owner.equals("*") ? "?" : owner.substring(owner.lastIndexOf('/') + 1));
        if (children != null && !children.isEmpty()) {
            sb.append('<');
            for (IGeneric child : children) {
                child.appendString(sb);
                sb.append(", ");
            }
            sb.setIndex(sb.length() - 2);
            sb.append('>');
        }
        if (subClass != null) {
            subClass.appendString(sb.append('.'));
        }
        for (int i = 0; i < array; i++) {
            sb.append("[]");
        }
    }

    public String toString() {
        CharList cl = new CharList();
        appendString(cl);
        return cl.toString();
    }
}