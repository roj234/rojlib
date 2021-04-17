/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: IInvocationInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.util.type.Type;

import java.util.List;

public interface IInvocationInsnNode {
    byte getOpcode();

    void setOpcode(byte code);

    Type returnType();

    void returnType(Type returnType);

    String name();

    void name(String methodName);

    List<Type> parameters();

    void rawTypes(String rawParam);

    String rawTypes();

    void rawDesc(String descriptor);
}