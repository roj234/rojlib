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
package roj.kscript.asm;

/**
 * @author Roj234
 * @since  2020/9/27 12:31
 */
public enum Opcode {
    GET_OBJ, PUT_OBJ, DELETE_OBJ, // 属性(Object)
    ADD_ARRAY, // 数组 []=
    INVOKE, INSTANCE_OF, // 函数
    POP, DUP, DUP2, SWAP, SWAP3, // 栈
    IF, IF_LOAD, GOTO, RETURN, RETURN_EMPTY, SWITCH, THROW, // 流程控制
    NOT, OR, XOR, AND, SHIFT_L, SHIFT_R, U_SHIFT_R, REVERSE, NEGATIVE,
    INCREASE, ADD, SUB, MUL, DIV, MOD, POW, // 数学操作
    LOAD, THIS, ARGUMENTS,
    CAST_INT, CAST_BOOL,
    SPREAD_ARRAY,
    GET_VAR, PUT_VAR, // LDC
    TRY_ENTER, TRY_EXIT,
    LABEL, USELESS;

    static final Opcode[] values = values();

    public static Opcode byId(byte code) {
        return values[code];
    }
}
