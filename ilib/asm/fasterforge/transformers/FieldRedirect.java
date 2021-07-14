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
package ilib.asm.fasterforge.transformers;

import net.minecraft.launchwrapper.IClassTransformer;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.struct.ConstantData;
import roj.asm.struct.attr.AttrCode;
import roj.asm.struct.insn.FieldInsnNode;
import roj.asm.struct.insn.InsnNode;
import roj.asm.struct.insn.InvokeInsnNode;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.MethodSimple;

import java.util.ListIterator;

public class FieldRedirect implements IClassTransformer {
    private final String clsName, fieldType, methodDesc;

    private final String bypass;

    public FieldRedirect(String desc, String cls, String type, String bypass) {
        this.desc = desc;
        this.clsName = cls;
        this.fieldType = type;
        this.methodDesc = "()" + type;
        this.bypass = bypass;
    }

    String desc;

    @Override
    public String toString() {
        return getClass().getName() + '[' + desc + ']';
    }

    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!this.clsName.equals(transformedName))
            return basicClass;
        ConstantData classNode = Parser.parseConstants(basicClass, true);
        FieldSimple fieldRef = null;
        for (FieldSimple f : classNode.fields) {
            if (this.fieldType.equals(f.type.getString()) && fieldRef == null) {
                fieldRef = f;
                continue;
            }
            if (this.fieldType.equals(f.type.getString()))
                throw new RuntimeException("Error processing " + this.clsName + " - found a duplicate holder field");
        }
        if (fieldRef == null)
            throw new RuntimeException("Error processing " + this.clsName + " - no holder field declared (is the code somehow obfuscated?)");
        MethodSimple getMethod = null;
        for (MethodSimple m : classNode.methods) {
            if (m.name.getString().equals(this.bypass))
                continue;
            if (this.methodDesc.equals(m.type.getString()) && getMethod == null) {
                getMethod = m;
                continue;
            }
            if (this.methodDesc.equals(m.type.getString()))
                throw new RuntimeException("Error processing " + this.clsName + " - duplicate get method found");
        }
        if (getMethod == null)
            throw new RuntimeException("Error processing " + this.clsName + " - no get method found (is the code somehow obfuscated?)");
        for (MethodSimple method : classNode.methods) {
            if (method.name.getString().equals(this.bypass))
                continue;

            AttrCode code = Parser.getOrCreateCode(classNode, method);
            if (code == null) continue;

            for (ListIterator<InsnNode> it = code.instructions.listIterator(); it.hasNext(); ) {
                InsnNode insnNode = it.next();
                if (insnNode instanceof FieldInsnNode) {
                    FieldInsnNode fi = (FieldInsnNode) insnNode;
                    // GETFIELD
                    if (fieldRef.name.getString().equals(fi.name) && (fi.code & 0xFF) == 180) {
                        //System.out.println("Found FieldRef fit " + fi + " || " + fieldRef);
                        InvokeInsnNode replace = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL);
                        replace.owner(classNode.name);
                        replace.name(getMethod.name.getString());
                        replace.rawTypes(getMethod.type.getString());
                        it.set(replace);
                    }
                }
            }
        }
        return Parser.toByteArray(classNode, true);
    }

}
