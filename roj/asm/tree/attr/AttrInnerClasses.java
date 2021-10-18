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

package roj.asm.tree.attr;

import roj.asm.cst.CstClass;
import roj.asm.cst.CstUTF;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PrimitiveIterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class AttrInnerClasses extends Attribute {
    public static final String NAME = "InnerClasses";

    public AttrInnerClasses() {
        super(NAME);
        classes = new ArrayList<>();
    }

    public AttrInnerClasses(ByteReader r, ConstantPool pool) {
        super(NAME);
        classes = parse(r, pool);
    }

    public List<InnerClass> parse(ByteReader r, ConstantPool pool) {
        //** If a class file has a version number that is 51.0 or above, outer_class_info_index must be 0 if inner_name_index is 0.
        final int count = r.readUnsignedShort();

        List<InnerClass> classes = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String selfName = ((CstClass) pool.get(r)).getValue().getString();
            CstClass outer = (CstClass) pool.get(r);
            // If C is not a member of a class or an interface (that is, if C is a top-level class or interface (JLS §7.6) or a local class (JLS §14.3) or an anonymous class (JLS §15.9.5)), the value of the outer_class_info_index item must be 0.
            String outerName = outer == null ? null : outer.getValue().getString();

            CstUTF nameS = (CstUTF) pool.get(r);
            // If C is anonymous (JLS §15.9.5), the item must be null
            // Otherwise, the item must be a Utf8
            String name = nameS == null ? null : nameS.getString();

            classes.add(new InnerClass(selfName, outerName, name, r.readChar()));
        }

        return classes;
    }

    public final List<InnerClass> classes;

    @Override
    protected void toByteArray1(ConstantPool pool, ByteWriter w) {
        w.writeShort(classes.size());
        for (int i = 0; i < classes.size(); i++) {
            InnerClass clazz = classes.get(i);
            w.writeShort(pool.getClassId(clazz.self)).writeShort(clazz.parent == null ? (short) 0 :
                    pool.getClassId(clazz.parent)).writeShort(clazz.name == null ? (short) 0 :
                    pool.getUtfId(clazz.name)).writeShort(clazz.flags);
        }
    }

    public String toString() {
        if (classes.isEmpty())
            return "InnerClasses: []";
        StringBuilder sb = new StringBuilder("InnerClasses: \n");
        for (InnerClass clazz : classes) {
            sb.append("         ").append(clazz.toString()).append('\n');
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    public static class InnerClass {
        public String self, parent, name;
        public char flags;

        public InnerClass(String self, String parent, String name, char flags) {
            this.self = self;
            this.parent = parent;
            this.name = name;
            this.flags = flags;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (PrimitiveIterator.OfInt itr = AccessFlag.of(flags).iterator(); itr.hasNext(); ) {
                sb.append(AccessFlag.byIdInnerClass(itr.nextInt())).append(' ');
            }
            sb.append("class ");
            if (parent == null && name == null) {
                sb.append("<anonymous>");
            } else {
                sb.append(parent.substring(parent.lastIndexOf('/') + 1)).append(/*'$'*/'.').append(name);
            }

            if (name == null || name.indexOf('$') != -1) {
                sb.append(' ').append("(Path: ").append(self).append(')');
            }

            return sb.append(';').toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            InnerClass that = (InnerClass) o;

            if (!self.equals(that.self)) return false;
            if (!Objects.equals(parent, that.parent)) return false;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            int result = self.hashCode();
            result = 31 * result + (parent != null ? parent.hashCode() : 0);
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }
    }
}