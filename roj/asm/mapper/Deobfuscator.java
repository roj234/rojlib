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

import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.FlDesc;
import roj.asm.mapper.util.MtDesc;
import roj.asm.tree.ConstantData;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.util.Helpers;

import java.io.File;
import java.util.List;

/**
 * class混淆器
 *
 * @author Roj233
 * @since 2021/7/18 18:33
 */
public abstract class Deobfuscator extends Obfuscator {
    public static final int REMOVE_SYNTHETIC = ADD_SYNTHETIC;

    public Deobfuscator() {
        this.constMapper = new DeobfConstMapper();
    }

    public void reset(List<File> libraries) {
        constMapper.generateSuperMap(libraries);
    }

    protected void prepare(Context c) {
        ConstantData data = c.getData();

        String dest = obfClass(data.name);
        if(dest == TREMINATE_THIS_CLASS)
            return;

        DeobfConstMapper t = (DeobfConstMapper) this.constMapper;
        if(dest != null && t.classMap.put(data.name, dest) != null) {
            System.out.println("重复的class name " + data.name);
        }

        MtDesc desc = new MtDesc(data.name, "", "");
        List<MethodSimple> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple method = methods.get(i);
            FlagList acc = method.accesses;
            if ((flags & REMOVE_SYNTHETIC) != 0) {
                acc.flag &= ~AccessFlag.SYNTHETIC;
            }
            if ((flags & ADD_PUBLIC) != 0 && (acc.flag & AccessFlag.PRIVATE) == 0) {
                acc.flag &= ~AccessFlag.PROTECTED;
                acc.flag |= AccessFlag.PUBLIC;
            }

            if ((desc.name = method.name.getString()).charAt(0) == '<') continue; // clinit, init
            desc.param = method.type.getString();
            if (!acc.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                if (isInherited(desc, true)) {
                    continue;
                }
            }
            String ms = obfMethodName(desc);
            if (ms != null) {
                t.methodMap.put(desc, ms);
                desc.flags = acc;
                desc = new MtDesc(data.name, "", "");
            }
        }

        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);
            FlagList acc = field.accesses;
            if ((flags & REMOVE_SYNTHETIC) != 0) {
                acc.flag &= ~AccessFlag.SYNTHETIC;
            }
            if ((flags & ADD_PUBLIC) != 0 && (acc.flag & AccessFlag.PRIVATE) == 0) {
                acc.flag &= ~AccessFlag.PROTECTED;
                acc.flag |= AccessFlag.PUBLIC;
            }

            desc.name = field.name.getString();
            desc.param = field.type.getString();
            String fs = obfFieldName(desc);
            if (fs != null) {
                t.fieldMap.put(Helpers.cast(desc), fs);
                desc.flags = acc;
                desc = new MtDesc(data.name, "", "");
            }
        }
    }

    public abstract String obfClass(String origin);
    public abstract String obfMethodName(MtDesc descriptor);
    public abstract String obfFieldName(MtDesc descriptor);

    @Override
    public final String obfFieldName(FlDesc descriptor) {
        throw new NoSuchMethodError();
    }
}
