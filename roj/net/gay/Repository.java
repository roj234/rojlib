/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.net.gay;

import roj.collect.MyHashMap;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.math.Vec2i;
import roj.util.ByteList;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * @author solo6975
 * @version 0.1
 * @since 2022/1/3 17:25
 */
public final class Repository {
    static final int HEADER = 0xFEDCBA02;

    private final List<Ver>             versions;
    private final MyHashMap<Vec2i, Ver> patches;

    final File basePath, configFile;
    int pathLen;
    Predicate<File> filter;

    private int index;

    public Repository(File dir) {
        if (!dir.isDirectory())
            throw new IllegalStateException("Not a directory");
        this.basePath = dir;
        this.configFile = new File(dir, ".gay.bin");
        this.pathLen = dir.getAbsolutePath().length() + 1;
        this.versions = new ArrayList<>();
        this.patches = new MyHashMap<>();
        this.filter = (f) -> !f.getName().startsWith(".");
        this.index = -1;
    }

    public static Repository init(File file) {
        return new Repository(file);
    }

    public void load() throws IOException {
        if (!configFile.isFile()) return;
        ByteList sb = IOUtil.getSharedByteBuf();
        sb.clear();
        sb.readStreamFully(new InflaterInputStream(new FileInputStream(configFile)));
        if (sb.readInt() != HEADER)
            throw new IllegalArgumentException();
        index = sb.readVarInt();
        int len = sb.readVarInt(false);
        Ver v = new Ver(this), v1;
        while (len-- > 0) {
            v.read(sb);
            if (!versions.isEmpty()) {
                v1 = versions.get(versions.size() - 1).copy();
                v1.deltaTo(v, true);
            } else {
                v1 = v.copy();
            }
            versions.add(v1);
        }
    }

    public void save() throws IOException {
        ByteList sb = IOUtil.getSharedByteBuf();
        sb.clear();
        sb.putInt(HEADER).putVarInt(index)
          .putVarInt(versions.size(), false);
        try (OutputStream fos = new DeflaterOutputStream(new FileOutputStream(configFile))) {
            sb.writeToStream(fos);
            sb.clear();
            Ver v;
            for (int i = 0; i < versions.size(); i++) {
                if (i > 0) {
                    v = versions.get(i - 1).copy();
                    v.deltaTo(versions.get(i), false);
                } else {
                    v = versions.get(0);
                }
                v.save(sb);
                sb.writeToStream(fos);
                sb.clear();
            }
        }
    }

    public void remove(int amount) {
        if (amount <= 0 || amount > index + 1)
            throw new IllegalStateException();
        index -= amount; // index = 1, amount = 1
        if (amount == versions.size()) {
            versions.clear();
            return;
        }

        Ver last = versions.remove(0);
        while (true) {
            last.deltaTo(versions.get(0), true);
            if (--amount == 0) {
                versions.set(0, last);
                break;
            }
            versions.remove(0);
        }
    }

    public void add() throws IOException {
        if (index != versions.size() - 1)
            throw new IllegalStateException("Only support linear version");
        Ver v;
        if (versions.isEmpty()) {
            v = new Ver(this);
            v.readFresh();
        } else {
            v = versions.get(versions.size() - 1).copy();
            v.deltaToCur(true, true);

            Ver copy = versions.get(versions.size() - 1).copy();
            copy.deltaTo(v, false);
            System.out.println(copy);
            System.out.println("index " + (index + 1));
        }
        versions.add(v);
        index++;
    }

    public void set(int version) throws IOException {
        if (index == version) return;
        Ver v = patch(index, version);
        System.out.println("更新量: " + v);
        v.apply();
        index = version;
    }

    public int get() {
        return index;
    }

    public int size() {
        return versions.size();
    }

    public Ver patch(int from, int to) {
        if (from == to) return null;
        Vec2i x = new Vec2i(from, to);
        Ver delta = patches.get(x);
        if (delta == null) {
            Ver fr = versions.get(from);
            Ver tx = versions.get(to);
            delta = fr.copy();
            delta.deltaTo(tx, false);
            patches.put(x, delta);
        }
        return delta;
    }

    public Ver get(int v) {
        return versions.get(v);
    }

    public void setFilter(Predicate<File> filter) {
        this.filter = filter;
    }

    List<File> getFiles() {
        return FileUtil.findAllFiles(basePath, filter);
    }
}
