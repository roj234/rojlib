package roj.lavac.expr;

import roj.asm.Opcodes;
import roj.asm.tree.MoFNode;
import roj.asm.type.Type;
import roj.lavac.parser.CompileContext;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/31 23:55
 */
public final class LavacUtil {
	public static void main(String[] args) {
		System.out.println(tryCast(args[0], args[1]));
	}

	/**
	 * Check access flag first!!! <BR>
	 * field cast
	 */
	public static boolean fieldCast(MoFNode from, MoFNode to, CompileContext acc) {
		return LavacUtil.tryCast(from.rawDesc(), to.rawDesc()) != 0 || acc.canInstanceOf(from.rawDesc(), to.rawDesc(), 0);
	}

	/**
	 * 宽化类型转换以及自动拆/包
	 *
	 * @param from 源类型
	 * @param to 目标类型
	 *
	 * @return 0: unable <BR>
	 * -2: go through <BR>
	 * -3: wrap <BR>
	 * -1: unwrap <BR>
	 * other: can and write opcodes
	 */
	public static int tryCast(String from, String to) {
		char fc = from.charAt(0);
		char tc = to.charAt(0);

		if (fc == tc && fc != '[' && fc != 'L') return -2;

		switch (tc) {
			case Type.LONG:
				switch (fc) {
					case Type.INT:
					case Type.BYTE:
					case Type.CHAR:
					case Type.SHORT:
						return Opcodes.I2L & 0xFF;
					case Type.DOUBLE:
						return Opcodes.D2L & 0xFF;
					case Type.FLOAT:
						return Opcodes.F2L & 0xFF;
					case Type.CLASS:
						return from.equals("Ljava/lang/Long;") ? -1 : 0;
				}
				break;
			case Type.BOOLEAN:
				if (fc == Type.CLASS) {
					return from.equals("Ljava/lang/Boolean;") ? -1 : 0;
				}
				break;
			case Type.INT:
				switch (fc) {
					case Type.BYTE:
					case Type.CHAR:
					case Type.SHORT:
						return -2;
					case Type.CLASS:
						return from.equals("Ljava/lang/Integer;") ? -1 : 0;
				}
				break;
			case Type.CHAR:
				break;
			case Type.SHORT:
				switch (fc) {
					case Type.BYTE:
					case Type.CHAR:
						return -2;
					case Type.CLASS:
						return from.equals("Ljava/lang/Short;") ? -1 : 0;
				}
				break;
			case Type.DOUBLE:
				switch (fc) {
					case Type.INT:
					case Type.BYTE:
					case Type.CHAR:
					case Type.SHORT:
						return Opcodes.I2D & 0xFF;
					case Type.FLOAT:
						return Opcodes.F2D & 0xFF;
					case Type.CLASS:
						return from.equals("Ljava/lang/Double;") ? -1 : 0;
				}
				break;
			case Type.FLOAT:
				switch (fc) {
					case Type.INT:
					case Type.BYTE:
					case Type.CHAR:
					case Type.SHORT:
						return Opcodes.I2F;
					case Type.CLASS:
						return from.equals("Ljava/lang/Float;") ? -1 : 0;
				}
				break;
			case Type.BYTE:
				break;
			case Type.CLASS:
				switch (to) {
					case "Ljava/lang/Boolean;":
						return fc == Type.BOOLEAN ? -3 : 0;
					case "Ljava/lang/Float;":
						return fc == Type.FLOAT ? -3 : 0;
					case "Ljava/lang/Integer;":
						return fc == Type.INT ? -3 : 0;
					case "Ljava/lang/Short;":
						return fc == Type.SHORT ? -3 : 0;
					case "Ljava/lang/Byte;":
						return fc == Type.BYTE ? -3 : 0;
					case "Ljava/lang/Double;":
						return fc == Type.DOUBLE ? -3 : 0;
					case "Ljava/lang/Long;":
						return fc == Type.LONG ? -3 : 0;
				}
		}
		return 0;
	}

	/**
	 * 宽化类型转换以及自动拆/包
	 *
	 * @param from 源类型
	 * @param to 目标类型
	 *
	 * @return 0: unable <BR>
	 * -2: go through as it is definitely equals to target type (e.g. short to integer) <BR>
	 * -3: wrap <BR>
	 * -1: unwrap <BR>
	 * other: can and write opcodes
	 */
	public static int primitiveCast(Type from, Type to) {
		byte fc = from.type;
		byte tc = to.type;

		if (fc == tc && fc != '[' && fc != 'L') return -2;

		switch (tc) {
			case Type.LONG:
				switch (fc) {
					case Type.INT:
					case Type.BYTE:
					case Type.CHAR:
					case Type.SHORT:
						return Opcodes.I2L & 0xFF;
					case Type.CLASS:
						return from.owner.equals("java/lang/Long") ? -1 : 0;
				}
				break;
			case Type.BOOLEAN:
				if (fc == Type.CLASS) {
					return from.owner.equals("java/lang/Boolean") ? -1 : 0;
				}
				break;
			case Type.INT:
				switch (fc) {
					case Type.BYTE:
					case Type.CHAR:
					case Type.SHORT:
						return -2;
					case Type.CLASS:
						return from.owner.equals("java/lang/Integer") ? -1 : 0;
				}
				break;
			case Type.CHAR:
				if (fc == Type.CLASS) {
					return from.owner.equals("java/lang/Character") ? -1 : 0;
				}
				break;
			case Type.SHORT:
				switch (fc) {
					case Type.BYTE:
						return -2;
					case Type.CLASS:
						return from.owner.equals("java/lang/Short") ? -1 : 0;
				}
				break;
			case Type.DOUBLE:
				switch (fc) {
					case Type.INT:
					case Type.BYTE:
					case Type.CHAR:
					case Type.SHORT:
						return Opcodes.I2D & 0xFF;
					case Type.FLOAT:
						return Opcodes.F2D & 0xFF;
					case Type.LONG:
						return Opcodes.L2D & 0xFF;
					case Type.CLASS:
						return from.owner.equals("java/lang/Double") ? -1 : 0;
				}
				break;
			case Type.FLOAT:
				switch (fc) {
					case Type.INT:
					case Type.BYTE:
					case Type.CHAR:
					case Type.SHORT:
						return Opcodes.I2F;
					case Type.LONG:
						return Opcodes.L2F & 0xFF;
					case Type.CLASS:
						return from.owner.equals("java/lang/Float") ? -1 : 0;
				}
				break;
			case Type.BYTE:
				break;
			case Type.CLASS:
				switch (to.owner) {
					case "java/lang/Boolean":
						return fc == Type.BOOLEAN ? -3 : 0;
					case "java/lang/Float":
						return fc == Type.FLOAT ? -3 : 0;
					case "java/lang/Integer":
						return fc == Type.INT ? -3 : 0;
					case "java/lang/Short":
						return fc == Type.SHORT ? -3 : 0;
					case "java/lang/Byte":
						return fc == Type.BYTE ? -3 : 0;
					case "java/lang/Double":
						return fc == Type.DOUBLE ? -3 : 0;
					case "java/lang/Long":
						return fc == Type.LONG ? -3 : 0;
				}
		}
		return 0;
	}
}
