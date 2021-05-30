package roj.mod.remap;

import roj.asm.cst.CstClass;
import roj.asm.remapper.util.Context;
import roj.asm.struct.ConstantData;
import roj.asm.struct.Field;
import roj.asm.struct.Method;
import roj.asm.struct.attr.AttrInnerClasses;
import roj.asm.struct.attr.Attribute;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.struct.simple.MoFNode;
import roj.collect.MyHashMap;
import roj.ui.CmdUtil;
import roj.util.ByteReader;
import roj.util.Helpers;

import java.util.*;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/30 13:23
 */
public class ClassMerger {
    private static final boolean DEBUG = false;

    public int serverOnly, clientOnly, both;
    public int mergedField, mergedMethod, replaceMethod;

    public Collection<Context> process(List<Context> clientContexts, List<Context> serverContexts) {
        Map<String, Context> contextMap = new MyHashMap<>();
        for(Context context : clientContexts) {
            contextMap.put(context.getName(), context);
        }

        clientOnly = clientContexts.size();
        serverOnly = serverContexts.size();

        for(Context context : serverContexts) {
            if(contextMap.containsKey(context.getName())) {
                processOne(contextMap.get(context.getName()), context);
                clientOnly--;
                serverOnly--;
                both++;
            } else {
                contextMap.put(context.getName(), context);
            }
        }

        return contextMap.values();
    }

    private void processOne(Context main, Context sub) {
        ConstantData subData = sub.getData();
        ConstantData mainData = main.getData();

        for(MoFNode ms : subData.methods) {
            MoFNode found = null;
            int index = -1;
            for (ListIterator<MethodSimple> iterator = mainData.methods.listIterator(); iterator.hasNext(); ) {
                MoFNode ms2 = iterator.next();
                if (ms.name().equals(ms2.name()) && ms.rawDesc().equals(ms2.rawDesc())) {
                    found = ms2;
                    index = iterator.nextIndex() - 1;
                    break;
                }
            }
            if(found == null) {
                mergedMethod++;
                mainData.methods.add(Helpers.cast(ms instanceof Method ? ms : new Method(subData, (MethodSimple) ms)));
            } else {
                mainData.methods.set(index, Helpers.cast(detectPriority(mainData, found, subData, ms)));
            }
        }

        for(MoFNode fs : subData.fields) {
            boolean found = false;
            for(MoFNode fs2 : mainData.fields) {
                if(fs.name().equals(fs2.name()) && fs.rawDesc().equals(fs2.rawDesc())) {
                    found = true;
                    break;
                }
            }
            if(!found) {
                mergedField++;
                mainData.fields.add(Helpers.cast(fs instanceof Field ? fs : new Field(subData, (FieldSimple) fs)));
            }
        }

        processInnerClasses(mainData, subData);
        processItf(mainData, subData);

    }

    private Method detectPriority(ConstantData cstM, MoFNode main, ConstantData cstS, MoFNode sub) {
        Method mainMethod = main instanceof Method ? (Method) main : new Method(cstM, (MethodSimple) main);
        Method subMethod = sub instanceof Method ? (Method) sub : new Method(cstS, (MethodSimple) sub);

        if(subMethod.code == null)
            return mainMethod;
        if(mainMethod.code == null) {
            return subMethod;
        }

        if(mainMethod.code.instructions.size() != subMethod.code.instructions.size()) {
            replaceMethod++;

            if(DEBUG)
                CmdUtil.warning("同名同参方法覆盖!" + cstM.name + '.' + main.name() + main.rawDesc());
        }

        // 指令合并太草了
        if(mainMethod.code.instructions.size() >= subMethod.code.instructions.size()) {
            return mainMethod;
        }
        return subMethod;
    }

    private void processInnerClasses(ConstantData main, ConstantData sub) {
        AttrInnerClasses mainAttr = getAttr(main);
        AttrInnerClasses subAttr = getAttr(sub);
        if(subAttr == null)
            return;
        if(mainAttr == null) {
            main.addAttribute(subAttr);
            return;
        }

        for (AttrInnerClasses.InnerClass sc : subAttr.classes) {
            boolean found = false;
            for (AttrInnerClasses.InnerClass mc : mainAttr.classes) {
                if (mc.equals(sc)) {
                    found = true;
                    break;
                }
            }
            if(!found) {
                mainAttr.classes.add(sc);
            }
        }
    }

    private AttrInnerClasses getAttr(ConstantData clz) {
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

        for (CstClass n : subItf) {
            if (!mainItf.contains(n)) {
                mainItf.add(n);
            }
        }

        mainItf.sort(Comparator.comparing(clz -> clz.getValue().getString()));
    }
}