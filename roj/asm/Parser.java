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

import roj.asm.cst.Constant;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstUTF;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantNamePool;
import roj.asm.util.ConstantPool;
import roj.collect.CharMap;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.Helpers;

import javax.annotation.Nonnull;
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
    public static final int CTYPE_LOD_1   = 0;
    public static final int CTYPE_LOD_2   = 1;
    public static final int CTYPE_LOD_3   = 2;
    public static final int CTYPE_REFLECT = 3;

    public static final int FTYPE_SIMPLE  = 0;
    public static final int FTYPE_FULL    = 1;
    public static final int FTYPE_REFLECT = 2;

    public static final int MTYPE_SIMPLE  = 3;
    public static final int MTYPE_FULL    = 4;
    public static final int MTYPE_REFLECT = 5;
    public static final int MFTYPE_LOD1   = 6;

    // region CLAZZ parse LOD 2

    public static Clazz parse(byte[] buf) {
        return parse(new ByteList(buf));
    }

    @SuppressWarnings("fallthrough")
    public static Clazz parse(ByteList buf) {
        if (buf == null) throw new NullPointerException("Bytecode is null!");

        Clazz result = new Clazz();
        ByteReader r = SharedBuf.reader(buf);

        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }

        result.version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);
        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        try {
            pool.read(r);
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
        result.accesses = r.readChar();
        try {
            result.name = pool.getName(r);
        } catch (ArrayIndexOutOfBoundsException e) {
            result.name = "ERROR: Unknown cpi";
        }
        boolean module = (result.accesses & AccessFlag.MODULE) != 0;
        if(module && result.accesses != AccessFlag.MODULE)
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
            int access = r.readUnsignedShort();
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
            int access = r.readUnsignedShort();
            CstUTF name = (CstUTF) pool.get(r);
            CstUTF desc = (CstUTF) pool.get(r);

            Method method = new Method(access, result, name.getString(), desc.getString());
            method.initAttributes(pool, r);
            methods.add(method);
        }

        result.initAttributes(pool, r);

        return result;
    }

    public static byte[] toByteArray(IClass c) {
        return SharedBuf.store(c).toByteArray();
    }

    public static ByteList toByteArrayShared(IClass c) {
        return SharedBuf.store(c);
    }

    // endregion
    // region CONSTANT DATA parse LOD 1

    public static ConstantData parseConstants(byte[] buf) {
        return parseConstants(new ByteList(buf));
    }

    @Nonnull
    public static ConstantData parseConstants(ByteList buf) {
        ByteReader r = SharedBuf.reader(buf);

        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }
        int version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);

        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        pool.read(r);

        ConstantData result = new ConstantData(version, pool, r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort());

        int len = r.readUnsignedShort();

        List<CstClass> itf = result.interfaces;
        for (int i = 0; i < len; i++) {
            itf.add((CstClass) pool.get(r));
        }

        len = r.readUnsignedShort();
        List<FieldSimple> fields = Helpers.cast(result.fields);
        for (int i = 0; i < len; i++) {
            FieldSimple field = new FieldSimple(r.readShort(), (CstUTF) pool.get(r), (CstUTF) pool.get(r));
            fields.add(field);

            int attrLen = r.readUnsignedShort();
            if (attrLen == 0) continue;
            AttributeList attributes = field.attributes();
            attributes.ensureCapacity(attrLen);

            while (attrLen-- > 0) {
                String name0 = ((CstUTF) pool.get(r)).getString();
                attributes.add(new AttrUnknown(name0, r.slice(r.readInt())));
            }
        }

        len = r.readUnsignedShort();
        List<MethodSimple> methods = Helpers.cast(result.methods);
        for (int i = 0; i < len; i++) {
            MethodSimple method = new MethodSimple(r.readShort(), (CstUTF) pool.get(r), (CstUTF) pool.get(r));
            method.cn(result.name);
            methods.add(method);

            int attrLen = r.readUnsignedShort();
            if (attrLen == 0) continue;
            AttributeList attributes = method.attributes();
            attributes.ensureCapacity(attrLen);

            while (attrLen-- > 0) {
                String name0 = ((CstUTF) pool.get(r)).getString();
                attributes.add(new AttrUnknown(name0, r.slice(r.readInt())));
            }
        }

        len = r.readUnsignedShort();
        if (len > 0) {
            AttributeList attrs = result.attributes();
            attrs.ensureCapacity(len);

            for (int i = 0; i < len; i++) {
                String name0 = ((CstUTF) pool.get(r)).getString();
                Attribute attr = new AttrUnknown(name0, r.slice(r.readInt()));
                attrs.add(attr);
            }
        }

        return result;
    }

    // endregion
    // region ACCESS parse LOD 0

    public static AccessData parseAccess(byte[] buf) {
        return parseAcc0(buf.clone(), new ByteList(buf));
    }

    public static AccessData parseAccessDirect(byte[] buf) {
        return parseAcc0(buf, new ByteList(buf));
    }

    @Nonnull
    public static AccessData parseAcc0(byte[] dst, ByteList src) {
        ByteReader r = SharedBuf.reader(src);

        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }
        r.rIndex += 4; // ver

        ConstantNamePool pool = SharedBuf.alloc().newConstNamePool(r.readUnsignedShort());
        pool.skip(r);
        CharMap<Constant> map = pool.map;
        r.rIndex += 2;
        map.put(r.readChar(), null);
        map.put(r.readChar(), null);

        int len = r.readUnsignedShort();
        for (int i = 0; i < len; i++) {
            map.put(r.readChar(), null);
        }

        for (int k = 0; k < 2; k++) {
            len = r.readUnsignedShort();
            for (int i = 0; i < len; i++) {
                r.rIndex += 2;
                map.put(r.readChar(), null);
                map.put(r.readChar(), null);

                int attrs = r.readUnsignedShort();
                for (int j = 0; j < attrs; j++) {
                    r.rIndex += 2;
                    int ol = r.readInt();
                    r.rIndex += ol;
                }
            }
        }

        pool.init(r);

        int cfo = r.rIndex; // acc

        r.rIndex += 2;

        String self = pool.getName(r);
        String parent = pool.getName(r);

        len = r.readUnsignedShort();  // itf
        List<String> itf = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            itf.add(pool.getName(r));
        }

        List<?>[] arr = new List<?>[2];
        for (int k = 0; k < 2; k++) {
            len = r.readUnsignedShort();
            List<AccessData.MOF> com = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                int offset = r.rIndex;

                char acc = r.readChar();

                AccessData.MOF d = new AccessData.MOF(((CstUTF) pool.get(r)).getString(),
                                                      ((CstUTF) pool.get(r)).getString(), offset);
                d.acc = acc;
                com.add(d);

                int attrs = r.readUnsignedShort();
                for (int j = 0; j < attrs; j++) {
                    r.rIndex += 2;
                    int ol = r.readInt();
                    r.rIndex += ol;
                }
            }
            arr[k] = com;
        }

        return new AccessData(dst, Helpers.cast(arr[0]), Helpers.cast(arr[1]), cfo, self, parent, itf);
    }

    // endregion
    // region SIMPLE parse LOD -1

    public static List<String> simpleData(byte[] buf) {
        return simpleData(new ByteList(buf));
    }

    public static List<String> simpleData(ByteList buf) {
        ByteReader r = SharedBuf.reader(buf);
        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }

        r.rIndex += 4; // ver

        ConstantNamePool pool = SharedBuf.alloc().newConstNamePool(r.readUnsignedShort());
        pool.skip(r);
        CharMap<Constant> map = pool.map;
        r.rIndex += 2;
        map.put(r.readChar(), null);
        map.put(r.readChar(), null);

        int len = r.readUnsignedShort();
        for (int i = 0; i < len; i++) {
            map.put(r.readChar(), null);
        }

        pool.init(r);

        r.rIndex += 2;

        List<String> list = new ArrayList<>();

        list.add(pool.getName(r));
        list.add(pool.getName(r));

        len = r.readUnsignedShort();
        for (int i = 0; i < len; i++) {
            list.add(pool.getName(r));
        }

        return list;
    }

    // endregion
    public static ByteReader reader(Attribute attr) {
        return SharedBuf.reader(attr.getRawData());
    }
}