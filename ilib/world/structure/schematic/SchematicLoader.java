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

package ilib.world.structure.schematic;

import ilib.ImpLib;
import ilib.util.NBTType;
import ilib.util.Registries;
import roj.collect.IntBiMap;
import roj.collect.IntMap;
import roj.io.IOUtil;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class SchematicLoader {
    public static final SchematicLoader INSTANCE = new SchematicLoader();

    private SchematicLoader() {
    }

    public Schematic loadSchematic(File file) {
        try {
            return this.loadSchematic(new FileInputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
            ImpLib.logger().fatal("Error loading schematic at: " + file);
            return null;
        }
    }

    public Schematic loadSchematic(String path) {
        try {
            return this.loadSchematic(new ByteArrayInputStream(IOUtil.read(ImpLib.class, path)));
        } catch (IOException e) {
            e.printStackTrace();
            ImpLib.logger().fatal("Error loading schematic at: " + path);
            return null;
        }
    }

    public Schematic loadSchematic(InputStream is) {
        try {
            NBTTagCompound tag = CompressedStreamTools.readCompressed(is);
            is.close();
            return this.loadModSchematic(tag);
        } catch (IOException e) {
            e.printStackTrace();
            ImpLib.logger().fatal("Error loading schematic.");
            return null;
        }
    }

    public Schematic loadModSchematic(NBTTagCompound nbt) {
        try {
            NBTTagList tileEntities = nbt.getTagList("TileEntities", 10);
            NBTTagList entities = nbt.hasKey("Entities", 10) ? nbt.getTagList("Entities", 10) : null;

            int width = nbt.getShort("Width") & 0xFFFF;
            int height = nbt.getShort("Height") & 0xFFFF;
            int length = nbt.getShort("Length") & 0xFFFF;

            byte[] metadata = nbt.getByteArray("Data");
            int[] blockArray = nbt.getIntArray("Blocks");

            Block[] blocks = new Block[width * height * length];

            IntMap<Block> idMap = new IntMap<>();
            idMap.put(-1, Blocks.AIR);

            int i;
            NBTTagList blockData = nbt.getTagList("BlockData", NBTType.COMPOUND.ordinal());
            for (i = 0; i < blockData.tagCount(); ++i) {
                NBTTagCompound tag = blockData.getCompoundTagAt(i);
                idMap.put(tag.getInteger("p"), Registries.block().getValue(new ResourceLocation(tag.getString("m"), tag.getString("b"))));
            }

            for (i = 0; i < blockArray.length; ++i) {
                blocks[i] = idMap.get(blockArray[i]);
            }

            return new Schematic(entities, tileEntities, (short) width, (short) height, (short) length, metadata, blocks);
        } catch (Exception e) {
            e.printStackTrace();
            ImpLib.logger().fatal("Error loading ModSchematic.");
            return null;
        }
    }

    public NBTTagCompound writeSchematic(World w, BlockPos pos, BlockPos length) {
        SchematicToWrite s = new SchematicToWrite((short) length.getX(), (short) length.getY(), (short) length.getZ());
        s.readFrom(w, pos, false);
        return writeSchematic(s);
    }

    public NBTTagCompound writeSchematic(SchematicToWrite schematic) {
        NBTTagCompound nbt = new NBTTagCompound();

        nbt.setShort("Width", (short) schematic.width());
        nbt.setShort("Height", (short) schematic.height());
        nbt.setShort("Length", (short) schematic.length());

        nbt.setByteArray("Data", schematic.data);
        nbt.setTag("TileEntities", schematic.tileDataList);

        if (schematic.entityDataList.tagCount() > 0)
            nbt.setTag("Entities", schematic.entityDataList);

        int[] blocks = new int[schematic.blocks.length];

        NBTTagList blockPalette = new NBTTagList();

        IntBiMap<Block> map = new IntBiMap<>();

        for (int i = 0; i < schematic.blocks.length; ++i) {
            Block block = schematic.blocks[i];
            if (block != Blocks.AIR) {
                if (!map.containsValue(block)) {
                    ResourceLocation rl = Registries.block().getKey(block);
                    if (rl == null) {
                        blocks[i] = -1;
                        continue;
                    }

                    NBTTagCompound tag = new NBTTagCompound();

                    int ID = map.size();
                    tag.setInteger("p", ID);
                    map.put(ID, block);
                    tag.setString("m", rl.getNamespace());
                    tag.setString("b", rl.getPath());
                    blockPalette.appendTag(tag);
                }

                blocks[i] = map.getInt(block);
            } else {
                blocks[i] = -1;
            }
        }

        nbt.setIntArray("Blocks", blocks);
        nbt.setTag("BlockData", blockPalette);
        return nbt;
    }
}