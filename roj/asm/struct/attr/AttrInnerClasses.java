/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttributeInnerClass.java
 */
package roj.asm.struct.attr;

import roj.asm.constant.CstClass;
import roj.asm.constant.CstUTF;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PrimitiveIterator;

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
            // If C is anonymous (JLS §15.9.5), the value of the inner_name_index item must be 0.
            String name = nameS == null ? null : nameS.getString();

            classes.add(new InnerClass(selfName, outerName, name, r.readShort()));
        }

        return classes;
    }

    public final List<InnerClass> classes;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(classes.size());
        for (InnerClass clazz : classes) {
            w.writeShort(pool.getClassId(clazz.self))
                    .writeShort(clazz.parent == null ? (short) 0 : pool.getClassId(clazz.parent))
                    .writeShort(clazz.name == null ? (short) 0 : pool.getUtfId(clazz.name))
                    .writeShort(clazz.flags);
        }
    }

    public String toString() {
        if (classes.isEmpty())
            return "InnerClasses: null";
        StringBuilder sb = new StringBuilder("InnerClasses: \n");
        for (InnerClass clazz : classes) {
            sb.append("         ").append(clazz.toString()).append('\n');
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    public static class InnerClass {
        public String self, parent, name;
        public short flags;

        public InnerClass(String self, String parent, String name, short flags) {
            this.self = self;
            this.parent = parent;
            this.name = name;
            this.flags = flags;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (PrimitiveIterator.OfInt itr = AccessFlag.parse(flags).iterator(); itr.hasNext(); ) {
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