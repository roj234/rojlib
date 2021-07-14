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
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import org.objectweb.asm.Type;
import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.RemapTo;
import roj.asm.nixim.Shadow;
import roj.asm.struct.ConstantData;
import roj.asm.struct.anno.Annotation;
import roj.asm.struct.attr.AttrAnnotation;
import roj.asm.struct.attr.Attribute;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.util.ConstantPool;
import roj.io.IOUtil;
import roj.util.ByteReader;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Nixim("net.minecraftforge.fml.common.discovery.asm.ASMModParser")
public class NiximASMModParser extends ASMModParser {
    @Copy
    private Map<String, List<Annotation>> named;

    @Copy
    private LinkedList<Annotation> annotations1;
    @Shadow("asmType")
    private Type asmType;
    @Shadow("classVersion")
    private int classVersion;
    @Shadow("asmSuperType")
    private Type asmSuperType;
    @Shadow("interfaces")
    private Set<String> interfaces;

    @RemapTo(value = "<init>", useSuperInject = false)
    public NiximASMModParser(InputStream stream) throws IOException {
        super(null);
        annotations1 = new LinkedList<>();
        interfaces = new HashSet<>();
        named = new HashMap<>();
        try {
            byte[] bytes = IOUtil.readFully(stream);
            ConstantData data = Parser.parseConstants(bytes, true);

            this.asmType = TypeHelper.asmType(data.name);
            this.asmSuperType = TypeHelper.asmType(data.parent);
            this.classVersion = data.version;
            for (CstClass itf : data.interfaces) {
                this.interfaces.add(itf.getValue().getString());
            }

            AttrAnnotation a = getAnnotationValue(data.cp, data.attrByName("RuntimeInvisibleAnnotations"));
            if (a != null)
                named.computeIfAbsent(data.name.replace('/', '.'), (k) -> new ArrayList<>()).addAll(a.annotations);
            a = getAnnotationValue(data.cp, data.attrByName("RuntimeVisibleAnnotations"));
            if (a != null)
                named.computeIfAbsent(data.name.replace('/', '.'), (k) -> new ArrayList<>()).addAll(a.annotations);

            //if(Config.readFullAnnotation) {
            for (FieldSimple fieldSimple : data.fields) {
                List<Annotation> list = named.computeIfAbsent(fieldSimple.name.getString(), (k) -> new ArrayList<>());
                a = getAnnotationValue(data.cp, fieldSimple.attrByName("RuntimeInvisibleAnnotations"));

                if (a != null)
                    list.addAll(a.annotations);

                a = getAnnotationValue(data.cp, fieldSimple.attrByName("RuntimeVisibleAnnotations"));

                if (a != null)
                    list.addAll(a.annotations);
            }

            for (MethodSimple methodSimple : data.methods) {
                List<Annotation> list = named.computeIfAbsent(methodSimple.name.getString() + methodSimple.type.getString(), (k) -> new ArrayList<>());
                a = getAnnotationValue(data.cp, methodSimple.attrByName("RuntimeInvisibleAnnotations"));

                if (a != null)
                    list.addAll(a.annotations);

                a = getAnnotationValue(data.cp, methodSimple.attrByName("RuntimeVisibleAnnotations"));

                if (a != null)
                    list.addAll(a.annotations);
            }

            //for (MethodSimple methodSimple : data.methods) {
            //    attribute = methodSimple.attrByName("RuntimeInvisibleAnnotations");
            //    attribute1 = methodSimple.attrByName("RuntimeVisibleAnnotations");
            //    addo(data, attribute);
            //    addo(data, attribute1);
            //}
            //}

            //this.data = data;
        } catch (Exception ex) {
            FMLLog.log.error("Unable to read a class file correctly", ex);
            throw new LoaderException(ex);
        }
    }

    @Copy
    public Set<String> getItf() {
        return this.interfaces;
    }

    @Copy
    public Map<String, List<Annotation>> getFieldAnnotationMap() {
        return this.named;
    }

    @RemapTo("toString")
    public String toString() {
        return MoreObjects.toStringHelper("MIASMAnnotationDiscover").add("className", this.asmType.getClassName()).add("classVersion", this.classVersion).add("superName", this.asmSuperType.getClassName()).add("annotations", this.annotations1).toString();
    }

    @RemapTo("sendToTable")
    public void sendToTable(ASMDataTable table, ModCandidate candidate) {
        String normName = asmType.getClassName();
        for (Map.Entry<String, List<Annotation>> entry : named.entrySet()) {
            for (Annotation annotation : entry.getValue()) {
                table.addASMData(candidate, annotation.type.owner.replace('/', '.'), normName, entry.getKey(), TypeHelper.toPrimitive(annotation.values));
            }
        }
        for (String intf : this.interfaces)
            table.addASMData(candidate, intf, asmType.getInternalName(), null, null);
    }

    @RemapTo("getAnnotations")
    @Override
    public LinkedList<ModAnnotation> getAnnotations() {
        LinkedList<Annotation> list = new LinkedList<>(this.annotations1);
        for (Map.Entry<String, List<Annotation>> entry : named.entrySet()) {
            list.addAll(entry.getValue());
        }
        return Helpers.cast(list);
        //throw new UnsupportedOperationException("Fuck the package-private access! I can't get type");
    }

    @Copy
    private static AttrAnnotation getAnnotationValue(ConstantPool pool, Attribute attribute) {
        if (attribute == null)
            return null;

        return new AttrAnnotation(attribute.name, new ByteReader(attribute.getRawData()), pool);
    }
}