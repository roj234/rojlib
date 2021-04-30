package roj.asm.util.type;

import roj.asm.util.IType;
import roj.concurrent.OperationDone;

import static roj.asm.util.type.NativeType.*;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: Type.java
 */
public class Type implements IType {
    public static final Type VOID = new Type(IO.O, NativeType.VOID, 0);

    public final boolean io;
    public final char type;
    public String owner;
    public int array;

    public Type(char type) {
        this(false, type, 0);
    }

    /**
     * TYPE_OTHER
     */
    public Type(boolean io, char type, int array) {
        this.io = io;
        this.type = NativeType.validate(type);
        this.array = array;
    }

    public Type(String type) {
        this(false, type, 0);
    }

    /**
     * TYPE_CLASS
     */
    public Type(boolean io, String owner, int array) {
        this.io = io;
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
    public void appendGeneric(StringBuilder sb) {
        ParamHelper.getOne(this, sb);
    }

    @Override
    public void appendString(StringBuilder sb) {
        sb.append(toString());
    }

    public int length() {
        return (array == 0 && (this.type == NativeType.LONG || this.type == NativeType.DOUBLE)) ? 2 : 1;
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

    public Type copy(boolean i) {
        Type type = new Type(i, this.type, array);
        type.owner = owner;
        return type;
    }
}
