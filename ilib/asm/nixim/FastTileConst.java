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
import ilib.asm.util.TileEntityCreator;
import ilib.util.freeze.FreezedTileEntity;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashMap;
import roj.reflect.DirectAccessor;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.FMLLog;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/20 17:56
 */
@Nixim("net.minecraft.tileentity.TileEntity")
abstract class FastTileConst extends TileEntity {
    @Copy
    public static Map<String, ilib.asm.util.TileEntityCreator> tileEntityCreator;

    @Shadow("field_190562_f")
    private static RegistryNamespaced<ResourceLocation, Class<? extends TileEntity>> REGISTRY;

    @Inject("func_190560_a")
    public static void register(String id, Class<? extends TileEntity> clazz) {
        if (tileEntityCreator == null) {
            tileEntityCreator = new MyHashMap<>();
        }
        ResourceLocation loc = new ResourceLocation(id);

        REGISTRY.putObject(loc, clazz);
    }

    @Copy
    private static ilib.asm.util.TileEntityCreator createSupplierFor(Class<? extends TileEntity> clazz) {
        return DirectAccessor.builder(TileEntityCreator.class)
                             .construct(clazz, "get")
                             .build();
    }

    @Inject("func_190200_a")
    public static TileEntity create(World worldIn, NBTTagCompound compound) {
        TileEntity tile = null;
        String id = compound.getString("id");
        TileEntityCreator supplier;

        if (Config.disableTileEntities.contains(id)) {
            ImpLib.logger().warn("已根据配置文件 *删除* 了位于 " + compound.getInteger("x") + ',' + compound.getInteger("y") + ',' + compound.getInteger("z") + " 的实体的所有数据!.");
            return null;
        }

        try {
            supplier = tileEntityCreator.get(id);
            if (supplier == null) {
                Class<? extends TileEntity> tileCz = REGISTRY.getObject(new ResourceLocation(id));
                if (tileCz != null) {
                    tileEntityCreator.put(id, supplier = createSupplierFor(tileCz));
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
