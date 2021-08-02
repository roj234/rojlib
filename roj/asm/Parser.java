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

package roj.asm;

import roj.asm.cst.CstClass;
import roj.asm.cst.CstUTF;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.asm.util.FlagList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Roj ASM parser (字节码解析器) <br>
 * 基于Java 8 构造 <br>
 *     现已支持 Java 15 (需要测试)
 *
 * @author Roj234
 * @version 2.0
 * @since 2021/5/29 17:16
 */
public final class Parser {
    // region CLAZZ parse LOD 2

    public static Clazz parse(byte[] buf) {
        return parse(new ByteList(buf));
    }

    @SuppressWarnings("fallthrough")
    public static Clazz parse(ByteList buf) {
        if (buf == null) throw new NullPointerException("Bytecode is null!");

        Clazz result = new Clazz();
        ByteReader r = new ByteReader(buf);

        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }

        result.version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);
        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        try {
            pool.read(r);
            pool.valid();
        } catch (Exception e) {
            throw new RuntimeException("Corrupted constant pool: ", e);
        }

        /**
         * If the ACC_MODULE flag is set in the access_flags item, then no other flag in the access_flags item may be
         * set, and the following rules apply to the rest of the ClassFile structure:
         *
         * major_version, minor_version: ≥ 53.0 (i.e., Java SE 9 and above)
         *
         * this_class: module-info
         *
         * super_class, interfaces_count, fields_count, methods_count: zero
         */
        result.accesses = AccessFlag.of(r.readShort());
        try {
            result.name = pool.getName(r);
        } catch (ArrayIndexOutOfBoundsException e) {
            result.name = "ERROR: Unknown cpi";
        }
        boolean module = result.accesses.hasAll(AccessFlag.MODULE);
        if(module && result.accesses.flag != AccessFlag.MODULE)
            throw new IllegalArgumentException("Module should only have 'module' flag");

        result.parent = pool.getName(r);
        if (result.parent == null && (!"java/lang/Object".equals(result.name) || module)) {
            throw new IllegalArgumentException("No father found");
        }

        int len = r.readUnsignedShort();
        List<String> interfaces = result.interfaces;
        if(module && len != 0)
            throw new IllegalArgumentException("Module should not have interfaces");
        for (int i = 0; i < len; i++) {
            interfaces.add(pool.getName(r));
        }

        len = r.readUnsignedShort();
        List<Field> fields = result.fields;
        if(module && len != 0)
            throw new IllegalArgumentException("Module should not have fields");
        for (int i = 0; i < len; i++) {
            FlagList access = AccessFlag.of(r.readShort());
            CstUTF name = (CstUTF) pool.get(r);
            CstUTF desc = (CstUTF) pool.get(r);

            Field field = new Field(access, name.getString(), desc.getString());
            field.initAttributes(pool, r);
            fields.add(field);
        }

        len = r.readUnsignedShort();
        if(module && len != 0)
            throw new IllegalArgumentException("Module should not have methods");
        List<Method> methods = result.methods;
        for (int i = 0; i < len; i++) {
            FlagList access = AccessFlag.of(r.readShort());
            CstUTF name = (CstUTF) pool.get(r);
            CstUTF desc = (CstUTF) pool.get(r);

            Method method = new Method(access, result, name.getString(), desc.getString());
            method.initAttributes(pool, r);
            methods.add(method);
        }

        result.initAttributes(pool, r);

        return result;
    }

    public static byte[] toByteArray(Clazz c) {
        return SharedCache.store(c).getByteArray();
    }

    public static ByteList toByteArrayShared(Clazz c) {
        return SharedCache.store(c);
    }

    // endregion
    // region CONSTANT DATA parse LOD 1

    public static ConstantData parseConstants(byte[] buf) {
        return parseConst0(new ByteReader(buf));
    }

    public static ConstantData parseConstants(ByteList buf) {
        return parseConst0(new ByteReader(buf));
    }

    @Nonnull
    public static ConstantData parseConst0(ByteReader r) {
        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }
        int version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);

        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        pool.read(r);
        pool.valid();

        ConstantData result = new ConstantData(version, pool, r.length(), r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort());

        int len = r.readUnsignedShort();

        List<CstClass> itf = result.interfaces;
        for (int i = 0; i < len; i++) {
            itf.add((CstClass) pool.get(r));
        }

        len = r.readUnsignedShort();
        List<FieldSimple> fields = result.fields;
        for (int i = 0; i < len; i++) {
            FieldSimple field = new FieldSimple(r.readShort(), (CstUTF) pool.get(r), (CstUTF) pool.get(r));

            AttributeList attributes = field.attributes;
            int attrLen = r.readUnsignedShort();
            attributes.ensureCapacity(attrLen);

            for (int j = 0; j < attrLen; j++) {
                String name0 = ((CstUTF) pool.get(r)).getString();

                Attribute attr = new AttrUnknown(name0, r.readBytesDelegated(r.readInt()));
                attributes.add(attr);
            }
            fields.add(field);
        }

        len = r.readUnsignedShort();
        List<MethodSimple> methods = result.methods;
        for (int i = 0; i < len; i++) {
            MethodSimple method = new MethodSimple(r.readShort(), (CstUTF) pool.get(r), (CstUTF) pool.get(r));
            method.cn(result.name, result.parent);

            AttributeList attributes = method.attributes;
            int attrLen = r.readUnsignedShort();
            attributes.ensureCapacity(attrLen);

            for (int j = 0; j < attrLen; j++) {
                String name0 = ((CstUTF) pool.get(r)).getString();

                Attribute attr = new AttrUnknown(name0, r.readBytesDelegated(r.readInt()));
                attributes.add(attr);
            }
            methods.add(method);
        }

        len = r.readUnsignedShort();
        AttributeList attributes = result.attributes;
        attributes.ensureCapacity(len);

        for (int i = 0; i < len; i++) {
            String name0 = ((CstUTF) pool.get(r)).getString();

            Attribute attr = new AttrUnknown(name0, r.readBytesDelegated(r.readInt()));

            attributes.add(attr);
        }

        return result;
    }

    public static byte[] toByteArray(ConstantData c) {
        return SharedCache.store(c).getByteArray();
    }

    public static ByteList toByteArrayShared(ConstantData c) {
        return SharedCache.store(c);
    }

    // endregion
    // region ACCESS parse LOD 0

    public static AccessData parseAccess(byte[] buf) {
        return parseAcc0(buf.clone(), new ByteReader(buf));
    }

    public static AccessData parseAccessDirect(byte[] buf) {
        return parseAcc0(buf, new ByteReader(buf));
    }

    @Nonnull
    public static AccessData parseAcc0(byte[] buf, ByteReader r) {
        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }
        r.index += 4; // ver

        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        pool.readNames(r);

        int cfo = r.index; // acc

        r.index += 2;

        String self = pool.getName(r);
        String parent = pool.getName(r);

        int len = r.readUnsignedShort();  // itf
        List<String> itf = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            itf.add(pool.getName(r));
        }

        List<?>[] arr = new List<?>[2];
        for (int k = 0; k < 2; k++) {
            len = r.readUnsignedShort();
            List<AccessData.MOF> com = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                int offset = r.index;

                short acc = r.readShort();

                AccessData.MOF d = new AccessData.MOF(((CstUTF) pool.get(r)).getString(),
                                                      ((CstUTF) pool.get(r)).getString(), offset);
                d.acc = acc;
                com.add(d);

                int attrs = r.readUnsignedShort();
                for (int j = 0; j < attrs; j++) {
                    r.index += 2;
                    int ol = r.readInt();
                    r.index += ol;
                }
            }
            arr[k] = com;
        }

        return new AccessData(buf, Helpers.cast(arr[0]), Helpers.cast(arr[1]), cfo, self, parent, itf);
    }

    // endregion
    // SIMPLE parse LOD -1

    public static List<String> simpleData(byte[] buf) {
        if (buf == null)
            return null;
        return simpleData(new ByteList(buf));
    }

    public static List<String> simpleData(ByteList buf) {
        if (buf == null)
            return null;

        ByteReader r = new ByteReader(buf);
        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }

        r.index += 4; // ver

        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        pool.readNames(r);

        int cfo = r.index; // acc

        r.index += 2;

        List<String> list = new ArrayList<>();

        list.add(pool.getName(r));
        list.add(pool.getName(r));

        int len = r.readUnsignedShort();  // itf
        for (int i = 0; i < len; i++) {
            list.add(pool.getName(r));
        }

        return list;
    }

    // endregion
    // region MISC

    /**
     * Used to create AttrCode
     *
     * @param clazz  class
     * @param method method
     * @return code or null
     */
    @Nullable
    public static AttrCode getOrCreateCode(@Nonnull ConstantData clazz, @Nonnull MethodSimple method) {
        Attribute attribute = method.attrByName("Code");

        if (attribute == null) {
            if (!method.accesses.hasAll(AccessFlag.ABSTRACT)) {
                throw new IllegalArgumentException("Non-abstract method " + clazz.name + '.' + method.name.getString() + ':' + method.type.getString() + " did not contains a Code attribute.");
            }
            return null;
        }

        AttrCode code;
        if (!(attribute instanceof AttrCode)) {
            int index = method.attributes.indexOf(attribute);
            method.attributes.set(index, code = new AttrCode(method, attribute.getRawData(), clazz.cp));
        } else {
            code = (AttrCode) attribute;
        }
        return code;
    }
    // endregion
}