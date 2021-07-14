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
package roj.kscript.parser.expr;

import roj.asm.Opcodes;
import roj.asm.struct.insn.InsnNode;
import roj.asm.struct.insn.InvokeInsnNode;
import roj.collect.CharMap;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/6/27 13:31
 */
public class NodeCache {
    static final ThreadLocal<CharMap<InsnNode>> CACHE = ThreadLocal.withInitial(CharMap::new);

    public static InsnNode a_asBool_0() {
        InsnNode node = CACHE.get().get('\0');
        if(node == null)
            CACHE.get().put('\0', node = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asBool", "()Z"));
        return node;
    }

    public static InsnNode a_asBool_1() {
        InsnNode node = CACHE.get().get('\1');
        if(node == null)
            CACHE.get().put('\1', node = new InvokeInsnNode(Opcodes.INVOKESTATIC, "roj/kscript/type/KBool", "valueOf", "(Z)Lroj/kscript/type/KBool;"));
        return node;
    }

    public static InsnNode a_asInt_0() {
        InsnNode node = CACHE.get().get('\2');
        if(node == null)
            CACHE.get().put('\2', node = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asBool", "()Z"));
        return node;
    }

    public static InsnNode a_field_0() {
        InsnNode node = CACHE.get().get('\3');
        if(node == null)
            CACHE.get().put('\3', node = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/IObject", "delete", "(Ljava/lang/String;)Z"));
        return node;
    }

    public static InsnNode a_field_1() {
        InsnNode node = CACHE.get().get('\4');
        if(node == null)
            CACHE.get().put('\4', node = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/IObject", "get", "(Ljava/lang/String;)Lroj/kscript/type/KType;"));
        return node;
    }
}
