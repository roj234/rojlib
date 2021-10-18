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
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModAPIManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;
import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstUTF;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodSimple;
import roj.asm.tree.attr.Attribute;

import java.util.*;

public class ModAPITransformer extends net.minecraftforge.fml.common.asm.transformers.ModAPITransformer {
    private static final boolean logDebugInfo = true;//(Config.debug & 1) != 0;

    private ListMultimap<String, ASMDataTable.ASMData> optionals;

    public ModAPITransformer() {

    }

    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        String lookupName = name;
        if (name.endsWith("$class"))
            lookupName = name.substring(0, name.length() - 6);
        if (this.optionals == null || !this.optionals.containsKey(lookupName))
            return basicClass;
        ConstantData classNode = Parser.parseConstants(basicClass);
        if (logDebugInfo)
            log("Optional removal - found optionals for class {} - processing", name);
        for (ASMDataTable.ASMData optional : this.optionals.get(lookupName)) {
            String modId = (String) optional.getAnnotationInfo().get("modid");
            if (Loader.isModLoaded(modId) || ModAPIManager.INSTANCE.hasAPI(modId)) {
                if (logDebugInfo)
                    log("Optional removal skipped - mod present {}", modId);
                continue;
            }
            if (logDebugInfo)
                log("Optional on {} triggered - mod missing {}", name, modId);
            if (optional.getAnnotationInfo().containsKey("iface")) {
                Boolean stripRefs = (Boolean) optional.getAnnotationInfo().get("striprefs");
                if (stripRefs == null)
                    stripRefs = Boolean.FALSE;
                stripInterface(classNode, (String) optional.getAnnotationInfo().get("iface"), stripRefs);
                continue;
            }
            stripMethod(classNode, optional.getObjectName());
        }
        if (logDebugInfo)
            log("Optional removal - class {} processed", name);
        return Parser.toByteArray(classNode);
    }

    private void stripMethod(ConstantData classNode, String methodDescriptor) {
        if (classNode.name.endsWith("$class")) {
            String subName = classNode.name.substring(0, classNode.name.length() - 6);
            int pos = methodDescriptor.indexOf('(') + 1;
            methodDescriptor = methodDescriptor.substring(0, pos) + 'L' + subName + ';' + methodDescriptor.substring(pos);
        }
        for (ListIterator<MethodSimple> iterator = classNode.methods.listIterator(); iterator.hasNext(); ) {
            MethodSimple method = iterator.next();
            if (methodDescriptor.equals(method.name + method.type.getString())) {
                iterator.remove();
                if (logDebugInfo)
                    log("Optional removal - method {} removed", methodDescriptor);
                return;
            }
        }
        if (logDebugInfo)
            log("Optional removal - method {} NOT removed - not found", methodDescriptor);
    }

    private void log(String s, Object... methodDescriptor) {
        ilib.asm.Loader.logger.info(s, methodDescriptor);
    }

    private void stripInterface(ConstantData cn, String interfaceName, boolean stripRefs) {
        String ifaceName = interfaceName.replace('.', '/');
        boolean found = cn.interfaces.remove(new CstClass(ifaceName));
        Attribute sign = cn.attrByName("signature");
        if (found && sign != null) {
            CstUTF v = (CstUTF) cn.cp.get(Parser.reader(sign));
            SignatureReader sr = new SignatureReader(v.getString());
            RemovingSignatureWriter sw = new RemovingSignatureWriter(ifaceName);
            sr.accept(sw);
            v.setString(sw.toString());
            if (logDebugInfo)
                log("Optional removal - interface {} removed from type signature", interfaceName);
        }
        if (found && logDebugInfo)
            log("Optional removal - interface {} removed", interfaceName);
        if (!found && logDebugInfo)
            log("Optional removal - interface {} NOT removed - not found", interfaceName);
        if (found && stripRefs) {
            if (logDebugInfo)
                log("Optional removal - interface {} - stripping method signature references", interfaceName);
            for (Iterator<MethodSimple> iterator = cn.methods.iterator(); iterator.hasNext(); ) {
                MethodSimple node = iterator.next();
                if (node.type.getString().contains(ifaceName)) {
                    if (logDebugInfo)
                        log("Optional removal - interface {} - stripping method containing reference {}", interfaceName, node.name);
                    iterator.remove();
                }
            }
            if (logDebugInfo)
                log("Optional removal - interface {} - all method signature references stripped", interfaceName);
        } else if (found) {
            if (logDebugInfo)
                log("Optional removal - interface {} - NOT stripping method signature references", interfaceName);
        }
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
            for (Map<String, Object> packed : packedList) {
                ASMDataTable.ASMData newData = data.copy(packed);
                result.add(newData);
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
