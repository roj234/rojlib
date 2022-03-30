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
package ilib.asm.nixim;

import ilib.Config;
import ilib.ImpLib;
import ilib.asm.util.MCHooks;
import ilib.asm.util.TileEntityCreator;
import ilib.util.freeze.FreezedTileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLLog;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashMap;
import roj.collect.ToIntMap;
import roj.reflect.DirectAccessor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

/**
 * @author Roj234
 * @since  2020/8/20 17:56
 */
@Nixim("net.minecraft.tileentity.TileEntity")
abstract class FastTileConst extends TileEntity {
    @Copy
    static Map<String, ilib.asm.util.TileEntityCreator> tileEntityCreator;
    @Copy(staticInitializer = "initTC")
    static RandomAccessFile tileCache;

    static void initTC() {
        tileEntityCreator = new MyHashMap<>();
        try {
            tileCache = new RandomAccessFile("Implib_FTC.bin", "rw");
            ToIntMap<String> map = new ToIntMap<>();
            TileEntityCreator creator = (TileEntityCreator) MCHooks.batchGenerate(tileCache, false, map);
            if (creator != null) {
                for (ToIntMap.Entry<String> entry : map.selfEntrySet()) {
                    TileEntityCreator c = (TileEntityCreator) creator.clone();
                    c.setId(entry.v);
                    tileEntityCreator.put(entry.k, c);
                }
                System.out.println("使用BatchGen节省了 " + map.size() + " 个无用的class");
            }
            tileCache.seek(0);
            tileCache.writeInt(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Shadow("field_190562_f")
    private static RegistryNamespaced<ResourceLocation, Class<? extends TileEntity>> REGISTRY;

    @Inject("func_190560_a")
    public static void register(String id, Class<? extends TileEntity> clazz) {
        if (tileCache != null) {
            try {
                MCHooks.batchAdd(tileCache, id, clazz);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        REGISTRY.putObject(new ResourceLocation(id), clazz);
    }

    @Inject("func_190200_a")
    public static TileEntity create(World worldIn, NBTTagCompound compound) {
        String id = compound.getString("id");

        if (Config.disableTileEntities.contains(id)) {
            ImpLib.logger().warn("已根据配置文件 *删除* 了位于 " + compound.getInteger("x") + ',' + compound.getInteger("y") + ',' + compound.getInteger("z") + " 的实体的所有数据!.");
            return null;
        }

        TileEntity tile = null;
        try {
            TileEntityCreator supplier = tileEntityCreator.get(id);
            if (supplier == null) {
                Class<? extends TileEntity> tileCz = REGISTRY.getObject(new ResourceLocation(id));
                if (tileCz != null) {
                    tileEntityCreator.put(id, supplier = DirectAccessor.builder(TileEntityCreator.class)
                                                                       .construct(tileCz, "get")
                                                                       .build());
                }
            }
            if (supplier != null) {
                tile = supplier.get();
                if (tile == null)
                    throw new InternalError(supplier.toString());
            } else {
                if(Config.freezeUnknownEntries.contains("tile")) {
                    FMLLog.log.debug("冻结不存在的Tile {}", id);
                    return new FreezedTileEntity(compound);
                }
                //System.out.println("未知TileEntityId: " + id);
            }
        } catch (Throwable var7) {
            FMLLog.log.error("一个 Tile {}({}) 无法加载NBT数据, 这是一个bug!", id, getTEClass(id), var7);
        }

        if (tile != null) {
            try {
                tile.setWorldCreate(worldIn);
                tile.readFromNBT(compound);
            } catch (Throwable var6) {
                FMLLog.log.error("一个 Tile {}({}) 无法加载NBT数据, 这是一个bug!", id, getTEClass(id), var6);
                tile = null;
            }
        } else {
            FMLLog.log.warn("跳过不存在的Tile {}", id);
        }

        return tile;
    }

    @Copy
    private static String getTEClass(String id) {
        Class<? extends TileEntity> clazz = REGISTRY.getObject(new ResourceLocation(id));
        return clazz == null ? "null" : clazz.getName();
    }
}
