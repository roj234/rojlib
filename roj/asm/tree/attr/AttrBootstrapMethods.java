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

package roj.asm.tree.attr;

import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static roj.asm.cst.CstType.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class AttrBootstrapMethods extends Attribute {
    public AttrBootstrapMethods() {
        super("BootstrapMethods");
        methods = new ArrayList<>();
    }

    public AttrBootstrapMethods(ByteReader r, ConstantPool pool) {
        super("BootstrapMethods");
        methods = parse(r, pool);
    }

    public final List<BootstrapMethod> methods;

    /*
        u2 num_bootstrap_methods;
    {   u2 bootstrap_method_ref;
        u2 num_bootstrap_arguments;
        u2 bootstrap_arguments[num_bootstrap_arguments];
    } bootstrap_methods[num_bootstrap_methods];
    */
    public static List<BootstrapMethod> parse(ByteReader r, ConstantPool pool) {
        int len = r.readUnsignedShort();
        List<BootstrapMethod> methods = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            CstMethodHandle handle = (CstMethodHandle) pool.get(r);
            if (handle.kind != 6 && handle.kind != 8)
                throw new IllegalStateException("The reference_kind item of the CONSTANT_MethodHandle_info structure should have the value 6 or 8 (§5.4.3.5).");
            // parsing method
            int argc = r.readUnsignedShort();
            List<Constant> list = new ArrayList<>(argc);
            for (int j = 0; j < argc; j++) {
                Constant c = pool.get(r);
                switch (c.type()) {
                    case STRING:
                    case CLASS:
                    case INT:
                    case LONG:
                    case FLOAT:
                    case DOUBLE:
                    case METHOD_HANDLE:
                    case METHOD_TYPE:
                        break;
                    default:
                        throw new IllegalStateException("Only accept STRING CLASS INT LONG FLOAT DOUBLE METHOD_HANDLE or METHOD_TYPE, got " + c);
                }
                list.add(c);
            }
            final CstRef ref = handle.getRef();
            methods.add(new BootstrapMethod(ref.getClassName(), ref.desc().getName().getString(), ref.desc().getType().getString(), handle.kind, ref.type(), list));
        }

        return methods;
    }

    /**
     * InvokeDynamic
     * <pre>
     *
     * If nativeName ==  1 (REF_getField), 2 (REF_getStatic), 3 (REF_putField), or 4 (REF_putStatic)
     *      ref = CONSTANT_Fieldref_info
     *
     * If nativeName ==  5 (REF_invokeVirtual) or 8 (REF_newInvokeSpecial),
     *      ref = CONSTANT_Methodref_info
     *
     * If nativeName == 6 (REF_invokeStatic) or 7 (REF_invokeSpecial),
     *      if(version number < 52)
     *          ref = CONSTANT_Methodref_info
     *      else
     *          ref = either a CONSTANT_Methodref_info or a CONSTANT_InterfaceMethodref_info
     *
     * If nativeName == 9 (REF_invokeInterface)
     *      ref = CONSTANT_InterfaceMethodref_info
     * </pre>
     */
    public static final class Kind {
        public static byte GETFIELD = 1,
                GETSTATIC = 2, PUTFIELD = 3, PUTSTATIC = 4,
                INVOKEVIRTUAL = 5, INVOKESTATIC = 6, INVOKESPECIAL = 7, NEW_INVOKESPECIAL = 8,
                INVOKEINTERFACE = 9;

        public static boolean verifyType(byte kind, byte type) {
            switch (kind) {
                case 1:
                case 2:
                case 3:
                case 4:
                    return type == CstType.FIELD;
                case 5:
                case 8:
                    return type == CstType.METHOD;
                case 6:
                case 7:
                    return type == CstType.METHOD || type == CstType.INTERFACE;
                case 9:
                    return type == CstType.INTERFACE;
            }
            return false;
        }

        static final byte[] toString = {
                Opcodes.GETFIELD, Opcodes.GETSTATIC, Opcodes.PUTFIELD, Opcodes.PUTSTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC, Opcodes.INVOKESPECIAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE
        };

        public static String toString(byte kind) {
            return Opcodes.toString0(toString[kind]);
        }

        public static byte validate(int kind) {
            if (kind < 1 || kind > 9)
                throw new IllegalArgumentException("Illegal kind " + kind + ". Must in [1-9]");
            return (byte) kind;
        }
    }

    public static class BootstrapMethod {
        /**
         * 仅比较
         */
        public BootstrapMethod(String owner, String name, String desc, int kind) {
            this.owner = owner;
            this.name = name;
            this.rawDesc = desc;
            this.kind = Kind.validate(kind);
            this.parameters = Collections.emptyList();
        }

        public BootstrapMethod(String owner, String name, String desc, byte kind, byte methodType, List<Constant> arguments) {
            this.owner = owner;
            this.name = name;
            this.parameters = ParamHelper.parseMethod(this.rawDesc = desc);
            this.returnType = parameters.remove(parameters.size() - 1);
            this.kind = Kind.validate(kind);
            this.methodType = methodType;
            this.arguments = arguments;
        }

        public final String rawDesc;

        public String owner, name;

        public final List<Type> parameters;
        public Type returnType;

        public List<Constant> arguments;

        public byte kind, methodType;

        public void toByteArray(ConstantWriter pool, ByteWriter w) {
            if (!Kind.verifyType(kind, methodType)) {
                throw new IllegalArgumentException("Method type " + methodType + " doesn't fit with lambda kind " + kind);
            }
            parameters.add(returnType);
            w.writeShort(pool.getMethodHandleId(owner, name, ParamHelper.getMethod(parameters), kind, methodType));
            parameters.remove(parameters.size() - 1);

            w.writeShort(arguments.size());
            for (Constant c : arguments) {
                w.writeShort(pool.reset(c).getIndex());
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("type=").append(Kind.toString(kind))
                    .append("\n            Site: ").append(returnType).append(' ').append(owner.substring(owner.lastIndexOf('/') + 1)).append('.').append(name)
                    .append('(');

            if (!parameters.isEmpty()) {
                for (Type p : parameters) {
                    sb.append(p).append(", ");
                }
                sb.delete(sb.length() - 2, sb.length());
            }
            sb.append(")\n            Desc: ");

            CstMethodType mType = null;
            for (Constant c : arguments) {
                if (c.type() == CstType.METHOD_TYPE) {
                    mType = (CstMethodType) c;
                    break;
                }
            }

            if (mType == null) {
                throw new IllegalArgumentException("METHOD_TYPE argument not found. ");
            }

            String methodDesc = mType.getValue().getString();

            List<Type> types = ParamHelper.parseMethod(methodDesc);

            sb.append(types.remove(types.size() - 1)).append(" .dynamic(");
            if (!types.isEmpty()) {
                for (Type p : types) {
                    sb.append(p).append(", ");
                }
                sb.delete(sb.length() - 2, sb.length());
            }
            return sb.append(')').append('\n').toString();
        }

        public boolean equals0(BootstrapMethod method) {
            return method.kind == this.kind && method.owner.equals(this.owner) && method.name.equals(this.name) && method.rawDesc.equals(this.rawDesc);
        }
    }

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(methods.size());
        for (BootstrapMethod method : methods) {
            method.toByteArray(pool, w);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("BootstrapMethods: \n");
        int i = 0;
        for (BootstrapMethod method : methods) {
            sb.append("         #").append(i++).append(": ").append(method).append('\n');
        }
        sb.delete(sb.length() - 2, sb.length());
        return sb.toString();
    }
}