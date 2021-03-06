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
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.MoFNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
import roj.asm.util.InsnList;

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
    public void transform(String trName, Context ctx) {
        if (!clsName.equals(trName)) return;

        ConstantData data = ctx.getData();

        String fieldRef = null;

        List<? extends FieldNode> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            MoFNode f = fields.get(i);
            if (fieldType.equals(f.rawDesc()) && fieldRef == null) {
                fieldRef = f.name();
                continue;
            }
            if (fieldType.equals(f.rawDesc()))
                throw new RuntimeException("Error processing " + clsName + " - found a duplicate holder field");
        }
        if (fieldRef == null)
            throw new RuntimeException("Error processing " + clsName + " - no holder field declared (is the code somehow obfuscated?)");

        MethodNode getMethod = null;

        List<? extends MethodNode> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodNode m = methods.get(i);
            if (m.name().equals(bypass)) continue;
            if (methodDesc.equals(m.rawDesc()) && getMethod == null) {
                getMethod = m;
                continue;
            }
            if (methodDesc.equals(m.rawDesc())) throw new RuntimeException("Error processing " + clsName + " - duplicate get method found");
        }
        if (getMethod == null)
            throw new RuntimeException("Error processing " + clsName + " - no get method found (is the code somehow obfuscated?)");

        for (int j = 0; j < methods.size(); j++) {
            MethodNode method = methods.get(j);
            if (method.name().equals(bypass)) continue;

            AttrCode code = AttrHelper.getOrCreateCode(data.cp, method);
            if (code == null) continue;

            InsnList insn = code.instructions;
            for (int i = 0; i < insn.size(); i++) {
                InsnNode node = insn.get(i);
                if (node.getOpcode() == Opcodes.GETFIELD) {
                    FieldInsnNode fi = (FieldInsnNode) node;
                    // GETFIELD
                    if (fieldRef.equals(fi.name)) {
                        InvokeInsnNode replace = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL);
                        replace.owner = data.name;
                        replace.name = getMethod.name();
                        replace.rawDesc(getMethod.rawDesc());

                        insn.set(i, replace);
                    }
                }
            }
        }
    }
}
