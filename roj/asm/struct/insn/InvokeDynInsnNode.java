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

package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstDynamic;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantWriter;
import roj.util.ByteWriter;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class InvokeDynInsnNode extends InsnNode implements IInvocationInsnNode {
    public InvokeDynInsnNode() {
        super(Opcodes.INVOKEDYNAMIC);
    }

    public InvokeDynInsnNode(CstDynamic ref, int type) {
        super(Opcodes.INVOKEDYNAMIC);
        this.bootstrapTableIndex = ref.bootstrapTableIndex;
        this.name = ref.getDesc().getName().getString();
        this.params = ParamHelper.parseMethod(ref.getDesc().getType().getString());
        this.returnType = params.remove(params.size() - 1);
    }

    @Override
    protected boolean validate() {
        return code == Opcodes.INVOKEDYNAMIC;
    }
    /**
     * bootstrap table index
     */
    public int bootstrapTableIndex;
    public String name;
    public List<Type> params;
    public Type returnType;

    private int did;

    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        // The third and fourth operand bytes of each invokedynamic instruction must have the value zero.
        // Thus, we ignore it again(Previous in InvokeItfInsnNode).
        w.writeShort(this.did).writeShort(0);
    }

    /**
     * indexbyte1
     * indexbyte2
     * 0
     * 0
     */
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        toByteArray(w);

        params.add(returnType);
        this.did = pool.getInvokeDynId((short) bootstrapTableIndex, name, ParamHelper.getMethod(params));
        params.remove(params.size() - 1);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(" #").append(bootstrapTableIndex).append(' ').append(returnType).append(" ?").append('.').append(name).append('(');
        for (Type par : params) {
            sb.append(par).append(", ");
        }
        if (!params.isEmpty())
            sb.delete(sb.length() - 2, sb.length());
        return sb.append(')').toString();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void name(String methodName) {
        this.name = methodName;
    }

    @Override
    public Type returnType() {
        return returnType;
    }

    @Override
    public void returnType(Type returnType) {
        this.returnType = returnType;
    }

    @Override
    public List<Type> parameters() {
        return params;
    }

    @Override
    public void rawTypes(String rawParam) {
        this.params = ParamHelper.parseMethod(this.rawParam = rawParam);
        this.returnType = params.remove(params.size() - 1);
    }

    private String rawParam;

    @Override
    public String rawTypes() {
        return this.rawParam;
    }

    @Override
    public void rawDesc(String descriptor) {
        throw new UnsupportedOperationException("InvokeDyn does not support 'classed' descriptor");
    }
}