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
package roj.fbt;

import roj.collect.LinkedMyHashMap;
import roj.fbt.result.TagCompound;
import roj.fbt.result.TagResult;
import roj.fbt.tags.Tag;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class FixedBinaryTag {
    private final Map<Tag, String> tagNames = new LinkedMyHashMap<>();
    private int length;

    public FixedBinaryTag() {
    }

    public FixedBinaryTag addTag(String name, Tag tag) {
        tagNames.put(tag, name);
        this.length += tag.getLength();
        return this;
    }

    private final ByteList list = new ByteList();

    private static final ByteReader reader = new ByteReader();

    public TagCompound read(byte[] bytes) {
        reader.refresh(list.setValue(bytes));
        return read(reader);
    }

    public TagCompound read(ByteReader reader) {
        TagCompound compound = new TagCompound(this);
        Map<String, TagResult> result = compound.getResultMap();
        for (Map.Entry<Tag, String> entry : tagNames.entrySet()) {
            result.put(entry.getValue(), entry.getKey().read(reader));
        }
        return compound;
    }

    public ByteList write(TagCompound compound) {
        list.clear();
        write(new ByteWriter(list), compound);
        return list;
    }

    public ByteWriter write(ByteWriter writer, TagCompound compound) {
        Map<String, TagResult> result = compound.getResultMap();
        for (Tag tag : tagNames.keySet()) {
            tag.write(writer, result.get(tagNames.get(tag)));
        }
        return writer;
    }

    public int length() {
        return length;
    }
}
