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
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.util.ByteWriter;

/**
 * invokevirtual invokespecial invokestatic
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class InvokeInsnNode extends IInvokeInsnNode implements IClassInsnNode {
    public InvokeInsnNode(byte code) {
        super(code);
    }

    public InvokeInsnNode(byte code, String descriptor) {
        super(code);
        rawDesc(descriptor);
    }

    public InvokeInsnNode(byte code, String owner, String name, String types) {
        super(code);
        this.owner = owner;
        this.name = name;
        this.rawParam = types;
    }

    public InvokeInsnNode(byte code, CstRef ref) {
        super(code);
        this.owner = ref.getClassName();
        this.name = ref.desc().getName().getString();
        this.rawParam = ref.desc().getType().getString();
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

    public void toByteArray(ConstantWriter cw, ByteWriter w) {
        if (params != null) {
            params.add(returnType);
            rawParam = ParamHelper.getMethod(params);
            params.remove(params.size() - 1);
        }
        w.writeByte(code).writeShort(cw.getMethodRefId(owner, name, rawParam));
    }

    @Override
    public int nodeSize() {
        return 3;
    }

    @Override
    public final void rawDesc(String desc) {
        int cIdx = desc.indexOf(".");

        this.owner = desc.substring(0, cIdx);

        int nIdx = desc.indexOf(":", cIdx + 1);
        String name = desc.substring(cIdx + 1, nIdx);
        if (name.charAt(0) == '"') {
            name = name.substring(1, name.length() - 1);
        }
        this.name = name;

        this.rawParam = desc.substring(nIdx + 1);
        if (params != null) {
            params.clear();
            ParamHelper.parseMethod(rawParam, params);
            returnType = params.remove(params.size() - 1);
        }
    }

    public final String toString() {
        initPar();
        StringBuilder sb = new StringBuilder(super.toString()).append(' ').append(returnType).append(' ').append(owner.substring(owner.lastIndexOf('/') + 1)).append('.').append(name).append('(');
        if (!params.isEmpty()) {
            for (int i = 0; i < params.size(); i++) {
                Type par = params.get(i);
                sb.append(par).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append(')').toString();
    }
}