package roj.kscript.ast;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 12:31
 */
public enum OpCode {
    GET_OBJ, PUT_OBJ, DELETE_OBJ, // 属性(Object)
    ADD_ARRAY, // 数组 []=
    INVOKE, INVOKE_NEW, INSTANCE_OF, // 函数
    POP, DUP, DUP2, SWAP, SWAP3, // 栈
    IF, IF_LOAD, GOTO, RETURN, RETURN_EMPTY, SWITCH, THROW, // 流程控制
    NOT, OR, XOR, AND, SHIFT_L, SHIFT_R, U_SHIFT_R, REVERSE, NEGATIVE, // 基础数学操作
    INCREASE, ADD, SUB, MUL, DIV, MOD, // 高级数学操作
    LOAD, THIS, ARGUMENTS, GET_VAR, PUT_VAR, // LDC
    TRY_ENTER, TRY_EXIT, TRY_END,
    LABEL
}
