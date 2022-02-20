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
package roj.asm.util;

import roj.asm.Opcodes;
import roj.asm.cst.CstDouble;
import roj.asm.cst.CstFloat;
import roj.asm.cst.CstInt;
import roj.asm.cst.CstLong;
import roj.asm.tree.insn.*;
import roj.asm.type.Type;

import javax.annotation.Nonnull;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class NodeHelper {
    public static void insertDebug(InsnList list) {
        list.add(NPInsnNode.of(DUP));
        list.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", new Type("java/lang/PrintStream")));
        list.add(NPInsnNode.of(SWAP));
        list.add(new InvokeInsnNode(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;"));
        list.add(new InvokeInsnNode(INVOKEVIRTUAL, "java/lang/PrintStream", "println", "(Ljava/lang/String;)V"));
    }

    private static NPInsnNode getNode(char x, byte base) {
        return NPInsnNode.of(getValue(x, base));
    }

    private static byte getValue(char x, byte base) {
        switch (Character.toUpperCase(x)) {
            case 'I':
                break;
            case 'L':
                base += 1;
                break;
            case 'F':
                base += 2;
                break;
            case 'D':
                base += 3;
                break;
            case 'A':
                base += 4;
                break;
            default:
                throw new NumberFormatException("Value out of range [ILFDA] got " + x);
        }
        return base;
    }

    public static byte X_STORE(char x) {
        return getValue(x, ISTORE);
    }

    public static byte X_LOAD(char x) {
        return getValue(x, ILOAD);
    }

    public static NPInsnNode X_ARRAY_STORE(char x) {
        return getNode(x, IASTORE);
    }

    public static NPInsnNode X_ARRAY_LOAD(char x) {
        return getNode(x, IALOAD);
    }

    public static NPInsnNode X_RETURN(String x) {
        return NPInsnNode.of(x.isEmpty() ? RETURN : getValue(x.charAt(0), IRETURN));
    }

    public static NPInsnNode X_LOAD_I(char x, int i) {
        if (i < 0 || i > 3)
            throw new NumberFormatException("i not in [0, 3] : " + i);
        return loadSore(getValue(x, ILOAD), i);
    }

    public static NPInsnNode XSTORE_I(char x, int i) {
        if (i < 0 || i > 3)
            throw new NumberFormatException("i not in [0, 3] : " + i);
        return loadSore(getValue(x, ISTORE), i);
    }

    public static InsnNode loadInt(int number) {
        switch (number) {
            case -1:
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return NPInsnNode.of((byte) (ICONST_0 + number));
            default:
                if((byte)number == number) {
                    return new U1InsnNode(BIPUSH, number);
                }
                if((short)number == number) {
                    return new U2InsnNode(SIPUSH, number);
                }
                return new LdcInsnNode(LDC, new CstInt(number));
        }
    }

    public static void loadLongSlow(long number, InsnList target) {
        switch ((int) number) {
            case 0:
            case 1:
                target.add(NPInsnNode.of((byte) (LCONST_0 + number)));
                break;
            case -1:
            case 2:
            case 3:
            case 4:
            case 5:
                target.add(NPInsnNode.of((byte) (ICONST_0 + number)));
                target.add(NPInsnNode.of(I2L));
                break;
            default:
                if((byte)number == number) {
                    target.add(new U2InsnNode(BIPUSH, (int) number));
                    target.add(NPInsnNode.of(I2L));
                } else if((short)number == number) {
                    target.add(new U2InsnNode(SIPUSH, (int) number));
                    target.add(NPInsnNode.of(I2L));
                } else if ((int)number == number) {
                    target.add(new LdcInsnNode(LDC, new CstInt((int) number)));
                    target.add(NPInsnNode.of(I2L));
                } else {
                    target.add(new LdcInsnNode(LDC2_W, new CstLong(number)));
                }
        }
    }

    public static InsnNode loadLong(long number) {
        switch ((int) number) {
            case 0:
            case 1:
                return NPInsnNode.of((byte) (LCONST_0 + number));
            default:
                return new LdcInsnNode(LDC2_W, new CstLong(number));
        }
    }

    public static void loadFloatSlow(float number, InsnList target) {
        int n = (int) number;
        if (number != n) {
            target.add(new LdcInsnNode(LDC, new CstFloat(number)));
            return;
        }
        switch (n) {
            case 0:
            case 1:
            case 2:
                target.add(NPInsnNode.of((byte) (FCONST_0 + number)));
                break;
            case -1:
            case 3:
            case 4:
            case 5:
                target.add(NPInsnNode.of((byte) (ICONST_0 + number)));
                target.add(NPInsnNode.of(I2F));
                break;
            default:
                if((byte)n == n) {
                    target.add(new U2InsnNode(BIPUSH, n));
                    target.add(NPInsnNode.of(I2F));
                } /*else if((short)n == n) {
                    target.add(new U2InsnNode(SIPUSH, n));
                    target.add(of(I2F));
                } */else {
                    target.add(new LdcInsnNode(LDC, new CstFloat(number)));
                }
        }
    }

    public static InsnNode loadFloat(float number) {
        int n = (int) number;
        if (number != n || n < 0 || n > 2) {
            return new LdcInsnNode(LDC, new CstFloat(number));
        }
        return NPInsnNode.of((byte) (FCONST_0 + number));
    }

    public static void loadDoubleSlow(double number, InsnList target) {
        int n = (int) number;
        if (number != n) {
            target.add(new LdcInsnNode(LDC2_W, new CstDouble(number)));
            return;
        }
        switch (n) {
            case 0:
            case 1:
                target.add(NPInsnNode.of((byte) (DCONST_0 + number)));
                break;
            case -1:
            case 2:
            case 3:
            case 4:
            case 5:
                target.add(NPInsnNode.of((byte) (ICONST_0 + number)));
                target.add(NPInsnNode.of(I2D));
                break;
            default:
                if((byte)n == n) {
                    target.add(new U2InsnNode(BIPUSH, n));
                } else if((short)n == n) {
                    target.add(new U2InsnNode(SIPUSH, n));
                } else {
                    //target.add(new LdcInsnNode(LDC, new CstInt(n)));
                    target.add(new LdcInsnNode(LDC2_W, new CstDouble(number)));
                    return;
                }
                target.add(NPInsnNode.of(I2D));
        }
    }

    public static InsnNode loadDouble(double number) {
        int n = (int) number;
        if (number != n || n < 0 || n > 1) {
            return new LdcInsnNode(LDC2_W, new CstDouble(number));
        }
        return NPInsnNode.of((byte) (DCONST_0 + number));
    }

    public static void compress(@Nonnull InsnList list, byte base, int id) {
        switch (base) {
            case ALOAD:
            case DLOAD:
            case ILOAD:
            case FLOAD:
            case LLOAD:
            case ISTORE:
            case FSTORE:
            case LSTORE:
            case DSTORE:
            case ASTORE:
                if (id <= 3) {
                    list.add(loadSore(base, id));
                } else if (id <= 255) {
                    list.add(new U1InsnNode(base, id));
                } else if (id <= 65535) {
                    list.add(NPInsnNode.of(Opcodes.WIDE));
                    list.add(new U2InsnNode(base, id));
                } else {
                    throw new IllegalArgumentException("No more thad 65535 types!");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported base 0x" + Integer.toHexString(base));
        }
    }

    @Nonnull
    public static NPInsnNode loadSore(byte base, int id) {
        return NPInsnNode.of((byte) ((base <= 25 ? ((base - 0x15) * 4 + 0x1a) : ((base - 0x36) * 4 + 0x3b)) + id));
    }

    public static int getVarId(InsnNode node) {
        switch (node.getOpcode()) {
            case ALOAD_0:
            case FLOAD_0:
            case ILOAD_0:
            case DLOAD_0:
            case LLOAD_0:

            case ASTORE_0:
            case FSTORE_0:
            case ISTORE_0:
            case DSTORE_0:
            case LSTORE_0:
                return 0;

            case ALOAD_1:
            case FLOAD_1:
            case ILOAD_1:
            case DLOAD_1:
            case LLOAD_1:

            case ASTORE_1:
            case FSTORE_1:
            case ISTORE_1:
            case DSTORE_1:
            case LSTORE_1:
                return 1;

            case ALOAD_2:
            case FLOAD_2:
            case ILOAD_2:
            case DLOAD_2:
            case LLOAD_2:

            case ASTORE_2:
            case FSTORE_2:
            case ISTORE_2:
            case DSTORE_2:
            case LSTORE_2:
                return 2;

            case ALOAD_3:
            case FLOAD_3:
            case ILOAD_3:
            case DLOAD_3:
            case LLOAD_3:

            case ASTORE_3:
            case FSTORE_3:
            case ISTORE_3:
            case DSTORE_3:
            case LSTORE_3:
                return 3;

            case ALOAD:
            case DLOAD:
            case FLOAD:
            case LLOAD:
            case ILOAD:

            case ASTORE:
            case FSTORE:
            case ISTORE:
            case DSTORE:
            case LSTORE:
                return ((IIndexInsnNode) node).getIndex();
            default:
                return -1;
        }
    }

    public static InsnNode decompress(InsnNode node) {
        switch (node.getOpcode()) {
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:
                return new U1InsnNode(ASTORE, (node.getOpcode() - ASTORE_0));

            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3:
                return new U1InsnNode(FSTORE, (node.getOpcode() - FSTORE_0));

            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:
                return new U1InsnNode(ISTORE, (node.getOpcode() - ISTORE_0));

            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3:
                return new U1InsnNode(DSTORE, (node.getOpcode() - DSTORE_0));

            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3:
                return new U1InsnNode(LSTORE, (node.getOpcode() - LSTORE_0));

            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:
                return new U1InsnNode(ALOAD, (node.getOpcode() - ALOAD_0));

            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:
                return new U1InsnNode(FLOAD, (node.getOpcode() - FLOAD_0));

            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:
                return new U1InsnNode(ILOAD, (node.getOpcode() - ILOAD_0));

            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:
                return new U1InsnNode(DLOAD, (node.getOpcode() - DLOAD_0));

            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:
                return new U1InsnNode(LLOAD, (node.getOpcode() - LLOAD_0));

            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case ICONST_M1:
                return new LdcInsnNode(LDC, new CstInt(node.getOpcode() - ICONST_0));

            case LCONST_0:
            case LCONST_1:
                return new LdcInsnNode(Opcodes.LDC2_W, new CstLong(node.getOpcode() - LCONST_0));

            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                return new LdcInsnNode(Opcodes.LDC, new CstFloat(node.getOpcode() - FCONST_0));

            case DCONST_0:
            case DCONST_1:
                return new LdcInsnNode(Opcodes.LDC2_W, new CstDouble(node.getOpcode() - DCONST_0));

            case BIPUSH:
            case SIPUSH:
                return new LdcInsnNode(Opcodes.LDC, new CstInt(((IIndexInsnNode) node).getIndex()));

            case ALOAD:
            case DLOAD:
            case FLOAD:
            case LLOAD:
            case ILOAD:

            case ASTORE:
            case FSTORE:
            case ISTORE:
            case DSTORE:
            case LSTORE:
            default:
                return node;
        }
    }

    public static boolean isReturn(int code) {
        code &= 0xFF;
        return code >= 0xAC && code <= 0xB1;
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
}
