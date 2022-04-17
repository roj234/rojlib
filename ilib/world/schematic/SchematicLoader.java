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

package ilib.world.schematic;

import ilib.ImpLib;
import ilib.util.NBTType;
import ilib.util.Registries;
import roj.collect.IntMap;
import roj.collect.ToIntMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class SchematicLoader {
    private final File file;
    private Schematic schematic;

    public SchematicLoader(File file) {
        this.file = file;
    }

    public Schematic getSchematic() {
        if (schematic == null) {
            schematic = load(file);
            schematic.name = file.getName();
        }
        return schematic;
    }

    public String getName() {
        return schematic == null ? file.getName() : schematic.name;
    }

    public static Schematic load(File file) {
        try {
            return load(new FileInputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
            ImpLib.logger().fatal("Error loading schematic at: " + file);
            return null;
        }
    }

    public static Schematic load(String path) {
        InputStream in = SchematicLoader.class.getResourceAsStream(path);
        if (in == null) return null;
        return load(in);
    }

    public static Schematic load(InputStream in) {
        try {
            NBTTagCompound tag = CompressedStreamTools.readCompressed(in);
            return deserialize(tag);
        } catch (Throwable e) {
            ImpLib.logger().error("Error loading schematic", e);
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
    }

    public static Schematic deserialize(NBTTagCompound nbt) {
        NBTTagList tiles = nbt.getTagList("TileEntities", 10);
        NBTTagList entities = nbt.hasKey("Entities", 10) ? nbt.getTagList("Entities", 10) : null;

        int width = nbt.getShort("Width") & 0xFFFF;
        int height = nbt.getShort("Height") & 0xFFFF;
        int length = nbt.getShort("Length") & 0xFFFF;

        byte[] metadata = nbt.getByteArray("Data");
        int[] blockArray = nbt.getIntArray("Blocks");

        Block[] blocks = new Block[width * height * length];

        IntMap<Block> map = new IntMap<>();
        map.put(-1, Blocks.AIR);

        NBTTagList palette = nbt.getTagList("BlockData", NBTType.COMPOUND);
        for (int i = 0; i < palette.tagCount(); ++i) {
            NBTTagCompound tag = palette.getCompoundTagAt(i);
            map.put(tag.getInteger("p"), Registries.block().getValue(new ResourceLocation(tag.getString("m"), tag.getString("b"))));
        }

        for (int i = 0; i < blockArray.length; ++i) {
            blocks[i] = map.get(blockArray[i]);
        }

        return new Schematic(entities, tiles, (short) width, (short) height, (short) length, metadata, blocks);
    }

    public static NBTTagCompound write(World w, BlockPos pos, BlockPos length) {
        SchematicToWrite s = new SchematicToWrite((short) length.getX(), (short) length.getY(), (short) length.getZ());
        s.readFrom(w, pos, false);
        return serialize(s);
    }

    public static NBTTagCompound serialize(SchematicToWrite s) {
        NBTTagCompound nbt = new NBTTagCompound();

        nbt.setShort("Width", (short) s.width());
        nbt.setShort("Height", (short) s.height());
        nbt.setShort("Length", (short) s.length());

        nbt.setByteArray("Data", s.data);
        if (s.tileTags.tagCount() > 0) nbt.setTag("TileEntities", s.tileTags);
        if (s.entityTags.tagCount() > 0) nbt.setTag("Entities", s.entityTags);

        Block[] blocks = s.blocks;
        int[] blockIds = new int[blocks.length];

        NBTTagList palette = new NBTTagList();
        ToIntMap<Block> map = new ToIntMap<>();

        for (int i = 0; i < blocks.length; ++i) {
            Block block = blocks[i];
            if (block != Blocks.AIR) {
                if (!map.containsKey(block)) {
                    ResourceLocation rl = Registries.block().getKey(block);
                    if (rl == null) {
                        blockIds[i] = -1;
                        continue;
                    }

                    NBTTagCompound tag = new NBTTagCompound();

                    int ID = map.size();
                    tag.setInteger("p", ID);
                    map.put(ID, block);
                    tag.setString("m", rl.getNamespace());
                    tag.setString("b", rl.getPath());
                    palette.appendTag(tag);
                }

                blockIds[i] = map.getInt(block);
            } else {
                blockIds[i] = -1;
            }
        }

        nbt.setIntArray("Blocks", blockIds);
        nbt.setTag("BlockData", palette);
        return nbt;
    }
}