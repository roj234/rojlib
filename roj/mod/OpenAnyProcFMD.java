/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 *
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationProcessor.java
 */
package roj.mod;

import roj.asm.annotation.AnnotationProcessor;
import roj.asm.annotation.OpenAny;
import roj.asm.remapper.IRemapper;
import roj.asm.remapper.util.FlDesc;
import roj.asm.remapper.util.MtDesc;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OpenAnyProcFMD extends AnnotationProcessor {
    public static final Map<String, String> srg2mcp = new MyHashMap<>(1000, 1.5f);

    public OpenAnyProcFMD() {
        super();
        load_S2M_Map();
    }

    public static void load_S2M_Map() {
        if(srg2mcp.isEmpty()) {
            Shared.initRemapper();
            IRemapper remapper = Shared.remapper;

            for (Map.Entry<FlDesc, String> entry : remapper.getFieldMap().entrySet()) {
                srg2mcp.put(entry.getValue(), entry.getKey().name);
            }

            for (Map.Entry<MtDesc, String> entry : remapper.getMethodMap().entrySet()) {
                srg2mcp.put(entry.getValue(), entry.getKey().name);
            }
        }
    }

    @Override
    public boolean hook() {
        ModDevelopment.annotationHook(this);
        return false;
    }

    @Override
    public void processAnnotationData(List<OpenAny> list, Map<String, Set<String>> openAnyDatas) {
        for (OpenAny annotation : list) {
            String classQualifiedName = annotation.value().replace('.', '/').replace(':', '/');
            Set<String> data = openAnyDatas.computeIfAbsent(classQualifiedName, (key) -> new MyHashSet<>());

            SimpleList<String> strings = new SimpleList<>(annotation.names());
            strings.ensureCapacity(strings.size() << 1);
            for(String s : annotation.names()) {
                strings.add(srg2mcp(s));
            }

            data.addAll(strings);
        }
    }

    private String srg2mcp(String s) {
        return srg2mcp.getOrDefault(s, s);
    }
}