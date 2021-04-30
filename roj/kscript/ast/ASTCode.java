package roj.kscript.ast;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 12:31
 */
public enum ASTCode {
    GET_OBJECT, PUT_OBJECT, DELETE_OBJECT, // 属性(Object)
    ADD_ARRAY, // 数组 []=
    INVOKE_FUNCTION, INVOKE_NEW, INSTANCE_OF, // 函数
    POP, DUP, DUP2, DUP2_2, // 栈
    IF, IF_LOAD, GOTO, RETURN, RETURN_EMPTY, // 流程控制
    SWITCH, // 选择
    NOT, OR, XOR, AND, SHIFT_L, SHIFT_R, U_SHIFT_R, REVERSE, NEGATIVE, // 基础数学操作
    IINC, ADD, SUB, MUL, DIV, MOD, // 高级数学操作
    LOAD_DATA, LOAD_THIS, LOAD_VARIABLE, SET_VARIABLE, // LDC
    TRY_ENTER, TRY_EXIT, TRY_END,
    LABEL, // GOTO的目标
    SWAP, THROW // 技术性
}
