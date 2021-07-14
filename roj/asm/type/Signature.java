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

import roj.asm.util.IType;
import roj.collect.MyHashMap;
import roj.text.CharList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class Signature implements IType {
    public Map<String, Collection<Generic>> genericTypeMap;

    public static final byte METHOD = 1,
            FIELD_OR_CLASS = 0,
            CLASS = -1;

    /**
     * Contract: values[0] is class, other is interface
     */
    public final List<IType> values;
    public IType returns;
    public final byte type;
    public List<IType> throwsException;

    public Signature(int type) {
        this.genericTypeMap = new MyHashMap<>();
        this.values = new ArrayList<>();
        this.type = (byte) type;
    }

    public Signature(Map<String, Collection<Generic>> genericTypeMap, List<IType> value, boolean isMethod, List<IType> exceptions) {
        this.genericTypeMap = genericTypeMap;
        this.returns = value.remove(value.size() - 1);
        this.values = value;
        this.type = (isMethod ? METHOD : (value.isEmpty() ? FIELD_OR_CLASS : CLASS));
        this.throwsException = exceptions;
    }

    @Override
    public String toGeneric() {
        CharList sb = new CharList();
        if (!genericTypeMap.isEmpty()) {
            sb.append('<');
            for (Map.Entry<String, Collection<Generic>> entry : genericTypeMap.entrySet()) {
                sb.append(entry.getKey()).append(':');
                Collection<Generic> list = entry.getValue();

                boolean flag = false;
                for (Generic value : list) {
                    if (value.type == Generic.CLASS) {
                        if (flag) {
                            throw new IllegalArgumentException("At most one class extend!");
                        }
                        flag = true;
                    }
                    value.appendGeneric(sb);
                }
            }
            sb.append('>');
        }
        if (type != FIELD_OR_CLASS) {
            if (type == METHOD)
                sb.append('(');
            for (IType value : values) {
                value.appendGeneric(sb);
            }
            if (type == METHOD)
                sb.append(')');
        }
        returns.appendGeneric(sb);
        if (throwsException != null) {
            for (IType value : throwsException) {
                sb.append('^');
                value.appendGeneric(sb);
            }
        }
        return sb.toString();
    }

    @Override
    public void appendGeneric(CharList sb) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void appendString(CharList sb) {
        throw new UnsupportedOperationException();
    }

    public String getSignatureType() {
        if (genericTypeMap.isEmpty())
            return "";
        CharList sb = new CharList(40).append('<');
        for (Map.Entry<String, Collection<Generic>> entry : genericTypeMap.entrySet()) {
            sb.append(entry.getKey());
            Collection<Generic> list = entry.getValue();
            if (list.size() > 1/* || !list.get(0).clazz.className.equals("java/lang/Object")*/) {
                sb.append(" extends ");
                for (Generic value : list) {
                    value.appendString(sb);
                    sb.append(" & ");
                }
                sb.setIndex(sb.length() - 3);
            }
            sb.append(", ");
        }
        sb.setIndex(sb.length() - 2);
        return sb.append('>').toString();
    }

    public String toString() {
        if (type == FIELD_OR_CLASS) {
            return returns.toString();
        } else {
            CharList sb = new CharList();
            if (type == METHOD) {
                returns.appendString(sb);
                sb.append(' ').append('(');
                for (IType value : values) {
                    value.appendString(sb);
                    sb.append(", ");
                }
                sb.setIndex(sb.length() - 2);
                sb.append(')');
            } else {
                for (IType value : values) {
                    value.appendString(sb);
                }
                returns.appendString(sb);
            }
            return sb.toString();
        }
    }

    public void rename(UnaryOperator<String> fn) {
        for (Collection<Generic> values : genericTypeMap.values()) {
            for (Generic value : values) {
                value.rename(fn);
            }
        }
        if (values != null) {
            for (IType value : values) {
                rename0(fn, value);
            }
        }
        rename0(fn, returns);
    }

    static void rename0(UnaryOperator<String> fn, IType value) {
        if (value.getClass() == Type.class) {
            Type type = (Type) value;
            if (type.owner != null)
                type.owner = fn.apply(type.owner);
        } else {
            ((Generic) value).rename(fn);
        }
    }

    @Override
    public boolean isGeneric() {
        return true;
    }
}