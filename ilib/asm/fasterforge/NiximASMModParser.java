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
package ilib.asm.fasterforge;

import com.google.common.base.MoreObjects;
import ilib.asm.fasterforge.anc.FastParser;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.objectweb.asm.Type;
import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldSimple;
import roj.asm.tree.MethodSimple;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.ConstantPool;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Nixim(value = "net.minecraftforge.fml.common.discovery.asm.ASMModParser", copyItf = true)
public class NiximASMModParser extends ASMModParser implements FastParser {
    @Copy
    private Map<String, List<Annotation>> named;

    @Shadow("asmType")
    private Type asmType;
    @Shadow("classVersion")
    private int classVersion;
    @Shadow("asmSuperType")
    private Type asmSuperType;
    @Shadow("interfaces")
    private Set<String> interfaces;

    NiximASMModParser() throws IOException {
        super(null);
    }

    void $$$CONSTRUCTOR() {}

    @Inject(value = "<init>", at = At.REPLACE)
    public void remapInit(InputStream stream) {
        $$$CONSTRUCTOR();

        interfaces = new MyHashSet<>();
        named = new MyHashMap<>();
        try {
            ByteList shared = IOUtil.getSharedByteBuf();
            shared.clear();
            shared.readStreamArrayFully(stream);
            ConstantData data = Parser.parseConstants(shared);

            this.asmType = TypeHelper.asmType(data.name);
            this.asmSuperType = TypeHelper.asmType(data.parent);
            this.classVersion = data.version;
            List<CstClass> classes = data.interfaces;
            for (int i = 0; i < classes.size(); i++) {
                this.interfaces.add(classes.get(i).getValue().getString());
            }

            List<Annotation> list = named.computeIfAbsent(data.name.replace('/', '.'), Helpers.fnArrayList());
            getAnn(data.cp, data.attrByName("RuntimeInvisibleAnnotations"), list);
            getAnn(data.cp, data.attrByName("RuntimeVisibleAnnotations"), list);

            List<FieldSimple> fields = data.fields;
            for (int i = 0; i < fields.size(); i++) {
                FieldSimple fs = fields.get(i);
                list = named.computeIfAbsent(fs.name.getString(), Helpers.fnArrayList());
                getAnn(data.cp, fs.attrByName("RuntimeInvisibleAnnotations"), list);
                getAnn(data.cp, fs.attrByName("RuntimeVisibleAnnotations"), list);
            }

            List<MethodSimple> methods = data.methods;
            for (int i = 0; i < methods.size(); i++) {
                MethodSimple ms = methods.get(i);
                list = named.computeIfAbsent(ms.name.getString() + ms.type.getString(), Helpers.fnArrayList());
                getAnn(data.cp, ms.attrByName("RuntimeVisibleAnnotations"), list);
                getAnn(data.cp, ms.attrByName("RuntimeVisibleAnnotations"), list);
            }
        } catch (Exception ex) {
            FMLLog.log.error("class加载失败", ex);
            throw new LoaderException(ex);
        }
    }

    @Copy
    @Override
    public Set<String> getItf() {
        return this.interfaces;
    }

    @Copy
    @Override
    public Map<String, List<Annotation>> getAnnotationMap() {
        return this.named;
    }

    @Override
    public List<Annotation> getClassAnnotations() {
        return this.named.getOrDefault(asmType.getClassName(), Collections.emptyList());
    }

    @Inject("toString")
    public String toString() {
        return MoreObjects.toStringHelper("FastAnnotationDiscover")
                          .add("name", this.asmType.getClassName())
                          .add("version", this.classVersion)
                          .add("parent", asmSuperType.getClassName()).toString();
    }

    @Inject("sendToTable")
    public void sendToTable(ASMDataTable table, ModCandidate candidate) {
        String normName = asmType.getClassName();
        for (Map.Entry<String, List<Annotation>> entry : named.entrySet()) {
            List<Annotation> value = entry.getValue();
            for (int i = 0; i < value.size(); i++) {
                Annotation ann = value.get(i);
                table.addASMData(candidate, ann.clazz.replace('/', '.'), normName,
                                 entry.getKey(), TypeHelper.toPrimitive(ann.values));
            }
        }
        for (String intf : this.interfaces)
            table.addASMData(candidate, intf, asmType.getInternalName(), null, null);
    }

    @Copy
    private static void getAnn(ConstantPool cp, Attribute attr, List<Annotation> list) {
        if (attr == null)
            return;
        ByteReader r = Parser.reader(attr);

        int cnt = r.readUnsignedShort();
        while (cnt-- > 0) {
            list.add(Annotation.deserialize(cp, r));
        }
    }
}