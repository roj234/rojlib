package roj.lavac.util;

import roj.asm.Opcodes;
import roj.asm.tree.MoFNode;
import roj.asm.type.NativeType;
import roj.asm.type.Type;
import roj.lavac.parser.IAccessor;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/31 23:55
 */
public final class Util {
    /**
     * Check access flag first!!! <BR>
     * field cast
     */
    public static boolean fieldCast(MoFNode from, MoFNode to, IAccessor acc) {
        return Util.upperTypeOrWrap(from.rawDesc(), to.rawDesc()) != 0 || acc.canInstanceOf(from.rawDesc(), to.rawDesc(), false);
    }

    /**
     * 宽化类型转换以及自动拆/包
     *
     * @param from 源类型
     * @param to   目标类型
     * @return 0: unable <BR>
     * -2: go through <BR>
     * -3: wrap <BR>
     * -1: unwrap <BR>
     * other: can and write opcodes
     */
    public static int upperTypeOrWrap(String from, String to) {
        char fc = from.charAt(0);
        char tc = to.charAt(1);

        if (fc == tc && fc != '[' && fc != 'L')
            return -2;

        switch (tc) {
            case NativeType.LONG:
                switch (fc) {
                    case NativeType.INT:
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                    case NativeType.SHORT:
                        return Opcodes.I2L & 0xFF;
                    case NativeType.DOUBLE:
                        return Opcodes.D2L & 0xFF;
                    case NativeType.FLOAT:
                        return Opcodes.F2L & 0xFF;
                    case NativeType.CLASS:
                        return from.equals("Ljava/lang/Long;") ? -1 : 0;
                }
                break;
            case NativeType.BOOLEAN:
                if (fc == NativeType.CLASS) {
                    return from.equals("Ljava/lang/Boolean;") ? -1 : 0;
                }
                break;
            case NativeType.INT:
                switch (fc) {
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                    case NativeType.SHORT:
                        return -2;
                    case NativeType.CLASS:
                        return from.equals("Ljava/lang/Integer;") ? -1 : 0;
                }
                break;
            case NativeType.CHAR:
                break;
            case NativeType.SHORT:
                switch (fc) {
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                        return -2;
                    case NativeType.CLASS:
                        return from.equals("Ljava/lang/Short;") ? -1 : 0;
                }
                break;
            case NativeType.DOUBLE:
                switch (fc) {
                    case NativeType.INT:
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                    case NativeType.SHORT:
                        return Opcodes.I2D & 0xFF;
                    case NativeType.FLOAT:
                        return Opcodes.F2D & 0xFF;
                    case NativeType.CLASS:
                        return from.equals("Ljava/lang/Double;") ? -1 : 0;
                }
                break;
            case NativeType.FLOAT:
                switch (fc) {
                    case NativeType.INT:
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                    case NativeType.SHORT:
                        return Opcodes.I2F;
                    case NativeType.CLASS:
                        return from.equals("Ljava/lang/Float;") ? -1 : 0;
                }
                break;
            case NativeType.BYTE:
                break;
            case NativeType.CLASS:
                switch (to) {
                    case "Ljava/lang/Boolean;":
                        return fc == NativeType.BOOLEAN ? -3 : 0;
                    case "Ljava/lang/Float;":
                        return fc == NativeType.FLOAT ? -3 : 0;
                    case "Ljava/lang/Integer;":
                        return fc == NativeType.INT ? -3 : 0;
                    case "Ljava/lang/Short;":
                        return fc == NativeType.SHORT ? -3 : 0;
                    case "Ljava/lang/Byte;":
                        return fc == NativeType.BYTE ? -3 : 0;
                    case "Ljava/lang/Double;":
                        return fc == NativeType.DOUBLE ? -3 : 0;
                    case "Ljava/lang/Long;":
                        return fc == NativeType.LONG ? -3 : 0;
                }
        }
        return 0;
    }

    /**
     * 宽化类型转换以及自动拆/包
     *
     * @param from 源类型
     * @param to   目标类型
     * @return 0: unable <BR>
     * -2: go through as it is definitely equals to target type (e.g. short to integer) <BR>
     * -3: wrap <BR>
     * -1: unwrap <BR>
     * other: can and write opcodes
     */
    public static int upperTypeOrWrap(Type from, Type to) {
        byte fc = from.type;
        byte tc = to.type;

        if (fc == tc && fc != '[' && fc != 'L')
            return -2;

        switch (tc) {
            case NativeType.LONG:
                switch (fc) {
                    case NativeType.INT:
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                    case NativeType.SHORT:
                        return Opcodes.I2L & 0xFF;
                    case NativeType.DOUBLE:
                        return Opcodes.D2L & 0xFF;
                    case NativeType.FLOAT:
                        return Opcodes.F2L & 0xFF;
                    case NativeType.CLASS:
                        return from.owner.equals("java/lang/Long") ? -1 : 0;
                }
                break;
            case NativeType.BOOLEAN:
                if (fc == NativeType.CLASS) {
                    return from.owner.equals("java/lang/Boolean") ? -1 : 0;
                }
                break;
            case NativeType.INT:
                switch (fc) {
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                    case NativeType.SHORT:
                        return -2;
                    case NativeType.CLASS:
                        return from.owner.equals("java/lang/Integer") ? -1 : 0;
                }
                break;
            case NativeType.CHAR:
                break;
            case NativeType.SHORT:
                switch (fc) {
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                        return -2;
                    case NativeType.CLASS:
                        return from.owner.equals("java/lang/Short") ? -1 : 0;
                }
                break;
            case NativeType.DOUBLE:
                switch (fc) {
                    case NativeType.INT:
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                    case NativeType.SHORT:
                        return Opcodes.I2D & 0xFF;
                    case NativeType.FLOAT:
                        return Opcodes.F2D & 0xFF;
                    case NativeType.CLASS:
                        return from.owner.equals("java/lang/Double") ? -1 : 0;
                }
                break;
            case NativeType.FLOAT:
                switch (fc) {
                    case NativeType.INT:
                    case NativeType.BYTE:
                    case NativeType.CHAR:
                    case NativeType.SHORT:
                        return Opcodes.I2F;
                    case NativeType.CLASS:
                        return from.owner.equals("java/lang/Float") ? -1 : 0;
                }
                break;
            case NativeType.BYTE:
                break;
            case NativeType.CLASS:
                switch (to.owner) {
                    case "java/lang/Boolean":
                        return fc == NativeType.BOOLEAN ? -3 : 0;
                    case "java/lang/Float":
                        return fc == NativeType.FLOAT ? -3 : 0;
                    case "java/lang/Integer":
                        return fc == NativeType.INT ? -3 : 0;
                    case "java/lang/Short":
                        return fc == NativeType.SHORT ? -3 : 0;
                    case "java/lang/Byte":
                        return fc == NativeType.BYTE ? -3 : 0;
                    case "java/lang/Double":
                        return fc == NativeType.DOUBLE ? -3 : 0;
                    case "java/lang/Long":
                        return fc == NativeType.LONG ? -3 : 0;
                }
        }
        return 0;
    }
}
