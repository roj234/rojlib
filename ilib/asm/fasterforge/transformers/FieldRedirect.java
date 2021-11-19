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

import ilib.api.ContextClassTransformer;
import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldSimple;
import roj.asm.tree.MethodSimple;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.util.Context;

import java.util.List;

public class FieldRedirect implements ContextClassTransformer {
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

    @Override
    public void transform(String transformedName, Context context) {
        if (!this.clsName.equals(transformedName)) return;

        ConstantData data = context.getData();

        String fieldRef = null;

        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple f = fields.get(i);
            if (this.fieldType.equals(f.type.getString()) && fieldRef == null) {
                fieldRef = f.name();
                continue;
            }
            if (this.fieldType.equals(f.type.getString()))
                throw new RuntimeException("Error processing " + this.clsName + " - found a duplicate holder field");
        }
        if (fieldRef == null)
            throw new RuntimeException("Error processing " + this.clsName + " - no holder field declared (is the code somehow obfuscated?)");

        MethodSimple getMethod = null;
        for (MethodSimple m : data.methods) {
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

        for (MethodSimple method : data.methods) {
            if (method.name.getString().equals(this.bypass))
                continue;

            AttrCode code;
            Attribute attr = (Attribute) method.attributes.getByName("Code");
            if (attr != null) {
                if (attr instanceof AttrCode) {
                    code = (AttrCode) attr;
                } else {
                    method.attributes.putByName(code = new AttrCode(method, attr.getRawData(), data.cp));
                }
            } else {
                continue;
            }

            for (int i = 0; i < code.instructions.size(); i++) {
                InsnNode node = code.instructions.get(i);
                if (node.getOpcode() == Opcodes.GETFIELD) {
                    FieldInsnNode fi = (FieldInsnNode) node;
                    // GETFIELD
                    if (fieldRef.equals(fi.name)) {
                        InvokeInsnNode replace = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL);
                        replace.owner = data.name;
                        replace.name = getMethod.name.getString();
                        replace.setParameters(getMethod.type.getString());

                        code.instructions.set(i, replace);
                    }
                }
            }
        }
    }
}
