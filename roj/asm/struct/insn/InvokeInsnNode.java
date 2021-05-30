/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: InvokeInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstRef;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

// invokevirtual
// invokespecial
// invokestatic
public class InvokeInsnNode extends InsnNode implements IInvocationInsnNode, IClassInsnNode {
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
        rawTypes(types);
    }

    public InvokeInsnNode(byte code, CstRef ref) {
        super(code);
        this.owner = ref.getClassName();
        this.name = ref.desc().getName().getString();
        this.params = ParamHelper.parseMethod(this.rawTypes = ref.desc().getType().getString());
        this.returnType = params.remove(params.size() - 1);
        // Only class file version 52.0 or above, can supports CONSTANT_Interface Methodref (CstRefItf).
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

    public String owner, name;
    public List<Type> params;
    public Type returnType;

    @Override
    public String owner() {
        return owner;
    }

    @Override
    public void owner(String clazz) {
        this.owner = clazz;
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
    public String name() {
        return name;
    }

    @Override
    public void name(String name) {
        this.name = name;
    }

    @Override
    public List<Type> parameters() {
        return params;
    }

    @Override
    public void rawTypes(String rawParam) {
        this.params = ParamHelper.parseMethod(this.rawTypes = rawParam);
        this.returnType = params.remove(params.size() - 1);
    }

    private String rawTypes;

    @Override
    public String rawTypes() {
        return this.rawTypes;
    }

    int mid;

    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        w.writeShort(mid);
    }

    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        toByteArray(w);

        // Only the invokespecial instruction is allowed to invoke an instance initialization method (§2.9.1).
        // No other method whose name begins with the character '<' ('\u003c') may be called by the method invocation instructions. In particular, the class or interface initialization method specially named <clinit> is never called explicitly from Java Virtual Machine instructions, but only implicitly by the Java Virtual Machine itself.
        // But I will not limit this because they are VALID instructions, only those cause crashes does I will limit.

        params.add(returnType);
        mid = pool.getMethodRefId(owner, name, ParamHelper.getMethod(params));
        params.remove(params.size() - 1);
    }

    /**
     * miasm/util/ByteWriter.writeShort:(I)Lmiasm/util/ByteWriter;
     *
     * @param descriptor javap descriptor
     */
    @Override
    public void rawDesc(String descriptor) {
        int index = descriptor.indexOf(".");
        String tmp = descriptor.substring(index + 1);
        int index2 = tmp.indexOf(":");

        this.owner = descriptor.substring(0, index);
        String name = tmp.substring(0, index2);
        if (name.contains("\"")) {
            name = name.substring(1, name.length() - 1);
        }
        this.name = name;

        List<Type> methodTypes = this.params == null ? (this.params = new ArrayList<>()) : this.params;
        methodTypes.clear();
        methodTypes.addAll(ParamHelper.parseMethod(this.rawTypes = tmp.substring(index2 + 1)));
        this.returnType = methodTypes.remove(methodTypes.size() - 1);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(' ').append(returnType).append(' ').append(owner.substring(owner.lastIndexOf('/') + 1)).append('.').append(name).append('(');
        if (!params.isEmpty()) {
            for (Type par : params) {
                sb.append(par).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append(')').toString();
    }
}