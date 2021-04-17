/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: InvokeDynamicInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.constant.CstDynamic;
import roj.asm.util.ConstantWriter;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;
import roj.util.ByteWriter;

import java.util.List;

public class InvokeDynamicInsnNode extends InsnNode implements IInvocationInsnNode {
    public InvokeDynamicInsnNode() {
        super(Opcodes.INVOKEDYNAMIC);
    }

    public InvokeDynamicInsnNode(CstDynamic ref, int type) {
        super(Opcodes.INVOKEDYNAMIC);
        //this.type = type;
        this.bootstrapTableIndex = ref.bootstrapTableIndex;
        this.name = ref.getDesc().getName().getString();
        this.parameters = ParamHelper.parseMethod(ref.getDesc().getType().getString());
        this.returnType = parameters.remove(parameters.size() - 1);
    }

    @Override
    protected boolean validate() {
        return code == Opcodes.INVOKEDYNAMIC;
    }

    //public int type;
    /**
     * bootstrap table index
     */
    public int bootstrapTableIndex;
    public String name;
    public List<Type> parameters;
    public Type returnType;

    private int did;

    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        // The third and fourth operand bytes of each invokedynamic instruction must have the value zero.
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


        parameters.add(returnType);
        this.did = pool.getInvokeDynId((short) bootstrapTableIndex, name, ParamHelper.getMethod(parameters));
        parameters.remove(parameters.size() - 1);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(" #").append(bootstrapTableIndex).append(' ').append(returnType).append(" ?").append('.').append(name).append('(');
        for (Type par : parameters) {
            sb.append(par).append(", ");
        }
        if (!parameters.isEmpty())
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
        return parameters;
    }

    @Override
    public void rawTypes(String rawParam) {
        this.parameters = ParamHelper.parseMethod(this.rawParam = rawParam);
        this.returnType = parameters.remove(parameters.size() - 1);
    }

    private String rawParam;

    @Override
    public String rawTypes() {
        return this.rawParam;
    }

    @Override
    public void rawDesc(String descriptor) {
        throw new UnsupportedOperationException();
    }
}