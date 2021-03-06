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
package ilib.asm.fasterforge.transformers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;
import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstUTF;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModAPIManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModAPITransformer extends net.minecraftforge.fml.common.asm.transformers.ModAPITransformer {
    private static final boolean debug = true;

    private ListMultimap<String, ASMDataTable.ASMData> optionals;

    public ModAPITransformer() {}

    public byte[] transform(String name, String trName, byte[] b) {
        String lookupName = name;
        if (name.endsWith("$class"))
            lookupName = name.substring(0, name.length() - 6);
        if (optionals == null || !optionals.containsKey(lookupName)) return b;

        ConstantData data = Parser.parseConstants(b);
        if (debug) log("Optional - for {}", name);

        for (ASMDataTable.ASMData optional : optionals.get(lookupName)) {
            String modId = (String) optional.getAnnotationInfo().get("modid");
            if (Loader.isModLoaded(modId) || ModAPIManager.INSTANCE.hasAPI(modId)) {
                if (debug) log("Optional skipped {}", modId);
                continue;
            }

            if (debug) log("Optional process {}", name, modId);
            if (optional.getAnnotationInfo().containsKey("iface")) {
                Boolean ref = (Boolean) optional.getAnnotationInfo().get("striprefs");
                if (ref == null) ref = Boolean.FALSE;
                stripInterface(data, (String) optional.getAnnotationInfo().get("iface"), ref);
                continue;
            }

            stripMethod(data, optional.getObjectName());
        }
        return Parser.toByteArray(data);
    }

    private static void stripMethod(ConstantData data, String desc) {
        if (data.name.endsWith("$class")) {
            String subName = data.name.substring(0, data.name.length() - 6);
            int pos = desc.indexOf('(') + 1;
            desc = desc.substring(0, pos) + 'L' + subName + ';' + desc.substring(pos);
        }

        for (int i = 0; i < data.methods.size(); i++) {
            MethodNode mn = data.methods.get(i);
            if (desc.equals(mn.name() + mn.rawDesc())) {
                data.methods.remove(i);
                if (debug) log("Optional - method {}", desc);
                return;
            }
        }
        if (debug) log("Optional - NOT found", desc);
    }

    private static void stripInterface(ConstantData cn, String itf, boolean stripRefs) {
        String itfNative = itf.replace('.', '/');
        boolean found = cn.interfaces.remove(new CstClass(itfNative));
        if (!found) {
            if (debug) log("Optional - NOT found {}", itf);
            return;
        }
        Attribute sign = cn.attrByName("Signature");
        if (sign != null) {
            CstUTF v = (CstUTF) cn.cp.get(Parser.reader(sign));
            SignatureReader sr = new SignatureReader(v.getString());
            RemovingSignatureWriter sw = new RemovingSignatureWriter(itfNative);
            sr.accept(sw);
            v.setString(sw.toString());
            if (debug) log("Optional - signature remove {}", sw);
        }
        if (stripRefs) {
            for (int i = cn.methods.size() - 1; i >= 0; i--) {
                MethodNode mn = cn.methods.get(i);
                if (mn.rawDesc().contains(itfNative)) {
                    cn.methods.remove(i);
                    if (debug) log("Optional - remove interface {} parameter method {}", itf, mn.name());
                }
            }
        } else {
            if (debug) log("Optional - interface {} ", itf);
        }
    }

    private static void log(String s, Object... str) {
        ilib.asm.Loader.logger.info(s, str);
    }

    public void initTable(ASMDataTable dataTable) {
        this.optionals = ArrayListMultimap.create();
        Set<ASMDataTable.ASMData> interfaceLists = dataTable.getAll("net.minecraftforge.fml.common.Optional$InterfaceList");
        addData(unpackInterfaces(interfaceLists));
        Set<ASMDataTable.ASMData> interfaces = dataTable.getAll("net.minecraftforge.fml.common.Optional$Interface");
        addData(interfaces);
        Set<ASMDataTable.ASMData> methods = dataTable.getAll("net.minecraftforge.fml.common.Optional$Method");
        addData(methods);
    }

    @SuppressWarnings("unchecked")
    private Set<ASMDataTable.ASMData> unpackInterfaces(Set<ASMDataTable.ASMData> packedInterfaces) {
        Set<ASMDataTable.ASMData> result = Sets.newHashSet();
        for (ASMDataTable.ASMData data : packedInterfaces) {
            List<Map<String, Object>> packedList = (List<Map<String, Object>>) data.getAnnotationInfo().get("value");
            for (int i = 0; i < packedList.size(); i++) {
                result.add(data.copy(packedList.get(i)));
            }
        }
        return result;
    }

    private void addData(Set<ASMDataTable.ASMData> interfaces) {
        for (ASMDataTable.ASMData data : interfaces)
            this.optionals.put(data.getClassName(), data);
    }

    private static class RemovingSignatureWriter extends SignatureWriter {
        private final String ifaceName;

        RemovingSignatureWriter(String ifaceName) {
            this.ifaceName = ifaceName;
        }

        public void visitClassType(String name) {
            if (name.equals(this.ifaceName)) {
                super.visitClassType("java/lang/Object");
            } else {
                super.visitClassType(name);
            }
        }
    }
}
