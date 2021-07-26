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
import roj.asm.cst.CstRefField;
import roj.asm.tree.Clazz;
import roj.asm.tree.Field;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantWriter;
import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

//getstatic
//putstatic
//getfield
//putfield
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
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

    // net/xxx/abc.DEF:LXXXX javap
    public FieldInsnNode(byte code, String desc) {
        super(code);
        int index = desc.indexOf(".");
        String tmp = desc.substring(index + 1);
        int index2 = tmp.indexOf(":");

        this.owner = desc.substring(0, index);
        this.name = tmp.substring(0, index2);
        if (name.contains("\"")) {
            name = name.substring(1, name.length() - 1);
        }

        this.type = ParamHelper.parseField(tmp.substring(index2 + 1));
    }

    public FieldInsnNode(byte code, CstRefField ref) {
        super(code);
        this.owner = ref.getClassName();
        this.name = ref.desc().getName().getString();
        String s = ref.desc().getType().getString();
        this.type = ParamHelper.parseField(s);
    }

    public String owner, name;
    public Type type;

    public FieldInsnNode(byte code, Clazz clazz, int index) {
        super(code);
        Field field = clazz.fields.get(index);
        this.owner = clazz.name;
        this.name = field.name;
        this.type = field.type;
    }

    @Override
    public void owner(String clazz) {
        this.owner = clazz;
    }

    public String owner() {
        return owner;
    }

    private int fid;

    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        w.writeShort(this.fid);
    }

    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        super.toByteArray(w);
        w.writeShort(this.fid = pool.getFieldRefId(owner, name, ParamHelper.getField(type)));
    }

    @Override
    protected boolean validate() {
        switch (getOpcode()) {
            case GETFIELD:
            case GETSTATIC:
            case PUTFIELD:
            case PUTSTATIC:
                return true;
        }
        return false;
    }

    public String toString() {
        return Opcodes.toString0(code, type, owner.substring(owner.lastIndexOf('/') + 1), name);
    }
}