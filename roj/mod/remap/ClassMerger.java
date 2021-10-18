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
package roj.mod.remap;

import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.mapper.util.Context;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrInnerClasses;
import roj.asm.tree.attr.Attribute;
import roj.collect.MyHashMap;
import roj.ui.CmdUtil;
import roj.util.Helpers;

import java.util.Collection;
import java.util.List;

/**
 * Merge client and server classes
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/30 13:23
 */
public class ClassMerger {
    public int serverOnly, clientOnly, both;
    public int mergedField, mergedMethod, replaceMethod;

    public Collection<Context> process(List<Context> main, List<Context> sub) {
        MyHashMap<String, Context> byName = new MyHashMap<>();
        for (int i = 0; i < main.size(); i++) {
            Context ctx = main.get(i);
            byName.put(ctx.getFileName(), ctx);
        }

        clientOnly = main.size();
        serverOnly = sub.size();

        for (int i = 0; i < sub.size(); i++) {
            Context sc = sub.get(i);

            Context mc = byName.putIfAbsent(sc.getFileName(), sc);
            if (mc != null) {
                processOne(mc, sc);
                clientOnly--;
                serverOnly--;
                both++;
            }
        }

        return byName.values();
    }

    private void processOne(Context main, Context sub) {
        ConstantData subData = sub.getData();
        ConstantData mainData = main.getData();

        List<? extends MoFNode> subMs = subData.methods;
        List<? extends MoFNode> mainMs = mainData.methods;
        outer:
        for (int i = 0; i < subMs.size(); i++) {
            MoFNode sm = subMs.get(i);
            for (int j = 0; j < mainMs.size(); j++) {
                MoFNode mm = mainMs.get(j);
                if (sm.name().equals(mm.name()) && sm.rawDesc().equals(mm.rawDesc())) {
                    Method Mm = mm instanceof Method ? (Method) mm : new Method(mainData, (MethodSimple) mm);

                    Method v = detectPriority(mainData, Mm, subData, sm);
                    if(v != Mm) {
                        mainMs.set(j, Helpers.cast(v));
                    }
                    continue outer;
                }
            }
            mergedMethod++;
            mainMs.add(Helpers.cast(sm instanceof Method ? sm : new Method(subData, (MethodSimple) sm)));
        }

        List<? extends MoFNode> subFs = subData.fields;
        List<? extends MoFNode> mainFs = mainData.fields;
        outer:
        for (int i = 0; i < subFs.size(); i++) {
            MoFNode fs = subFs.get(i);
            for (int j = 0; j < mainFs.size(); j++) {
                MoFNode fs2 = mainFs.get(j);
                if (fs.name().equals(fs2.name()) && fs.rawDesc().equals(fs2.rawDesc())) {
                    continue outer;
                }
            }
            mergedField++;
            mainData.fields.add(Helpers.cast(fs instanceof Field ? fs : new Field(subData, (FieldSimple) fs)));
        }

        processInnerClasses(mainData, subData);
        processItf(mainData, subData);
    }

    private Method detectPriority(ConstantData cstM, Method mainMethod, ConstantData cstS, MoFNode sub) {
        Method subMethod = sub instanceof Method ? (Method) sub : new Method(cstS, (MethodSimple) sub);

        if(subMethod.code == null)
            return mainMethod;
        if(mainMethod.code == null)
            return subMethod;

        if(mainMethod.code.instructions.size() != subMethod.code.instructions.size()) {
            replaceMethod++;

            CmdUtil.warning("同名同参方法覆盖!" + cstM.name + '.' + mainMethod.name() + mainMethod.rawDesc());
        }

        // 指令合并太草了
        if(mainMethod.code.instructions.size() >= subMethod.code.instructions.size()) {
            return mainMethod;
        }
        return subMethod;
    }

    private void processInnerClasses(ConstantData main, ConstantData sub) {
        AttrInnerClasses subAttr = getAttr(sub);
        if(subAttr == null)
            return;
        AttrInnerClasses mainAttr = getAttr(main);
        if(mainAttr == null) {
            main.attributes.add(subAttr);
            return;
        }

        List<AttrInnerClasses.InnerClass> scs = subAttr.classes;
        List<AttrInnerClasses.InnerClass> mcs = mainAttr.classes;
        o:
        for (int i = 0; i < scs.size(); i++) {
            AttrInnerClasses.InnerClass sc = scs.get(i);

            for (int j = 0; j < mcs.size(); j++) {
                AttrInnerClasses.InnerClass mc = mcs.get(j);
                if (mc.equals(sc)) {
                    continue o;
                }
            }

            mainAttr.classes.add(sc);
        }

        main.attributes.putByName(mainAttr);
    }

    private static AttrInnerClasses getAttr(ConstantData clz) {
        AttrInnerClasses aIC = null;
        Attribute attr = clz.attrByName("InnerClasses");
        if(attr instanceof AttrInnerClasses) {
            aIC = (AttrInnerClasses) attr;
        } else if(attr != null) {
            aIC = new AttrInnerClasses(Parser.reader(attr), clz.cp);
        }
        return aIC;
    }

    private void processItf(ConstantData main, ConstantData sub) {
        List<CstClass> mainItf = main.interfaces;
        List<CstClass> subItf = sub.interfaces;

        for (int i = 0; i < subItf.size(); i++) {
            CstClass n = subItf.get(i);
            if (!mainItf.contains(n)) {
                mainItf.add(n);
            }
        }
    }
}