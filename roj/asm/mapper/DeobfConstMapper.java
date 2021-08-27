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
package roj.asm.mapper;

import roj.asm.Parser;
import roj.asm.cst.CstRef;
import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.MtDesc;
import roj.asm.tree.AccessData;
import roj.asm.tree.ConstantData;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.CharMap;
import roj.collect.FindSet;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.text.CharList;
import roj.ui.CmdUtil;
import roj.util.Helpers;

import java.io.File;
import java.util.*;

/**
 * 不要用cache和map的read，会崩溃，我也懒得做支持...
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/19 22:13
 */
public final class DeobfConstMapper extends ConstMapper {
    public MyHashMap<MtDesc, String> getFieldMap1() {
        return Helpers.cast(fieldMap);
    }

    public DeobfConstMapper() {}

    /**
     * @inheritDoc
     */
    final void mapSelfField(Context ctx, List<FieldSimple> fields, ConstantData data, Collection<String> supers) {
        if(SKIP_BIN_FIELDS)
            if(classMap.containsKey(data.name)) return;

        MtDesc sp = Util.shareMD();

        FindSet<MtDesc> sf = Helpers.cast(selfSkipFields);
        out:
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);
            if (field.accesses.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                sf.add(new MtDesc(data.name, field.name.getString(), field.type.getString()));
                continue;
            }

            sp.name = field.name.getString();
            sp.param = field.type.getString();

            // field只能覆盖...
            for (String parent : supers) {
                sp.owner = parent;
                if (sf.contains(sp))
                    break out;

                Map.Entry<MtDesc, String> entry = Helpers.cast(fieldMap.find(Helpers.cast(sp)));
                if (entry != null) {
                    // 还是时间与空间的问题
                    sf.add(new MtDesc(data.name, field.name.getString(), field.type.getString()));
                    break out;
                }
            }
        }
    }

    /**
     * @inheritDoc
     */
    final void mapField(Context ctx, ConstantData data, CstRef ref) {
        MtDesc fd = Util.shareMD().read(ref);

        Collection<String> parents = resolveParentShared(ref.getClassName());
        for(String parent : parents) {
            fd.owner = parent;

            if(selfSkipFields.contains(fd)) {
                if(DEBUG)
                    System.out.println("[3F-" + data.name + "-NT]: " + (!fd.owner.equals(data.name) ? fd.owner + '.' : "") + fd.name);
                return;
            }

            Map.Entry<MtDesc, String> entry = Helpers.cast(fieldMap.find(Helpers.cast(fd)));
            if(entry != null) {
                if(DEBUG)
                    System.out.println("[3F-" + data.name + "]: " + (!fd.owner.equals(data.name) ? fd.owner + '.' : "") + fd.name + " => " + entry.getValue());
                setRefName(data, ref, entry.getValue());
                return;
            }
        }
    }

    /**
     * @inheritDoc
     */
    final void generateSuperMap(List<File> files) {
        libSupers.clear();

        libSkipFields.clear();
        libSkipMethods.clear();
        if(files.isEmpty())
            return;

        Set<String> noMFClasses = new MyHashSet<>();
        Set<AccessData> libClasses = new MyHashSet<>();
        CharMap<FlagList> flagCache = new CharMap<>();

        ZipUtil.ICallback cb = (fileName, s) -> {
            byte[] bytes = IOUtil.readFully(s);
            if(bytes.length < 32)
                return;

            AccessData data;
            try {
                data = Parser.parseAccessDirect(bytes);
            } catch (Throwable e) {
                CmdUtil.warning("Class " + fileName + " is unable to read", e);
                return;
            }

            ArrayList<String> list = new ArrayList<>();
            if(!"java/lang/Object".equals(data.superName)) {
                list.add(data.superName);
            }
            List<String> itf = data.itf;
            for (int i = 0; i < itf.size(); i++) {
                String name = itf.get(i);
                if (name.endsWith("_NMR$FAKEIMPL")) {
                    int ss = name.lastIndexOf('/') + 1;
                    int e = name.length() - "_NMR$FAKEIMPL".length();
                    name = new CharList(e - ss + 1).append(name, ss, e - ss).replace('_', '/').toString();
                    if(DEBUG)
                        System.out.println("[NMR继承-Lib] " + data.name + " <= " + name);
                }
                list.add(name);
            }

            // 构建lib一极继承表
            if(!list.isEmpty())
                libSupers.put(data.name, list);


            List<AccessData.MOF> ent = data.methods;
            MtDesc m = new MtDesc(data.name, "", "");
            for (int i = 0; i < ent.size(); i++) {
                AccessData.MOF method = ent.get(i);
                if(method.name.startsWith("<") || (method.acc & (AccessFlag.STATIC | AccessFlag.PRIVATE | AccessFlag.FINAL)) != 0) {
                    continue;
                }
                m.name = method.name;
                m.param = method.desc;
                m.flags = flagCache.computeIfAbsent((char) method.acc, ConstMapper.fl);
                libSkipMethods.add(m);
                m = new MtDesc(data.name, "", "");
            }

            ent = data.fields;
            for (int i = 0; i < ent.size(); i++) {
                AccessData.MOF field = ent.get(i);
                if((field.acc & AccessFlag.PRIVATE) != 0) {
                    continue;
                }
                m.name = field.name;
                m.param = field.desc;
                m.flags = flagCache.computeIfAbsent((char) field.acc, ConstMapper.fl);
                libSkipFields.add(Helpers.cast(m));
                m = new MtDesc(data.name, "", "");
            }
        };

        for (int i = 0; i < files.size(); i++) {
            File fi = files.get(i);
            String f = fi.getName();
            if (!f.startsWith("[noread]") && (f.endsWith(".zip") || f.endsWith(".jar")))
                ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
        }

        makeInheritMap(libSupers, false);
    }
}