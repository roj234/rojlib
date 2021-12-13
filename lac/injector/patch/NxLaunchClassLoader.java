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
package lac.injector.patch;

import lac.client.AccessHelper;
import org.objectweb.asm.ClassVisitor;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;
import roj.collect.MyHashSet;
import roj.crypt.NotMd5;
import roj.crypt.SM3;
import roj.crypt.SM4;
import roj.io.IOUtil;
import roj.reflect.DirectAccessor;
import roj.text.DottedStringPool;
import roj.text.StringPool;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/10/15 19:00
 */
@Nixim(value = "net.minecraft.launchwrapper.LaunchClassLoader", copyItf = true)
class NxLaunchClassLoader extends ClassLoader implements AccessHelper {
    @Copy(staticInitializer = "_load_", targetIsFinal = true)
    static Set<String> classes;
    @Copy(targetIsFinal = true)
    static AccessHelper helper;

    static void _load_() {
        File file = new File("mods.info");
        if (file.isFile()) {
            try {
                helper = DirectAccessor.builder(AccessHelper.class).access(ClassLoader.class, "classes", "getRaw", null).build();

                loadClasses(file);
            } catch (Throwable ignored) {
                classes = Collections.emptySet();
            }
        }
    }

    @Copy
    static void loadClasses(File file) throws Exception {
        ByteReader r = new ByteReader(decodeModInfo(file));
        StringPool p = new DottedStringPool(r, '.');
        classes = new MyHashSet<>();
        for (int i = r.readInt() - 1; i >= 0; i--) {
            classes.add(p.readString(r));
        }
    }

    @Copy
    static ByteList decodeModInfo(File file) throws IOException, GeneralSecurityException {
        NotMd5 notMd5 = new NotMd5();

        ByteWriter bw = new ByteWriter();
        digestModFiles(notMd5, bw);

        SM4 sm4 = new SM4();
        sm4.reset(SM4.DECRYPT | SM4.SM4_PADDING | SM4.SM4_STREAMED);
        sm4.setOption(SM4.SM4_IV, Arrays.copyOf(notMd5.digest(), 16));
        sm4.setKey(bw.toByteArray());

        ByteList out = bw.list;
        out.clear();

        ByteBuffer in = ByteBuffer.wrap(IOUtil.read(file));
        out.ensureCapacity(in.limit());
        sm4.crypt(in, ByteBuffer.wrap(out.list));
        return out;
    }

    @Copy
    static void digestModFiles(NotMd5 notMd5, ByteWriter bw) throws IOException {
        SM3 sm3 = new SM3();

        File[] mods = new File("mods").listFiles();
        Arrays.sort(mods, (o1, o2) -> o1.getName().compareTo(o2.getName()));

        byte[] buf = new byte[1024];
        for (File fn : mods) {
            notMd5.update(fn.getName().getBytes(StandardCharsets.UTF_16));
            sm3.update((byte) fn.length());
            try (FileInputStream in = new FileInputStream(fn)) {
                int remain = in.available();
                while (true) {
                    int read = in.read(buf);
                    if (read <= 0) break;
                    sm3.update(buf, 0, read);
                    remain -= read;
                    if (remain > 1024)
                        remain -= in.skip(1024);
                }
            }
            bw.writeBytes(sm3.digest());
        }
    }

    static Class<?> $$$CONTINUE() { return null; }

    @Inject(value = "findClass", at = At.HEAD, flags = Inject.FLAG_MODIFIABLE_PARAMETER)
    public Class<?> findClass(String name) {
        if (!classes.contains(name))
            name = randomGet(name.hashCode());
        return $$$CONTINUE();
    }

    @Copy
    static String randomGet(int name) {
        Vector<Class<?>> v = helper.getRaw(Launch.classLoader);
        return v.get(((int) System.nanoTime()) % v.size()).getName();
    }

    @Copy
    @Override
    public List<Class<?>> getSorted(ClassLoader loader) {
        return staticGetSorted(this);
    }

    @Copy
    static List<Class<?>> staticGetSorted(ClassLoader loader) {
        Vector<Class<?>> raw = ((AccessHelper) loader).getRaw(ClassVisitor.class.getClassLoader());
        List<Class<?>> list = new ArrayList<>(raw);
        list.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
        return list;
    }

    @Copy
    @Override
    public Vector<Class<?>> getRaw(ClassLoader loader) {
       return helper.getRaw(this);
    }
}
