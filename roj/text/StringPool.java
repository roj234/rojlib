package roj.text;

import roj.collect.IntBiMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: StringPool.java
 */
public class StringPool {
    final IntBiMap<String> list = new IntBiMap<>();
    final List<String> ordered;

    public StringPool() {
        this.ordered = new ArrayList<>();
    }

    public StringPool(ByteReader reader) {
        int length = reader.readVarInt(false);
        String[] array = new String[length];
        this.ordered = Arrays.asList(array);
        for (int i = 0; i < length; i++) {
            array[i] = reader.readVString();
        }
    }

    public ByteWriter writePool(ByteWriter data) {
        data.writeVarInt(ordered.size(), false);
        for (String s : ordered) {
            data.writeVString(s);
        }
        return data;
    }

    public ByteWriter writeString(ByteWriter w, String string) {
        int id = list.getByValue(string);
        if (id == -1) {
            list.putByValue(id = list.size(), string);
            ordered.add(string);
        }
        return w.writeVarInt(id, false);
    }

    public String readString(ByteReader r) {
        return ordered.get(r.readVarInt(false));
    }

    public void writePool(OutputStream os) throws IOException {
        writePool(new ByteWriter(5 * ordered.size())).list.writeToStream(os);
    }

    public int size() {
        return ordered.size();
    }
}
