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
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;

import java.util.*;
import java.util.function.UnaryOperator;

/**
 * 泛型签名
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Signature implements IType {
    public Map<String, List<Generic>> genericTypeMap;

    public static final byte METHOD = 1,
            FIELD_OR_CLASS = 0,
            CLASS = -1;

    /**
     * Contract: values[0] is class, other is interface
     */
    public List<IGeneric> values;
    public IGeneric returns;
    public final byte type;
    public List<IGeneric> Throws;

    public Signature(int type) {
        this.genericTypeMap = new MyHashMap<>();
        this.values = new ArrayList<>();
        this.type = (byte) type;
    }

    public Signature(Map<String, List<Generic>> genericTypeMap, List<IGeneric> value, boolean isMethod, List<IGeneric> exceptions) {
        this.genericTypeMap = genericTypeMap;
        this.returns = value.remove(value.size() - 1);
        this.values = value;
        this.type = (isMethod ? METHOD : (value.isEmpty() ? FIELD_OR_CLASS : CLASS));
        this.Throws = exceptions;
    }

    @Override
    public String toGeneric() {
        CharList sb = IOUtil.getSharedCharBuf();

        if (!genericTypeMap.isEmpty()) {
            sb.append('<');
            for (Map.Entry<String, List<Generic>> entry : genericTypeMap.entrySet()) {
                sb.append(entry.getKey()).append(':');
                List<Generic> list = entry.getValue();

                list.get(0).appendGeneric(sb);

                for (int i = 1; i < list.size(); i++) {
                    Generic value = list.get(i);
                    if (value.type == Generic.TYPE_CLASS) {
                        throw new IllegalArgumentException("Interface expected here");
                    }
                    value.appendGeneric(sb);
                }
            }
            sb.append('>');
        }
        if (type != FIELD_OR_CLASS) {
            if (type == METHOD)
                sb.append('(');
            for (int i = 0; i < values.size(); i++) {
                values.get(i).appendGeneric(sb);
            }
            if (type == METHOD)
                sb.append(')');
        }
        returns.appendGeneric(sb);
        if (Throws != null) {
            for (int i = 0; i < Throws.size(); i++) {
                sb.append('^');
                Throws.get(i).appendGeneric(sb);
            }
        }
        return sb.toString();
    }

    public String getSignatureType() {
        if (genericTypeMap.isEmpty()) return "";

        CharList sb = IOUtil.getSharedCharBuf();

        sb.append('<');
        Iterator<Map.Entry<String, List<Generic>>> itr = genericTypeMap.entrySet().iterator();
        while (true) {
            Map.Entry<String, List<Generic>> entry = itr.next();
            appendTypeParameter(sb, entry.getKey(), entry.getValue());

            if (!itr.hasNext()) break;
            sb.append(", ");
        }

        return sb.append('>').toString();
    }

    public void appendTypeParameter(CharList sb, String name, List<Generic> list) {
        sb.append(name);
        if (list == null)
            list = genericTypeMap.getOrDefault(name, Collections.emptyList());
        if (list.isEmpty()) return;
        if (list.size() > 1 || !list.get(0).owner.equals("java/lang/Object")) {
            sb.append(" extends ");
            int i = 0;
            while (true) {
                Generic value = list.get(i++);
                value.appendString(sb, this);
                if (i == list.size()) break;
                sb.append(" & ");
            }
        }
    }

    public String toString() {
        CharList sb = IOUtil.getSharedCharBuf();
        if (type == FIELD_OR_CLASS) {
            returns.appendString(sb, this);
        } else {
            if (type == METHOD) {
                returns.appendString(sb, this);
                sb.append(' ').append('(');
                for (int i = 0; i < values.size(); i++) {
                    values.get(i).appendString(sb, this);
                    sb.append(", ");
                }
                sb.setIndex(sb.length() - 2);
                sb.append(')');
            } else {
                for (int i = 0; i < values.size(); i++) {
                    values.get(i).appendString(sb, this);
                }
                returns.appendString(sb, this);
            }
        }
        return sb.toString();
    }

    public void rename(UnaryOperator<String> fn) {
        for (Collection<Generic> values : genericTypeMap.values()) {
            for (Generic value : values) {
                value.rename(fn);
            }
        }
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                rename0(fn, values.get(i));
            }
        }
        rename0(fn, returns);
    }

    static void rename0(UnaryOperator<String> fn, IGeneric value) {
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

    public static void main(String[] args) {
        Signature signature = parse(args[0]);
        System.out.println("toString(): " + signature.toString());
        System.out.println("getSignatureType(): " + signature.getSignatureType());
        System.out.println("toGeneric(): " + signature.toGeneric());
    }

    private static final int F_TEST_ITF = 1, F_PRIMITIVE = 2, F_IS_SUB_CLASS = 4;

    /**
     * Signatures encode declarations written in the Java programming language that use types outside the type system of the Java Virtual Machine. They support reflection and debugging, as well as compilation when only class files are available.
     * <p>
     * A Java compiler must emit a signature for any class, interface, constructor, method, or field whose declaration uses type variables or parameterized types. Specifically, a Java compiler must emit:
     * <p>
     * A class signature for any class or interface declaration which is either generic, or has a parameterized type as a superclass or superinterface, or both.
     * <p>
     * A method signature for any method or constructor declaration which is either generic, or has a type variable or parameterized type as the return type or a formal parameter type, or has a type variable in a throws clause, or any combination thereof.
     * <p>
     * If the throws clause of a method or constructor declaration does not involve type variables, then a compiler may treat the declaration as having no throws clause for the purpose of emitting a method signature.
     * <p>
     * A field signature for any field, formal parameter, or local variable declaration whose type uses a type variable or a parameterized type.
     * <p>
     * Signatures are specified using a grammar which follows the notation of §4.3.1. In addition to that notation:
     * <p>
     * The syntax [x] on the right-hand side of a production denotes zero or one occurrences of x. That is, x is an optional symbol. The alternative which contains the optional symbol actually defines two alternatives: one that omits the optional symbol and one that includes it.
     * <p>
     * A very long right-hand side may be continued on a second line by clearly indenting the second line.
     * <p>
     * The grammar includes the terminal symbol Identifier to denote the name of a type, field, method, formal parameter, local variable, or type variable, as generated by a Java compiler. Such a name must not contain any of the ASCII characters . ; [ / < > : (that is, the characters forbidden in method names (§4.2.2) and also colon) but may contain characters that must not appear in an identifier in the Java programming language (JLS §3.8).
     * <p>
     * Signatures rely on a hierarchy of nonterminals known as type signatures:
     * <p>
     * A Java type signature represents either a reference type or a primitive type of the Java programming language.
     * <p>
     * 下面都啥鬼东西，不如实践
     */

    public static Signature parse(String generic) {
        MutableInt mi = new MutableInt();
        CharList tmp = IOUtil.getSharedCharBuf();

        int i = 0;

        Map<String, List<Generic>> map = new LinkedMyHashMap<>();

        if (generic.charAt(0) == '<') {
            i = 1;

            outer:
            while (i < generic.length()) {
                char cr = generic.charAt(i++);
                switch (cr) {
                    case ':': {
                        String key = tmp.toString();
                        tmp.clear();
                        List<Generic> list = new ArrayList<>(4);

                        do {
                            mi.setValue(i);
                            tmp.clear();
                            list.add((Generic) getSignatureValue(generic, mi, F_TEST_ITF, tmp));
                            i = mi.getValue();
                        } while (hasNext(generic, i));
                        tmp.clear();

                        map.put(key, list);
                        continue;
                    }
                    case '>':
                        break outer;
                    default:
                        tmp.append(cr);
                }
            }
        }

        boolean isMethod = generic.charAt(i) == '(';
        if (isMethod) {
            i++;
        }

        List<IGeneric> exceptions = new ArrayList<>();

        boolean returnVal = false;
        List<IGeneric> params = new ArrayList<>();
        while (i < generic.length()) {
            switch (generic.charAt(i)) {
                case '^':
                    if (returnVal) {
                        mi.setValue(i + 1);
                        tmp.clear();
                        exceptions.add(getSignatureValue(generic, mi, 0, tmp));
                        i = mi.getValue();

                        continue;
                    } else {
                        throw new IllegalArgumentException("[" + (i) + "(" + generic.charAt(i) + ")]" + generic);
                    }
                case ')':
                    if (!returnVal) {
                        i++;
                        returnVal = true;
                        break;
                    } else {
                        throw new IllegalArgumentException("[" + (i) + "(" + generic.charAt(i) + ")]" + generic);
                    }
            }

            mi.setValue(i);
            tmp.clear();
            params.add(getSignatureValue(generic, mi, F_PRIMITIVE, tmp));
            i = mi.getValue();
        }

        return new Signature(map, params, isMethod, exceptions);
    }

    private static boolean hasNext(String generic, int i) {
        switch (generic.charAt(i++)) {
            case ':':
                return true;
            case 'L':
            case 'T':
                return generic.charAt(i) != ':';
            case '>':
            default:
                return false;
        }
    }

    @SuppressWarnings("fallthrough")
    private static IGeneric getSignatureValue(String s, MutableInt mi, int F, CharList tmp) {
        int i = mi.getValue();

        int arrayLevel = 0;
        byte subClass = 0;

        byte cat;
        if ((F & F_IS_SUB_CLASS) == 0) {
            switch (s.charAt(i)) {
                case '+':
                    subClass = Generic.EX_EXTENDS;
                    i++;
                    break;
                case '-':
                    subClass = Generic.EX_SUPERS;
                    i++;
                    break;
                case '[':
                    while (s.charAt(i) == '[') {
                        arrayLevel++;
                        i++;
                    }
            }

            final char c = s.charAt(i);
            if ((F & F_PRIMITIVE) != 0) {
                if (Type.isValid(c) && c != Type.CLASS) {
                    mi.setValue(i + 1);
                    return arrayLevel == 0 ? Type.std(c) : new Type(c, arrayLevel);
                }
            }

            out:
            switch (c) {
                case '*': {
                    mi.setValue(i + 1);
                    return new Generic(Generic.TYPE_CLASS, "*", arrayLevel, subClass);
                }
                case 'T':
                    cat = Generic.TYPE_TYPE_PARAM;
                    break;
                case 'L':
                    cat = Generic.TYPE_CLASS;
                    break;
                case ':':
                    if ((F & F_TEST_ITF) != 0) {
                        switch (s.charAt(++i)) {
                            case 'L':
                                cat = Generic.TYPE_INHERIT_CLASS;
                                break out;
                            case 'T': // <S:Ljava/lang/Object;T::TS;>     <S, T extends S>
                                cat = Generic.TYPE_INHERIT_TYPE_PARAM;
                                break out;
                        }
                    }
                default:
                    throw new IllegalArgumentException("[" + (i) + "(" + s.charAt(i) + ")]" + s);
            }
            i++;
        } else {
            cat = Generic.TYPE_SUB_CLASS;
        }

        boolean shouldFindNext = false;

        while (i < s.length()) {
            char c1 = s.charAt(i);
            if (c1 == ';' || c1 == '<') {
                if (c1 == '<')
                    shouldFindNext = true;
                break;
            }
            i++;
            tmp.append(c1);
        }

        if (tmp.length() == 0) {
            throw new IllegalArgumentException("[" + (i) + "(" + s.charAt(i) + ")]" + s);
        }

        Generic value = new Generic(cat, tmp.toString(), arrayLevel, subClass);
        // probably: method generic should check class generic...
        //if(cat == Generic.TYPE_TYPE_PARAM && !tnKeySet.contains(value.owner))
        //    throw new IllegalArgumentException("没找到Type Variable " + value.owner);

        if (shouldFindNext) {
            i++;
            while (i < s.length() && s.charAt(i) != '>') {
                mi.setValue(i);
                tmp.clear();
                value.addChild(getSignatureValue(s, mi, F & ~F_IS_SUB_CLASS, tmp));
                i = mi.getValue();
            }
        }
        if (s.charAt(i) != ';') {
            if (s.charAt(i) != '>') {
                throw new IllegalArgumentException("[" + (i) + "(" + s.charAt(i) + ")]" + s);
            } else if (s.charAt(++i) != ';') {
                if (s.charAt(i) != '.')
                    throw new IllegalArgumentException("[" + (i) + "(" + s.charAt(i) + ")]" + s);
                else {
                    mi.setValue(i + 1);
                    tmp.clear();
                    value.subClass = (Generic) getSignatureValue(s, mi, (F & 1) | F_IS_SUB_CLASS, tmp);
                    i = mi.getValue() - 1;
                }
            }
        }

        mi.setValue(i + 1);
        return value;
    }
}