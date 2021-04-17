/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ParamHelper.java
 */
package roj.asm.util.type;

import roj.collect.SimpleList;
import roj.text.CharList;

import java.util.List;

import static roj.asm.util.type.NativeType.*;

public final class ParamHelper {
    /**
     * Method descriptor
     */
    public static List<Type> parseMethod(String str) {
        String s1, s2;
        List<Type> ioList = new SimpleList<>();

        int index = str.indexOf(")");
        s1 = str.substring(index + 1); // Output
        s2 = str.substring(1, index);  // Input

        Type tmp3 = parseOne(IO.O, s1.charAt(0), s1);

        CharList tmp4 = new CharList(64);
        boolean flag = false;
        //boolean flag2 = false;
        int arrLv = 0;
        for (int i = 0; i < s2.length(); i++) {
            char cr = s2.charAt(i);
            if (cr == 'L' && !flag) {
                flag = true;
            } else if (cr == ';') {
                flag = false;
                s1 = tmp4.toString();
                ioList.add(new Type(IO.I, s1, arrLv));
                tmp4.clear();
                arrLv = 0;
            } else if (cr == '[') {
                arrLv++;
            } else if (flag) {
                tmp4.append(cr);
            } else {
                char type = NativeType.validate(cr);
                ioList.add(new Type(IO.I, type, arrLv));
                arrLv = 0;
            }
        }
        ioList.add(tmp3);
        return ioList;
    }

    public static String getMethod(List<Type> list) {
        StringBuilder sb = new StringBuilder("(");
        for (Type type : list) {
            if (type.io == IO.O) {
                sb.append(')');
            }
            getOne(type, sb);
        }
        return sb.toString();
    }

    public static Type getReturn(String str) {
        int index = str.indexOf(")");
        String s1 = str.substring(index + 1); // Output

        return parseOne(IO.O, s1.charAt(0), s1);
    }

    /**
     * Field descriptor
     */
    public static Type parseField(String s) {
        return parseOne(IO.O, s.charAt(0), s);
    }

    private static Type parseOne(boolean io, char c0, String s) {
        char type = NativeType.validate(c0);

        switch (type) {
            case ARRAY:
                int pos = s.lastIndexOf('[') + 1;
                String tmp = s.substring(pos);
                Type param = parseOne(io, tmp.charAt(0), tmp);
                param.array = pos;
                return param;
            case CLASS:
                if (!s.endsWith(";")) {
                    System.err.println("Class name " + s + " does not endsWith ;");
                    return new Type(io, s.substring(1), 0);
                }
                return new Type(io, s.substring(1, s.length() - 1), 0);
            default:
                return new Type(io, type, 0);
        }
    }

    public static String getField(Type type) {
        StringBuilder sb;
        getOne(type, sb = new StringBuilder());
        return sb.toString();
    }

    public static void getOne(Type type, StringBuilder sb) {
        int i = 0;
        for (int q = type.array; i < q; i++) {
            sb.append('[');
        }
        if (type.type == NativeType.CLASS) {
            sb.append('L').append(type.owner).append(';');
        } else {
            sb.append(type.type);
        }
    }

    /**
     * ConstantUTF8 / mostly
     */
    public static String classDescriptor(Class<?> clazz) {
        return classDescriptor(new StringBuilder(), clazz).toString();
    }

    public static StringBuilder classDescriptor(StringBuilder sb, Class<?> clazz) {
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
        StringBuilder sb = new StringBuilder("(");
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
        char type = NativeType.validate(s.charAt(0));
        if (s.length() == 1)
            return new Type(IO.I, type, 0);
        if (s.startsWith("[")) {
            int pos = s.lastIndexOf('[') + 1;
            String tmp = s.substring(pos);
            Type param = classType(tmp);
            param.array = pos;
            return param;
        } else {
            return new Type(IO.I, s.substring(1, s.length() - 1), 0);
        }
    }

    /**
     * XLOAD / XRETURN 的前缀
     */
    public static String nativeType(Class<?> clazz) {
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
     * @return void <init>(String, double)
     */
    public static String humanize(List<Type> types, String methodName) {
        Type ret = types.remove(types.size() - 1);

        StringBuilder sb = new StringBuilder().append(ret).append(' ').append(methodName).append("(");

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
     * @param in  void
     * @param out java.lang.String, double
     * @return ()V
     */
    public static String dehumanize(String in, String out) {
        StringBuilder sb = (new StringBuilder(in.length() + out.length() + 4)).append('(');
        String[] x = in.split(",");
        for (String s : x) {
            dehumanize(s, sb);
        }
        return dehumanize(out, sb.append(')')).toString();
    }

    private static StringBuilder dehumanize(String z, StringBuilder sb) {
        if (z.isEmpty())
            return sb;
        StringBuilder sb1 = new StringBuilder(z);

        while (sb1.charAt(sb1.length() - 1) == ']' && sb1.charAt(sb1.length() - 2) == '[') {
            sb1.deleteCharAt(sb1.length() - 1).deleteCharAt(sb1.length() - 1);
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
                sb1.setCharAt(i, '/');
        }
        return sb.append('L').append(sb1).append(';');
    }
}