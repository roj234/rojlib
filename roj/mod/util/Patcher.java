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
package roj.mod.util;

import LZMA.LzmaInputStream;
import roj.asm.tree.Clazz;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.MyHashMap;
import roj.io.JarReaderStream;
import roj.repackage.com_nothome_delta.ByteBufferSeekableSource;
import roj.repackage.com_nothome_delta.GDiffPatcher;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GDiff file patcher
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 19:59
 */
public final class Patcher {
    public static final boolean DEBUG = false;

    public static final byte[] EMPTY = new byte[0];

    private final GDiffPatcher serverPatcher = new GDiffPatcher();
    private final GDiffPatcher clientPatcher = new GDiffPatcher();

    private Map<String, List<Patch>> clientPatches, serverPatches;

    public int clientSuccessCount, serverSuccessCount, errorCount;

    public ByteList patchClient(@Nonnull String name, @Nonnull ByteList basicClass) {
        ByteList patch = patch(name, basicClass, clientPatches, clientPatcher);
        if(patch != null)
            clientSuccessCount++;
        return patch;
    }

    public ByteList patchServer(@Nonnull String name, @Nonnull ByteList basicClass) {
        ByteList patch = patch(name, basicClass, serverPatches, serverPatcher);
        if(patch != null)
            serverSuccessCount++;
        return patch;
    }

    public ByteList patch(@Nonnull String name, @Nonnull ByteList input, Map<String, List<Patch>> patchMap, GDiffPatcher patcher) {
        if (patchMap == null)
            return null;

        List<Patch> patches = patchMap.remove(name);
        if (patches == null)
            return null;

        if (DEBUG)
            System.out.println("开始给" + name + "打补丁 数量 " + patches.size());
        for (Patch patch : patches) {
            if (!patch.exist) {
                if(input.pos() != 0) {
                    CmdUtil.warning("期待空class " + patch.source);
                    errorCount++;
                    input = new ByteList(0);
                }
            } else {
                if (input.pos() == 0) {
                    throw new RuntimeException("期待非空class " + patch.source);
                }
                Adler32 adler32 = new Adler32();
                adler32.update(input.list, input.offset(), input.pos());
                int inputChecksum = (int) adler32.getValue();
                if (patch.checksum != inputChecksum) {
                    errorCount++;
                    //CmdUtil.warning("类 " + patch.targetClassName + " 的效验码不正确.");// class: " + Integer.toHexString(inputChecksum) + ", 补丁: " + Integer.toHexString(patch.inputChecksum) + ".");
                    return null;
                }
            }
            if(patch.patch.length == 0) {
                CmdUtil.warning("按照Forge的设计要清空: " + patch);
                CmdUtil.warning("但是清空？ 什么垃圾玩意");
                Clazz clazz = new Clazz();
                clazz.parent = "java/lang/Object";
                clazz.name = patch.source;
                clazz.accesses = new FlagList(AccessFlag.PUBLIC | AccessFlag.SUPER_OR_SYNC);
                return clazz.getBytes();
            }
            try {
                ByteList out = new ByteList(input.pos());
                patcher.patch(new ByteBufferSeekableSource(input.toByteBuffer()), new ByteArrayInputStream(patch.patch), out.asOutputStream());
                input = out;
            } catch (IOException e) {
                CmdUtil.error(name + "打补丁失败", e);
            }
        }
        if(DEBUG)
            CmdUtil.success("补丁应用成功 ");
        return input;
    }

    public void setup113(InputStream serverStream, Map<String, String> unmapper) {
        try {
            try (LzmaInputStream decompressed = new LzmaInputStream(serverStream)) {
                serverPatches = new MyHashMap<>();
                ZipInputStream zis = new ZipInputStream(decompressed);
                ZipEntry ze;
                ByteList list = new ByteList(2048);
                while ((ze = zis.getNextEntry()) != null) {
                    if(ze.getName().endsWith(".binpatch")) {
                        list.clear();
                        list.readStreamArrayFully(zis);
                        Patch cp = read113Patch(new ByteReader(list));
                        String cn = unmapper.getOrDefault(cp.source, cp.source);

                        serverPatches.computeIfAbsent(cn + ".class", Helpers.fnArrayList()).add(cp);
                    }
                    zis.closeEntry();
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException("补丁加载失败!", e);
        }
        if(DEBUG) {
            System.out.println("服务端补丁数量: " + serverPatches.size());
        }
    }

    public void setup112(InputStream stream) {
        try {
            try (LzmaInputStream decompressed = new LzmaInputStream(stream)) {
                clientPatches = new MyHashMap<>();
                serverPatches = new MyHashMap<>();
                JarOutputStream jos = new JarReaderStream(((entry, byteList) -> {
                    try {
                        Patch cp = readPatch(new ByteReader(byteList));
                        if(entry.getName().startsWith("binpatch/client")) {
                            clientPatches.computeIfAbsent(cp.source + ".class", Helpers.fnArrayList()).add(cp);
                        } else if(entry.getName().startsWith("binpatch/server")) {
                            serverPatches.computeIfAbsent(cp.source + ".class", Helpers.fnArrayList()).add(cp);
                        } else {
                            CmdUtil.warning("未知名字 " + entry.getName());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
                Pack200.newUnpacker().unpack(decompressed, jos);
            }
        } catch (Throwable e) {
            throw new RuntimeException("补丁加载失败!", e);
        }
        if(DEBUG) {
            System.out.println("客户端补丁数量: " + clientPatches.size());
            System.out.println("服务端补丁数量: " + serverPatches.size());
        }
    }

    private static Patch readPatch(DataInput input) throws IOException {
        String name = input.readUTF(), src = input.readUTF(), target = input.readUTF();
        boolean exists = input.readBoolean();
        int inputChecksum = 0;
        if (exists)
            inputChecksum = input.readInt();
        int patchLength = input.readInt();
        byte[] patchBytes = new byte[patchLength];
        input.readFully(patchBytes);
        return new Patch(src, target, exists, inputChecksum, patchBytes);
    }

    private static Patch read113Patch(DataInput input) throws IOException {
        int version = input.readByte() & 0xFF;
        if (version != 1)
            throw new IOException("Unsupported patch format: " + version);
        String obf = input.readUTF();
        String srg = input.readUTF();
        boolean exists = input.readBoolean();
        int checksum = exists ? input.readInt() : 0;
        int length = input.readInt();
        byte[] data = new byte[length];
        input.readFully(data);

        return new Patch(obf, srg, exists, checksum, data);
    }

    public void reset() {
        if(clientPatches != null)
            clientPatches.clear();
        if(serverPatches != null)
            serverPatches.clear();
        clientPatches = serverPatches = null;
        clientSuccessCount = serverSuccessCount = errorCount = 0;
    }

    public static class Patch {
        public String source;
        //public final String target;
        public final boolean exist;
        public final byte[] patch;
        public final int checksum;

        public Patch(String source, String target, boolean exist, int checksum, byte[] patch) {
            this.source = source;
            //this.target = target;
            this.exist = exist;
            this.checksum = checksum;
            this.patch = patch;
        }

        public String toString() {
            return "Src: " + source + " Patch.length: " + patch.length + ", Target.length: " + (exist ? " > 0" : " = 0");
        }
    }

}
