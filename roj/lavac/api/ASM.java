/*
 * This file is a part of MoreItems
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
package roj.lavac.api;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.Opcodes;

/**
 * ASM by call
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/2 21:53
 */
public class ASM {
    /**
     * No parameter ASM
     * @param opcode Opcode
     */
    public static native void simple(@MagicConstant(valuesFromClass = Opcodes.class) byte opcode);
    public static native void simpleX(@MagicConstant(valuesFromClass = Opcodes.class) byte opcode, int p1);
    public static native void simpleX2(@MagicConstant(valuesFromClass = Opcodes.class) byte opcode, int p1, int p2);

    public static native Object load(Object from);
    public static native Object load_by_index(int index);
    public static native Object loadThis();

    public static native void store_pop(Object target);
    public static native void store(Object target, Object value);
    public static native void store_by_index(int index, Object value);

    public static native void keep_return_value(Object returnValue);

    public static native void assert_stack(int stackSize);
    public static native void assert_stack(int stackSize, String message);
    public static native void assert_local(int localSize);
    public static native void assert_local(int localSize, String message);
    public static native void assert_local_type(int localIndex, Object type);
    public static native void assert_local_type(int localIndex, Object type, String message);
}
