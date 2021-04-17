/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrStringList.java
 */
package roj.asm.struct.attr;

import roj.asm.constant.CstUTF;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

public final class AttrStringList extends Attribute {
    public static final String EXCEPTIONS = "Exceptions";
    public static final String NEST_MEMBERS = "NestMembers";

    public final byte isMethod;

    public AttrStringList(String name, int isMethod) {
        super(name);
        classes = new ArrayList<>();
        this.isMethod = (byte) isMethod;
    }

    /**
     * @param isMethod true: {@link roj.asm.struct.Method} false: {@link AttrCode}
     */
    public AttrStringList(String name, ByteReader r, ConstantPool pool, int isMethod) {
        super(name);
        this.isMethod = (byte) isMethod;

        int len = r.readUnsignedShort();
        classes = new ArrayList<>(len);

        int i = 0;
        switch (isMethod) {
            case 0:
                for (; i < len; i++) {
                    classes.add(pool.getName(r));
                }
            case 1:
                for (; i < len; i++) {
                    classes.add(((CstUTF) pool.get(r)).getString());
                }
                break;
        }
    }

    public final List<String> classes;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        final List<String> ex = this.classes;

        w.writeShort(ex.size());
        int i = 0;
        switch (isMethod) {
            case 0:
                for (; i < ex.size(); i++) {
                    w.writeShort(pool.getClassId(ex.get(i)));
                }
                break;
            case 1:
                for (; i < ex.size(); i++) {
                    w.writeShort(pool.getUtfId(ex.get(i)));
                }
                break;
        }
    }

    public String toString() {
        return name + ": " + classes;
    }
}