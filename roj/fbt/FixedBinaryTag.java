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
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: null.java
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
