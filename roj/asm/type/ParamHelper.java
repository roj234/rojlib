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

import roj.collect.SimpleList;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.type.NativeType.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class ParamHelper {
    public static final ThreadLocal<CharList> sharedBuffer = ThreadLocal.withInitial(CharList::new);

    /**
     * Method descriptor
     */
    public static List<Type> parseMethod(String desc) {
        List<Type> params = new SimpleList<>();
        parseMethod(desc, params);
        return params;
    }

    @SuppressWarnings("fallthrough")
    public static void parseMethod(String desc, List<Type> params) {
        int index = desc.indexOf(")");
        String ret = desc.substring(index + 1); // Output
        Type returns = parseOne(ret.charAt(0), ret);

        CharList tmp = sharedBuffer.get();
        tmp.clear();

        boolean ref = false;
        int arr = 0;
        for (int i = 1; i < index; i++) {
            char c = desc.charAt(i);
            switch (c) {
                case ';':
                    if(!ref) {
                        throw new IllegalArgumentException(desc);
                    } else {
                        params.add(new Type(tmp.toString(), arr));
                        tmp.clear();
                        arr = 0;
                        ref = false;
                    }
                    break;
                case '[':
                    arr++;
                    break;
                case 'L':
                    if(!ref) {
                        ref = true;
                        break;
                    }
                default:
                    if(ref) {
                        tmp.append(c);
                    } else {
                        NativeType.validate(c);
                        params.add(arr == 0 ? Type.std(c) : new Type(c, arr));
                        arr = 0;
                    }
                    break;
            }
        }

        params.add(returns);
    }

    public static int paramSize(String desc) {
        int cnt = 0;
        int end = desc.indexOf(")");

        int clz = 0;
        for (int i = 1; i < end; i++) {
            char c = desc.charAt(i);
            switch (c) {
                case ';':
                    if((clz & 1) == 0) {
                        throw new IllegalArgumentException(desc);
                    } else {
                        cnt++;
                        clz = 0;
                    }
                    break;
                case '[':
                    break;
                case 'L':
                    clz = 1;
                    break;
                default:
                    if((clz & 1) == 0) {
                        if (c == NativeType.DOUBLE || c == NativeType.LONG) {
                            cnt += 2;
                        } else {
                            cnt ++;
                            clz = 0;
                        }
                    }
                    break;
            }
        }
        return cnt;
    }

    public static String getMethod(List<Type> list) {
        CharList sb = sharedBuffer.get();
        sb.clear();
        sb.append("(");
        for (int i = 0; i < list.size(); i++) {
            if (i == list.size() - 1) { // last
                sb.append(')');
            }

            getOne(list.get(i), sb);
        }
        return sb.toString();
    }

    public static Type getReturn(String str) {
        int index = str.indexOf(")");
        String s1 = str.substring(index + 1); // Output

        return parseOne(s1.charAt(0), s1);
    }

    /**
     * Field descriptor
     */
    public static Type parseField(String s) {
        return parseOne(s.charAt(0), s);
    }

    private static Type parseOne(char c0, String s) {
        switch (NativeType.validate(c0)) {
            case ARRAY:
                int pos = s.lastIndexOf('[') + 1;
                String p1 = s.substring(pos);
                Type param = parseOne(p1.charAt(0), p1);
                if(param.owner == null)
                    param = new Type((char) param.type, pos);
                else
                    param.array = pos;
                return param;
            case CLASS:
                if (!s.endsWith(";")) {
                    throw new IllegalArgumentException("Class '" + s + "' not endsWith ';'");
                }
                return new Type(s.substring(1, s.length() - 1), 0);
            default:
                return Type.std(c0);
        }
    }

    public static String getField(Type type) {
        if(type.owner == null && type.array == 0)
            return NativeType.toDesc(type.type);

        CharList sb = sharedBuffer.get();
        sb.clear();
        sb.ensureCapacity(2 + type.array + (type.owner == null ? 0 : type.owner.length() + 2));
        getOne(type, sb);
        return sb.toString();
    }

    public static void getOne(Type type, CharList sb) {
        for (int q = type.array; q > 0; q--) {
            sb.append('[');
        }
        if (type.type == NativeType.CLASS) {
            sb.append('L').append(type.owner).append(';');
        } else {
            sb.append((char) type.type);
        }
    }

    /**
     * ConstantUTF8 / mostly
     */
    public static String classDescriptor(Class<?> clazz) {
        CharList sb = sharedBuffer.get();
        sb.clear();

        return classDescriptor(sb, clazz).toString();
    }

    public static CharList classDescriptor(CharList sb, Class<?> clazz) {
        Class<?> tmp;
        while ((tmp = clazz.getComponentType()) != null) {
            clazz = tmp;
            sb.append('[');
        }
        if (clazz.isPrimitive()) {
            switch (clazz.getName()) {
                case "int":
                    return sb.append(INT);
                case "short":
                    return sb.append(SHORT);
                case "double":
                    return sb.append(DOUBLE);
                case "long":
                    return sb.append(LONG);
                case "float":
                    return sb.append(FLOAT);
                case "char":
                    return sb.append(CHAR);
                case "byte":
                    return sb.append(BYTE);
                case "boolean":
                    return sb.append(BOOLEAN);
                case "void":
                    return sb.append(VOID);
            }
        } else if (clazz.isArray()) {
            throw new IllegalArgumentException();
        }
        return sb.append('L').append(clazz.getName().replace('.', '/')).append(';');
    }

    public static String classDescriptors(Class<?>[] classes, Class<?> returns) {
        CharList sb = sharedBuffer.get();
        sb.clear();
        sb.append('(');

        for (Class<?> clazz : classes) {
            classDescriptor(sb, clazz);
        }
        sb.append(')');
        return classDescriptor(sb, returns).toString();
    }

    /**
     * ConstantClass
     */
    public static Type classType(String s) {
        byte c = NativeType.validate(s.charAt(0));
        if (s.length() == 1)
            return Type.std((char) c);
        if (s.startsWith("[")) {
            int pos = s.lastIndexOf('[') + 1;
            String p1 = s.substring(pos);
            Type param = classType(p1);
            if(param.owner == null)
                param = new Type((char) param.type, pos);
            else
                param.array = pos;
            return param;
        } else {
            return new Type(s.substring(1, s.length() - 1), 0);
        }
    }

    /**
     * XLOAD / XRETURN 的前缀
     */
    public static String XPrefix(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            switch (clazz.getName()) {
                case "int":
                case "char":
                case "byte":
                case "boolean":
                case "short":
                    return "I";
                case "double":
                    return "D";
                case "long":
                    return "L";
                case "float":
                    return "F";
                case "void":
                    return "";
            }
        }
        return "A";
    }

    /**
     * @param types (Ljava/lang/String;D)V
     * @param methodName <init>
     * @return void <init>(java.lang.String, double)
     */
    public static String humanize(List<Type> types, String methodName) {
        Type ret = types.remove(types.size() - 1);

        CharList sb = sharedBuffer.get();
        sb.clear();
        sb.append(ret).append(' ').append(methodName).append("(");

        if (!types.isEmpty()) {
            for (Type p : types) {
                sb.append(p).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }

        types.add(ret);

        return sb.append(')').toString();
    }

    /**
     * @param in  java.lang.String, double
     * @param out void
     * @return (Ljava/lang/String;D)V
     */
    public static String dehumanize(CharSequence in, CharSequence out) {
        CharList sb = sharedBuffer.get();
        sb.clear();
        sb.ensureCapacity(in.length() + out.length() + 4);
        sb.append('(');

        List<String> list = TextUtil.split(new ArrayList<>(), in, ',');
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            dehumanize0(s, sb);
        }
        return dehumanize0(out, sb.append(')')).toString();
    }

    public static CharList dehumanize0(CharSequence z, CharList sb) {
        if (z.length() == 0)
            return sb;
        CharList sb1 = new CharList().append(z);

        // Array filter
        while (sb1.length() > 2 && sb1.charAt(sb1.length() - 1) == ']' && sb1.charAt(sb1.length() - 2) == '[') {
            sb1.setIndex(sb1.length() - 2);
            sb.append('[');
        }

        switch (sb1.toString()) {
            case "int":
                return sb.append(NativeType.INT);
            case "short":
                return sb.append(NativeType.SHORT);
            case "double":
                return sb.append(NativeType.DOUBLE);
            case "long":
                return sb.append(NativeType.LONG);
            case "float":
                return sb.append(NativeType.FLOAT);
            case "char":
                return sb.append(NativeType.CHAR);
            case "byte":
                return sb.append(NativeType.BYTE);
            case "boolean":
                return sb.append(NativeType.BOOLEAN);
            case "void":
                return sb.append(NativeType.VOID);
        }
        for (int i = 0; i < sb1.length(); i++) {
            if (sb1.charAt(i) == '.')
                sb1.set(i, '/');
        }
        sb.append('L').append(sb1);
        return sb.append(';');
    }

    public static String normalize(String owner, int array) {
        if(array > 0) {
            CharList cl = new CharList(owner.length() + array + 3);
            for (int i = 0; i < array; i++) {
                cl.append('[');
            }
            cl.append('L');

            return cl.append(owner).replace('/', '.').append(';').toString();
        } else {
            return owner.replace('/', '.');
        }
    }
}