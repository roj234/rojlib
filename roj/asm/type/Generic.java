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
import roj.io.IOUtil;
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
 * @since 2021/6/18 9:51
 */
public class Generic implements IGeneric {
    public static final byte
            TYPE_TYPE_PARAM         = 0,
            TYPE_INHERIT_TYPE_PARAM = 1,
            TYPE_CLASS              = 2,
            TYPE_INHERIT_CLASS      = 3,
            TYPE_SUB_CLASS          = 4,

            EX_NONE = 0,
            EX_SUPERS = 1,
            EX_EXTENDS = -1;

    public final byte type;
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
            appendCategory(sb);
            sb.append(owner);
            if (children != null && !children.isEmpty()) {
                sb.append('<');
                for (int i = 0; i < children.size(); i++) {
                    IGeneric child = children.get(i);
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

    private void appendCategory(CharList sb) {
        if (type == TYPE_SUB_CLASS) {
            sb.append('.');
            return;
        }

        if (extendType != 0) sb.append(extendType == EX_SUPERS ? '-' : '+');
        if ((type & 1) != 0) sb.append(':');
        for (int i = 0; i < array; i++) sb.append('[');

        switch (type) {
            case TYPE_CLASS:
            case TYPE_INHERIT_CLASS:
                sb.append('L');
                return;
            case TYPE_TYPE_PARAM:
            case TYPE_INHERIT_TYPE_PARAM:
                sb.append('T');
                return;
        }
        throw new IllegalArgumentException(String.valueOf(type));
    }

    public void rename(UnaryOperator<String> fn) {
        if (type != TYPE_TYPE_PARAM && type != TYPE_INHERIT_TYPE_PARAM && type != TYPE_SUB_CLASS)
            owner = fn.apply(owner);
        if (subClass != null) {
            String tmp = fn.apply(owner + '$' + subClass.owner);
            int idx = tmp.indexOf('$');
            owner = tmp.substring(0, idx);
            subClass.owner = tmp.substring(idx + 1);
        }
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                Signature.rename0(fn, children.get(i));
            }
        }
    }

    public void appendString(CharList sb, Signature fn) {
        switch (extendType) {
            case EX_SUPERS:
                sb.append("? supers ");
                break;
            case EX_EXTENDS:
                sb.append("? extends ");
                break;
        }
        if (owner.equals("*")) {
            sb.append('?');
        } else if (fn != null && (type == TYPE_TYPE_PARAM || type == TYPE_INHERIT_TYPE_PARAM)) {
            fn.appendTypeParameter(sb, owner, null);
        } else {
            int start = owner.lastIndexOf('/') + 1;
            sb.append(owner, start, owner.length());
        }
        if (children != null && !children.isEmpty()) {
            sb.append('<');
            int i = 0;
            while (true) {
                children.get(i++).appendString(sb, fn);
                if (i == children.size()) break;
                sb.append(", ");
            }
            sb.append('>');
        }
        if (subClass != null) {
            subClass.appendString(sb.append('.'), fn);
        }
        for (int i = 0; i < array; i++) {
            sb.append("[]");
        }
    }

    public String toString() {
        CharList cl = IOUtil.SharedUTFCoder.get().charBuf;
        cl.clear();

        appendString(cl, null);
        return cl.toString();
    }
}