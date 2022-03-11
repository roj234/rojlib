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
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class ParamHelper {
    /**
     * 转换方法asm type字符串为对象
     */
    public static List<Type> parseMethod(String desc) {
        List<Type> params = new SimpleList<>();
        parseMethod(desc, params);
        return params;
    }

    @SuppressWarnings("fallthrough")
    public static void parseMethod(String desc, List<Type> params) {
        int index = desc.indexOf(")");

        CharList tmp = IOUtil.getSharedCharBuf();

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
                        if (!Type.isValid(c)) throw new IllegalArgumentException(desc);
                        params.add(arr == 0 ? Type.std(c) : new Type(c, arr));
                        arr = 0;
                    }
                    break;
            }
        }

        Type returns = parseOne(desc, index + 1);
        params.add(returns);
    }

    /**
     * 方法参数所占空间
     * @see roj.asm.tree.insn.InvokeItfInsnNode#toByteArray
     */
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
                        if (c == Type.DOUBLE || c == Type.LONG) {
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

    /**
     * 方法返回类型
     */
    public static Type parseReturn(String str) {
        int index = str.indexOf(")");
        if (index < 0) throw new IllegalArgumentException("不是方法描述");
        return parseOne(str, index + 1);
    }

    /**
     * 转换字段asm type字符串为对象
     */
    public static Type parseField(String s) {
        return parseOne(s, 0);
    }

    private static Type parseOne(String s, int off) {
        char c0 = s.charAt(off);
        if (!Type.isValid(c0)) throw new IllegalArgumentException(s);
        switch (c0) {
            case Type.ARRAY:
                int pos = s.lastIndexOf('[') + 1;
                Type t = parseOne(s, pos);
                if(t.owner == null) t = new Type(t.type, pos - off);
                else t.array = (char) (pos - off);
                return t;
            case Type.CLASS:
                if (!s.endsWith(";")) {
                    throw new IllegalArgumentException("'类'类型 '" + s + "' 未以';'结束");
                }
                return new Type(s.substring(off + 1, s.length() - 1));
            default:
                return Type.std(c0);
        }
    }

    /**
     * 转换字段asm type对象为字符串
     */
    public static String getField(Type type) {
        if(type.owner == null && type.array == 0)
            return Type.toDesc(type.type);

        CharList sb = IOUtil.getSharedCharBuf();
        getOne(type, sb);
        return sb.toString();
    }

    /**
     * 转换方法asm type对象为字符串
     */
    public static String getMethod(List<Type> list) {
        CharList sb = IOUtil.getSharedCharBuf().append('(');

        for (int i = 0; i < list.size(); i++) {
            if (i == list.size() - 1) { // last
                sb.append(')');
            }

            getOne(list.get(i), sb);
        }
        return sb.toString();
    }

    public static void getOne(Type type, CharList sb) {
        for (int q = type.array; q > 0; q--) {
            sb.append('[');
        }
        if (type.type == Type.CLASS) {
            sb.append('L').append(type.owner).append(';');
        } else {
            sb.append((char) type.type);
        }
    }

    /**
     * 转换class为字段的asm type
     */
    public static String class2asm(Class<?> clazz) {
        return class2asm(IOUtil.getSharedCharBuf(), clazz).toString();
    }
    public static CharList class2asm(CharList sb, Class<?> clazz) {
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

    /**
     * 转换class为方法的asm type
     */
    public static String class2asm(Class<?>[] classes, Class<?> returns) {
        CharList sb = IOUtil.getSharedCharBuf().append('(');

        for (Class<?> clazz : classes) {
            class2asm(sb, clazz);
        }
        sb.append(')');
        return class2asm(sb, returns).toString();
    }

    /**
     * 转换(非基本)字段asm type为class
     */
    public static String asm2class(String owner, int array) {
        if(array > 0) {
            CharList cl = IOUtil.getSharedCharBuf();
            for (int i = 0; i < array; i++) {
                cl.append('[');
            }
            cl.append('L');

            return cl.append(owner).replace('/', '.').append(';').toString();
        } else {
            return owner.replace('/', '.');
        }
    }

    /**
     * 转换方法asm type为人类可读(实际上是mojang用的map)类型
     * @param types [String, double, void]
     * @param methodName <init>
     * @param trimPackage 删除包名
     * @return void <init>(java.lang.String, double)
     */
    public static String humanize(List<Type> types, String methodName, boolean trimPackage) {
        Type ret = types.remove(types.size() - 1);

        CharList sb = IOUtil.getSharedCharBuf()
                            .append(ret).append(' ').append(methodName).append("(");

        if (!types.isEmpty()) {
            int i = 0;
            do {
                Type t = types.get(i++);
                if (trimPackage && t.owner != null) {
                    String o = t.owner;
                    sb.append(o, o.lastIndexOf('/') + 1, o.length());
                    for (int j = 0; j < t.array; j++) sb.append("[]");
                } else {
                    t.appendString(sb, null);
                }
                if (i == types.size()) break;
                sb.append(", ");
            } while (true);
        }

        types.add(ret);

        return sb.append(')').toString();
    }

    /**
     * 转换人类可读(实际上是mojang用的map)类型为方法asm type
     * @param in  java.lang.String, double
     * @param out void
     * @return (Ljava/lang/String;D)V
     */
    public static String dehumanize(CharSequence in, CharSequence out) {
        CharList sb = IOUtil.getSharedCharBuf()
                            .append('(');

        List<String> list = TextUtil.split(new ArrayList<>(), in, ',');
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            dehumanize0(s, sb);
        }
        return dehumanize0(out, sb.append(')')).toString();
    }

    private static CharList dehumanize0(CharSequence z, CharList sb) {
        if (z.length() == 0)
            return sb;
        CharList sb1 = new CharList().append(z);

        // Array filter
        while (sb1.length() > 2 && sb1.charAt(sb1.length() - 1) == ']' && sb1.charAt(sb1.length() - 2) == '[') {
            sb1.setLength(sb1.length() - 2);
            sb.append('[');
        }

        switch (sb1.toString()) {
            case "int":
                return sb.append(INT);
            case "short":
                return sb.append(Type.SHORT);
            case "double":
                return sb.append(Type.DOUBLE);
            case "long":
                return sb.append(Type.LONG);
            case "float":
                return sb.append(Type.FLOAT);
            case "char":
                return sb.append(Type.CHAR);
            case "byte":
                return sb.append(Type.BYTE);
            case "boolean":
                return sb.append(Type.BOOLEAN);
            case "void":
                return sb.append(Type.VOID);
        }
        for (int i = 0; i < sb1.length(); i++) {
            if (sb1.charAt(i) == '.')
                sb1.set(i, '/');
        }
        sb.append('L').append(sb1);
        return sb.append(';');
    }
}