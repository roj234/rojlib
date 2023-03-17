package roj.kscript.asm;

/**
 * @author Roj234
 * @since 2020/9/27 12:31
 */
public enum Opcode {
	GET_OBJ, PUT_OBJ, DELETE_OBJ, // 属性(Object)
	ADD_ARRAY, // 数组 []=
	INVOKE, INSTANCE_OF, // 函数
	POP, DUP, DUP2, SWAP, SWAP3, // 栈
	IF, IF_LOAD, GOTO, RETURN, RETURN_EMPTY, SWITCH, THROW, // 流程控制
	NOT, OR, XOR, AND, SHIFT_L, SHIFT_R, U_SHIFT_R, REVERSE, NEGATIVE, INCREASE, ADD, SUB, MUL, DIV, MOD, POW, // 数学操作
	LOAD, THIS, ARGUMENTS, CAST_INT, CAST_BOOL, SPREAD_ARRAY, GET_VAR, PUT_VAR, // LDC
	TRY_ENTER, TRY_EXIT, LABEL, USELESS;

	static final Opcode[] values = values();

	public static Opcode byId(byte code) {
		return values[code];
	}
}
