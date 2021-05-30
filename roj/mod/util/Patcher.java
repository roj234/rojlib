package roj.mod.util;

import LZMA.LzmaInputStream;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: Patcher.java
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
                CmdUtil.warning("按照Forge的设计要清空" + patch);
                CmdUtil.warning("但是清空了我的映射器没法处理, 毕竟OB的文件");
                CmdUtil.warning("如果你说我不行, 帮我找到Forge咋处理这些该死的violent的");
                return null;
                //return new ByteList(EMPTY);
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
                clientPatches = new HashMap<>();
                serverPatches = new HashMap<>();
                JarOutputStream jos = new JarReaderStream(((entry, byteList) -> {
                    try {
                        Patch cp = readPatch(new ByteReader(byteList));
                        if(entry.getName().startsWith("binpatch/client")) {
                            clientPatches.computeIfAbsent(cp.source + ".class", (s) -> new LinkedList<>()).add(cp);
                        } else if(entry.getName().startsWith("binpatch/server")) {
                            serverPatches.computeIfAbsent(cp.source + ".class", (s) -> new LinkedList<>()).add(cp);
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
            return "For " + source + " len=" + patch.length + ", Tlen=" + (exist ? " > 0" : " = 0");
        }
    }

}
