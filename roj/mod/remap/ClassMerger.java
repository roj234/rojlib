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

import roj.asm.cst.CstClass;
import roj.asm.mapper.util.Context;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Field;
import roj.asm.tree.Method;
import roj.asm.tree.attr.AttrInnerClasses;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.tree.simple.MoFNode;
import roj.collect.MyHashMap;
import roj.ui.CmdUtil;
import roj.util.ByteReader;
import roj.util.Helpers;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

/**
 * Merge client and server classes
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/30 13:23
 */
public class ClassMerger {
    private static final boolean DEBUG = false;

    public int serverOnly, clientOnly, both;
    public int mergedField, mergedMethod, replaceMethod;

    public Collection<Context> process(List<Context> main, List<Context> sub) {
        MyHashMap<String, Context> byName = new MyHashMap<>();
        for (int i = 0; i < main.size(); i++) {
            Context ctx = main.get(i);
            byName.put(ctx.getName(), ctx);
        }

        clientOnly = main.size();
        serverOnly = sub.size();

        for (int i = 0; i < sub.size(); i++) {
            Context sc = sub.get(i);

            Context mc = byName.putIfAbsent(sc.getName(), sc);
            if (mc != null) {
                if(processOne(mc, sc)) {
                    mc.get();
                }
                clientOnly--;
                serverOnly--;
                both++;
            }
        }

        return byName.values();
    }

    private boolean processOne(Context main, Context sub) {
        ConstantData subData = sub.getData();
        ConstantData mainData = main.getData();

        boolean flag = false;

        List<? extends MoFNode> methods = subData.methods;
        for (int i = 0; i < methods.size(); i++) {
            MoFNode ms = methods.get(i);
            MoFNode found = null;
            int index = -1;
            for (ListIterator<? extends MoFNode> iterator = mainData.methods.listIterator(); iterator.hasNext(); ) {
                MoFNode ms2 = iterator.next();
                if (ms.name().equals(ms2.name()) && ms.rawDesc().equals(ms2.rawDesc())) {
                    found = ms2;
                    index = iterator.nextIndex() - 1;
                    break;
                }
            }
            if (found == null) {
                mergedMethod++;
                mainData.methods.add(Helpers.cast(ms instanceof Method ? ms : new Method(subData, (MethodSimple) ms)));
                flag = true;
            } else {
                Method mm = found instanceof Method ? (Method) found : new Method(mainData, (MethodSimple) found);

                Method v = detectPriority(mainData, mm, subData, ms);
                if(v != mm) {
                    mainData.methods.set(index, Helpers.cast(v));
                    flag = true;
                }
            }
        }

        List<? extends MoFNode> fields = subData.fields;
        for (int i = 0; i < fields.size(); i++) {
            MoFNode fs = fields.get(i);
            boolean found = false;
            for (MoFNode fs2 : mainData.fields) {
                if (fs.name().equals(fs2.name()) && fs.rawDesc().equals(fs2.rawDesc())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                mergedField++;
                mainData.fields.add(Helpers.cast(fs instanceof Field ? fs : new Field(subData, (FieldSimple) fs)));
                flag = true;
            }
        }

        processInnerClasses(mainData, subData);
        processItf(mainData, subData);

        return flag;
    }

    private Method detectPriority(ConstantData cstM, Method mainMethod, ConstantData cstS, MoFNode sub) {
        Method subMethod = sub instanceof Method ? (Method) sub : new Method(cstS, (MethodSimple) sub);

        if(subMethod.code == null)
            return mainMethod;
        if(mainMethod.code == null)
            return subMethod;

        if(mainMethod.code.instructions.size() != subMethod.code.instructions.size()) {
            replaceMethod++;

            if(DEBUG)
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
            main.addAttribute(subAttr);
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
            aIC = new AttrInnerClasses(new ByteReader(attr.getRawData()), clz.cp);
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

        // 这是干么？不懂
        //mainItf.sort(Comparator.comparing(clz -> clz.getValue().getString()));
    }
}