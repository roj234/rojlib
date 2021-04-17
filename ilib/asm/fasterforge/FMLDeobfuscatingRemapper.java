/*
 * This file is a part of MoreItems
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

import LZMA.LzmaInputStream;
import org.objectweb.asm.commons.Remapper;
import roj.asm.Parser;
import roj.asm.mapper.ConstMapper;
import roj.asm.mapper.Mapping;
import roj.asm.mapper.Util;
import roj.asm.mapper.util.Desc;
import roj.asm.tree.AccessData;
import roj.asm.type.Signature;
import roj.collect.Flippable;
import roj.collect.HashBiMap;
import roj.collect.MyHashMap;
import roj.util.Helpers;

import net.minecraft.launchwrapper.LaunchClassLoader;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.patcher.ClassPatchManager;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class FMLDeobfuscatingRemapper extends Remapper implements IFDAccessPort {
    public static final FMLDeobfuscatingRemapper INSTANCE = new FMLDeobfuscatingRemapper();
    private Flippable<String, String> classNameBiMap = new HashBiMap<>();
    private LaunchClassLoader classLoader;
    private final Map<String, Map<String, String>> fieldDescriptions = new MyHashMap<>();
    private ConstMapper mapping;

    private FMLDeobfuscatingRemapper() {
    }

    public void setupLoadOnly(String deobfFileName, boolean loadAll) {
        Mapping mapping = this.mapping = new ConstMapper();
        try {
            mapping.loadMap(new LzmaInputStream(new FileInputStream(deobfFileName)), false);
        } catch (IOException e) {
            FMLLog.log.error("An error occurred loading the deobfuscation map data", e);
        }

        this.classNameBiMap = mapping.getClassMap();
    }

    public void setup(File mcDir, LaunchClassLoader classLoader, String deobfFileName) {
        this.classLoader = classLoader;

        String prop = System.getProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp");
        Mapping mapping = this.mapping = new ConstMapper();

        try {
            if (prop == null || prop.isEmpty()) {
                InputStream classData = this.getClass().getResourceAsStream(deobfFileName);
                mapping.loadMap(new LzmaInputStream(classData), false);
            } else {
                mapping.loadMap(new FileInputStream(prop), false);
            }
        } catch (IOException e) {
            FMLLog.log.error("An error occurred loading the deobfuscation map data", e);
        }

        this.classNameBiMap = mapping.getClassMap();
    }

    public boolean isRemappedClass(String className) {
        return !this.map(className).equals(className);
    }

    @Nullable
    private String getFieldType(String owner, String name) {
        Map<String, String> b = this.fieldDescriptions.get(owner);
        if (b == null) {
            synchronized (fieldDescriptions) {
                try {
                    byte[] bytes = ClassPatchManager.INSTANCE.getPatchedResource(owner,
                                      this.map(owner).replace('/', '.'),
                                      this.classLoader);
                    if (bytes == null) {
                        this.fieldDescriptions.put(owner, Collections.emptyMap());
                        return null;
                    } else {
                        AccessData data = Parser.parseAccessDirect(bytes);
                        Map<String, String> resMap = new MyHashMap<>(data.fields.size());
                        for (AccessData.MOF node : data.fields) {
                            resMap.put(node.name, node.desc);
                        }

                        this.fieldDescriptions.put(owner, b = resMap);
                    }
                } catch (IOException e) {
                    FMLLog.log.error("A critical exception occurred reading a class file {}", owner, e);
                    this.fieldDescriptions.put(owner, Collections.emptyMap());
                    return null;
                }
            }
        }
        return b.get(name);
    }

    public String mapFieldName(String owner, String name, @Nullable String desc) {
        if (this.classNameBiMap != null && !this.classNameBiMap.isEmpty()) {
            Desc fd = Util.shareMD();
            fd.owner = owner;
            fd.name = name;
            fd.param = "";
            return mapping.getFieldMap().getOrDefault(fd, name);
        } else {
            return name;
        }
    }

    public String map(String typeName) {
        if (!this.classNameBiMap.isEmpty()) {
            return Util.mapClassName(classNameBiMap, typeName);
        } else {
            return typeName;
        }
    }

    public String unmap(String typeName) {
        if (!this.classNameBiMap.isEmpty()) {
            return Util.mapClassName(classNameBiMap.flip(), typeName);
        } else {
            return typeName;
        }
    }

    public String mapMethodName(String owner, String name, String desc) {
        if (this.classNameBiMap != null && !this.classNameBiMap.isEmpty()) {
            Desc shareMD = Util.shareMD();
            shareMD.owner = owner;
            shareMD.name = name;
            shareMD.param = desc;
            return mapping.getMethodMap().getOrDefault(shareMD, name);
        } else {
            return name;
        }
    }

    @Nullable
    public String mapSignature(String signature, boolean typeSignature) {
        if(signature == null || signature.contains("!*")) {
            return signature;
        }
        Signature parse = Signature.parse(signature);
        parse.rename(s -> classNameBiMap.get(s));
        return parse.toGeneric();
    }

    public Set<String> getObfedClasses() {
        return this.classNameBiMap.keySet();
    }

    @Nullable
    public String getStaticFieldType(String oldType, String oldName, String newType, String newName) {
        String fType = this.getFieldType(newType, newName);
        if (!oldType.equals(newType)) {
            fieldDescriptions.computeIfAbsent(newType, Helpers.fnMyHashMap()).put(newName, fType);
        }
        return fType;
    }

    @Override
    public ConstMapper getMapper() {
        return mapping;
    }
}
