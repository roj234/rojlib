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

import roj.asm.OpcodeUtil;
import roj.asm.cst.CstRefField;
import roj.asm.tree.Clazz;
import roj.asm.tree.Field;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;

import static roj.asm.Opcodes.*;

/**
 * getstatic putstatic getfield putfield
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class FieldInsnNode extends InsnNode implements IClassInsnNode {
    public FieldInsnNode(byte code) {
        super(code);
    }

    public FieldInsnNode(byte code, String owner, String name, Type type) {
        super(code);

        this.owner = owner;
        this.name = name;
        this.type = type;
    }

    public FieldInsnNode(byte code, String owner, String name, String type) {
        super(code);

        if (!Type.isValid(type.charAt(0))) throw new IllegalArgumentException("别把class name当type!");

        this.owner = owner;
        this.name = name;
        this.rawType = type;
    }

    // net/xxx/abc.DEF:LXXXX javap
    public FieldInsnNode(byte code, String desc) {
        super(code);
        int cIdx = desc.indexOf(".");

        this.owner = desc.substring(0, cIdx);

        int nIdx = desc.indexOf(":", cIdx + 1);
        String name = desc.substring(cIdx + 1, nIdx);
        if (name.charAt(0) == '"') {
            name = name.substring(1, name.length() - 1);
        }
        this.name = name;

        this.rawType = desc.substring(nIdx + 1);
    }

    public FieldInsnNode(byte code, CstRefField ref) {
        super(code);
        this.owner = ref.getClassName();
        this.name = ref.desc().getName().getString();
        this.rawType = ref.desc().getType().getString();
    }

    public String owner, name, rawType;
    private Type type;

    public FieldInsnNode(byte code, Clazz clazz, int index) {
        super(code);
        Field field = clazz.fields.get(index);
        this.owner = clazz.name;
        this.name = field.name;
        this.type = field.type;
    }

    public Type getType() {
        if (type == null) type = ParamHelper.parseField(rawType);
        return type;
    }

    public void setType(Type type) {
        rawType = null;
        this.type = type;
    }

    @Override
    public int nodeType() {
        return T_FIELD;
    }

    @Override
    public void owner(String clazz) {
        // noinspection all
        this.owner = clazz.toString();
    }

    public String owner() {
        return owner;
    }

    public void toByteArray(ConstantPool cw, ByteList w) {
        if (type != null) rawType = ParamHelper.getField(type);
        w.put(code).putShort(cw.getFieldRefId(owner, name, rawType));
    }

    @Override
    public int nodeSize() {
        return 3;
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case GETFIELD:
            case GETSTATIC:
            case PUTFIELD:
            case PUTSTATIC:
                return true;
        }
        return false;
    }

    public String toString() {
        return OpcodeUtil.toString0(code, rawType, owner.substring(owner.lastIndexOf('/') + 1), name);
    }
}