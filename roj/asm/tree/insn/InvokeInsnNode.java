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

package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstRef;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.asm.util.InsnList;
import roj.util.ByteList;

import java.util.List;

/**
 * invokevirtual invokespecial invokestatic
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class InvokeInsnNode extends IInvokeInsnNode implements IClassInsnNode {
    public InvokeInsnNode(byte code) {
        super(code);
    }

    public InvokeInsnNode(byte code, String descriptor) {
        super(code);
        fullDesc(descriptor);
    }

    public InvokeInsnNode(byte code, String owner, String name, String types) {
        super(code);
        this.owner = owner;
        this.name = name;
        this.rawDesc = types;
    }

    public InvokeInsnNode(byte code, CstRef ref) {
        super(code);
        this.owner = ref.getClassName();
        this.name = ref.desc().getName().getString();
        this.rawDesc = ref.desc().getType().getString();
    }

    public InvokeInsnNode(byte code, IClass clazz, int index) {
        super(code);
        MoFNode mn = clazz.methods().get(index);
        this.owner = clazz.name();
        this.name = mn.name();
        this.rawDesc = mn.rawDesc();
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEVIRTUAL:
                return true;
        }
        return false;
    }

    @Override
    /**
     * fn validFor invokespecial() bool
     *   return name == <init> ||
     *      name in owner.methods ||
     *      name in owner.superClass.methods ||
     *      name in owner.interfaces.each(methods) ||
     *      name in Object.class
     */
    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
        if(name.startsWith("<")) {
            if(!name.equals("<init>"))
                throw new IllegalArgumentException("Calling methods name begin with '<' ('\\u003c') can only be named '<init>'");
            if(code != Opcodes.INVOKESPECIAL)
                throw new IllegalArgumentException("Only the invokespecial instruction is allowed to invoke an instance initialization method");
        }
    }

    public String owner;

    @Override
    public final int nodeType() {
        return T_INVOKE;
    }

    @Override
    public final String owner() {
        return owner;
    }

    @Override
    public final void owner(String clazz) {
        // noinspection all
        this.owner = clazz.toString();
    }

    public void toByteArray(ConstantPool cw, ByteList w) {
        w.put(code).putShort(cw.getMethodRefId(owner, name, rawDesc()));
    }

    @Override
    public int nodeSize() {
        return 3;
    }

    @Override
    public final void fullDesc(String desc) {
        int cIdx = desc.indexOf(".");

        this.owner = desc.substring(0, cIdx);

        int nIdx = desc.indexOf(":", cIdx + 1);
        String name = desc.substring(cIdx + 1, nIdx);
        if (name.charAt(0) == '"') {
            name = name.substring(1, name.length() - 1);
        }
        this.name = name;

        this.rawDesc = desc.substring(nIdx + 1);
        if (params != null) {
            params.clear();
            ParamHelper.parseMethod(rawDesc, params);
            returnType = params.remove(params.size() - 1);
        }
    }

    public final String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(' ').append(returnType()).append(' ').append(owner.substring(owner.lastIndexOf('/') + 1)).append('.').append(name).append('(');

        List<Type> params = parameters();
        if (!params.isEmpty()) {
            int i = 0;
            while (true) {
                Type par = params.get(i++);
                sb.append(par);
                if (i == params.size()) break;
                sb.append(", ");
            }
        }
        return sb.append(')').toString();
    }
}