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

package roj.asm.tree;

import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.FileOutputStream;
import java.util.List;

import static roj.asm.util.AccessFlag.*;

/**
 * Class LOD 2
 *
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public final class ConstantData implements IClass {
    public int version;

    public char accesses;

    public final CstClass nameCst, parentCst;

    public final String name, parent;

    public ConstantPool cp;

    public List<? extends MethodNode> methods = new SimpleList<>();
    public List<? extends FieldNode> fields = new SimpleList<>();
    public List<CstClass> interfaces = new SimpleList<>();
    AttributeList attributes = new AttributeList();

    public void verify() {
        int permDesc = 0;
        int typeDesc = 0;
        int fn = -1;

        for (int i = 0; i < 16; i++) {
            int v = 1 << i;
            if ((accesses & v) != 0) {
                switch (v) {
                    case PUBLIC:
                    case PROTECTED:
                        permDesc++;
                        break;
                    case INTERFACE:
                    case ENUM:
                        typeDesc++;
                        break;
                    case FINAL:
                        if (fn == 0) {
                            throw new IllegalArgumentException("Final and Abstract");
                        }
                        fn = 1;
                        break;
                    case ABSTRACT:
                        if (fn == 1) {
                            throw new IllegalArgumentException("Final and Abstract");
                        }
                        fn = 0;
                        break;
                    case ANNOTATION:
                    case SUPER_OR_SYNC:
                    case SYNTHETIC:
                    case STRICTFP:
                    case VOLATILE_OR_BRIDGE:
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported access flag " + accesses);
                }
            }
        }

        int v = accesses & (ANNOTATION | INTERFACE);
        if(v == ANNOTATION)
            throw new IllegalArgumentException("Not valid @interface " + accesses);

        if (permDesc > 1) {
            throw new IllegalArgumentException("ACCPermission too much " + accesses);
        }

        if (typeDesc > 1) {
            throw new IllegalArgumentException("ACCType too much " + accesses);
        }

        MyHashSet<String> descs = new MyHashSet<>();
        for (int j = 0; j < methods.size(); j++) {
            MoFNode method = methods.get(j);
            if (!descs.add(method.name() + '|' + method.rawDesc())) {
                throw new IllegalArgumentException("Duplicate method " + method.name() + method.rawDesc());
            }
        }
        descs.clear();

        for (int j = 0; j < fields.size(); j++) {
            MoFNode field = fields.get(j);
            if (!descs.add(field.name() + '|' + field.rawDesc())) {
                throw new IllegalArgumentException("Duplicate field " + field.name() + field.rawDesc());
            }
        }
    }

    public ConstantData(int version, ConstantPool cp, int accesses, int nameIndex, int superNameIndex) {
        this.cp = cp;
        this.version = version;
        this.accesses = (char) accesses;
        this.nameCst = ((CstClass) cp.array(nameIndex));
        this.name = nameCst.getValue().getString();
        if (superNameIndex == 0) {
            this.parentCst = null;
            this.parent = Helpers.nonnull();
        } else {
            this.parentCst = ((CstClass) cp.array(superNameIndex));
            this.parent = parentCst.getValue().getString();
        }
    }

    public Attribute attrByName(String name) {
        return attributes == null ? null : (Attribute) attributes.getByName(name);
    }

    @Override
    public AttributeList attributes() {
        return attributes == null ? attributes = new AttributeList() : attributes;
    }

    @Override
    public AttributeList attributesNullable() {
        return attributes;
    }

    public ByteList getBytes(ByteList w) {
        ConstantPool cw = this.cp;

        w.putShort(accesses)
         .putShort(cw.reset(nameCst).getIndex())
         .putShort(parentCst == null ? 0 : cw.reset(parentCst).getIndex())
         .putShort(interfaces.size());

        for (int i = 0; i < interfaces.size(); i++) {
            CstClass it = interfaces.get(i);
            w.putShort(cw.reset(it).getIndex());
        }

        w.putShort(fields.size());
        for (int i = 0, l = fields.size(); i < l; i++) {
            fields.get(i).toByteArray(cw, w);
        }

        w.putShort(methods.size());
        for (int i = 0, l = methods.size(); i < l; i++) {
            methods.get(i).toByteArray(cw, w);
        }

        if (attributes == null) {
            w.putShort(0);
        } else {
            w.putShort(attributes.size());
            for (int i = 0, l = attributes.size(); i < l; i++) {
                attributes.get(i).toByteArray(cw, w);
            }
        }

        int pos = w.wIndex();
        byte[] tmp = w.list;
        int cpl = cw.byteLength() + 10;
        if (tmp.length < pos + cpl) {
            tmp = new byte[pos + cpl];
        }
        System.arraycopy(w.list, 0, tmp, cpl, pos);
        w.list = tmp;

        w.wIndex(0);
        cw.write(w.putInt(0xCAFEBABE).putShort(version).putShort(version >> 16));
        assert w.wIndex() == cpl;
        w.wIndex(pos + cpl);

        return w;
    }

    public void normalize() {
        List<? extends MethodNode> methods = this.methods;
        for (int i = 0; i < methods.size(); i++) {
            if (methods.get(i) instanceof Method) {
                methods.set(i, Helpers.cast(((Method) methods.get(i)).i_downgrade(cp)));
            }
        }
        List<? extends MoFNode> fields = this.fields;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i) instanceof Field) {
                fields.set(i, Helpers.cast(((Field) fields.get(i)).i_downgrade(cp)));
            }
        }
    }

    @Override
    public String toString() {
        return "ConstantData{" +
                "name='" + name + '\'' +
                ", extends='" + parent + '\'' +
                ", impls=" + interfaces +
                ", methods=" + methods +
                ", fields=" + fields +
                '}';
    }

    @Override
    public String name() {
        return nameCst.getValue().getString();
    }

    @Override
    public String parent() {
        return parentCst == null ? null : parentCst.getValue().getString();
    }

    @Override
    public void accessFlag(int flag) {
        this.accesses = (char) flag;
    }

    @Override
    public char accessFlag() {
        return accesses;
    }

    @Override
    public List<String> interfaces() {
        SimpleList<Object> list = new SimpleList<>(interfaces);
        for (int i = 0; i < list.size(); i++) {
            list.set(i, ((CstClass)list.get(i)).getValue().getString());
        }
        return Helpers.cast(list);
    }

    @Override
    public List<? extends MoFNode> methods() {
        return methods;
    }

    @Override
    public List<? extends MoFNode> fields() {
        return fields;
    }

    @Override
    public int type() {
        return Parser.CTYPE_LOD_2;
    }

    public void dump() {
        try (FileOutputStream fos = new FileOutputStream(name.replace('/', '.') + ".class")) {
            Parser.toByteArrayShared(this).writeToStream(fos);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public Method getUpgradedMethod(String name) {
        return getUpgradedMethod(name, null);
    }

    public Method getUpgradedMethod(String name, String desc) {
        List<? extends MoFNode> methods = this.methods;
        for (int i = 0; i < methods.size(); i++) {
            MoFNode ms = methods.get(i);
            if (ms.name().equals(name) && (desc == null || ms.rawDesc().equals(desc))) {
                if (ms instanceof Method) return (Method) ms;
                Method m = new Method(this, (MethodSimple) ms);
                methods.set(i, Helpers.cast(m));
                return m;
            }
        }
        return null;
    }
}