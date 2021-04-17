/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: GenericSign.java
 */
package roj.asm.util.type;

import roj.asm.util.IType;
import roj.collect.MyHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

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
        StringBuilder sb = new StringBuilder();
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
    public void appendGeneric(StringBuilder sb) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void appendString(StringBuilder sb) {
        throw new UnsupportedOperationException();
    }

    public String getSignatureType() {
        if (genericTypeMap.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder().append('<');
        for (Map.Entry<String, Collection<Generic>> entry : genericTypeMap.entrySet()) {
            sb.append(entry.getKey());
            Collection<Generic> list = entry.getValue();
            if (list.size() > 1/* || !list.get(0).clazz.className.equals("java/lang/Object")*/) {
                sb.append(" extends ");
                for (Generic value : list) {
                    value.appendString(sb);
                    sb.append(" & ");
                }
                sb.deleteCharAt(sb.length() - 1).deleteCharAt(sb.length() - 1).deleteCharAt(sb.length() - 1);
            }
            sb.append(", ");
        }
        return sb.deleteCharAt(sb.length() - 1).deleteCharAt(sb.length() - 1).append('>').toString();
    }

    public String toString() {
        if (type == FIELD_OR_CLASS) {
            return returns.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            if (type == METHOD) {
                returns.appendString(sb);
                sb.append(' ').append('(');
                for (IType value : values) {
                    value.appendString(sb);
                    sb.append(", ");
                }
                sb.delete(sb.length() - 2, sb.length()).append(')');
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