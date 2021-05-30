package roj.asm.util.type;

import roj.asm.util.IType;
import roj.collect.CharMap;
import roj.concurrent.OperationDone;
import roj.text.CharList;

import static roj.asm.util.type.NativeType.*;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: Type.java
 */
public class Type implements IType {
    private static final CharMap<Type> STD = new CharMap<>(9);

    public static synchronized Type std(char c) {
        Type type = STD.get(c);
        if(type == null) {
            STD.put(c, type = new Type(c));
        }
        if(type.array != 0) {
            throw new IllegalArgumentException("Std type " + c + " have been changed.");
        }
        return type;
    }

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
        sb.append(toString());
    }

    public int length() {
        return (array == 0 && (type == NativeType.LONG || type == NativeType.DOUBLE)) ? 2 : 1;
    }

    public String nativeName() {
        switch (type) {
            case CLASS:
            case ARRAY:
                return "A";
            case NativeType.VOID:
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
        StringBuilder sb = new StringBuilder();
        if (this.owner != null) {
            sb.append(owner/*.substring(owner.lastIndexOf('/') + 1)*/);
        } else {
            sb.append(NativeType.toString(type));
        }

        for (int i = 0; i < this.array; i++)
            sb.append("[]");
        return sb.toString();
    }
}
