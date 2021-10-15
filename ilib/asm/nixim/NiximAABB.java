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

import com.google.common.base.Predicate;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import ilib.Config;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.world.World")
public abstract class NiximAABB extends World {
    @Inject("<init>")
    /**
     * 1. 如果不使用SIJ，这个通不过编译，因为没法调用World的上级<init>除非参数一样
     *   也就是说， 没法replace构造器？
     *   这没有问题
     *   还防止了一些问题
     * 2. 使用SIJ，
     *   在构造器后！后！后！
     *   插入一段代码
     *   在之前... 不用判断，无法编译除非调用static，预处理参数？
     *   那么怎么处理构造器
     *     1. StdSIJ,
     *     2. 把SIJ后的第一个InvokeSpecial之前(包括)的所有bc
     *     移动到第一个invokevirtual之前
     *     3. 因为参数一样... 不对哦，可能不一样
     *     3. 按顺序重排IS之前xLOAD_X的id
     *     4. 最开始的按顺序用xSTORE_X
     */
    protected NiximAABB(ISaveHandler p_i45749_1_, WorldInfo p_i45749_2_, WorldProvider p_i45749_3_, Profiler p_i45749_4_, boolean p_i45749_5_) {
        super(p_i45749_1_, p_i45749_2_, p_i45749_3_, p_i45749_4_, p_i45749_5_);
        this.boxCache = CacheBuilder.newBuilder().expireAfterAccess(Config.aabbCache, TimeUnit.MILLISECONDS).initialCapacity(512).maximumSize(8192).weakValues().concurrencyLevel(1).build();
        this.eeCache = CacheBuilder.newBuilder().expireAfterAccess(Config.aabbCache, TimeUnit.MILLISECONDS).initialCapacity(512).maximumSize(8192).weakValues().concurrencyLevel(1).build();
    }

    @Copy
    public Cache<AxisAlignedBB, List<AxisAlignedBB>> boxCache;

    @Copy
    public Cache<AxisAlignedBB, List<Entity>> eeCache;

    @Inject("func_184144_a")
    public List<AxisAlignedBB> getCollisionBoxes_patch(Entity entity, AxisAlignedBB bb) {
        List<AxisAlignedBB> list = this.boxCache.getIfPresent(bb);
        if (list == null) {
            list = super.getCollisionBoxes(entity, bb);
            this.boxCache.put(bb, list);
        }
        return list;
    }

    @Inject("func_175674_a")
    public List<Entity> getEntitiesInAABBexcluding_Patch(Entity entityIn, AxisAlignedBB boundingBox, Predicate<? super Entity> predicate) {
        List<Entity> entities = this.eeCache.getIfPresent(boundingBox);
        if (entities != null) {
            if (!entities.isEmpty()) {
                if (predicate != null)
                    for (Entity entity : entities) {
                        if (!predicate.apply(entity)) {
                            entities = null;
                            break;
                        }
                    }
                if (entities != null)
                    return entities;
            }
        }
        entities = super.getEntitiesInAABBexcluding(entityIn, boundingBox, predicate);
        this.eeCache.put(boundingBox, entities);
        return entities;
    }

    @Inject("func_175647_a")
    @SuppressWarnings("unchecked")
    public <T extends Entity> List<T> performant_getEntitiesWithinAABB(Class<? extends T> clazz, AxisAlignedBB aabb, Predicate<? super T> filter) {
        List<T> entities = (List<T>) this.eeCache.getIfPresent(aabb);
        if (entities != null) {
            if (!entities.isEmpty()) {
                if (filter != null)
                    for (T entity : entities) {
                        if (!clazz.isInstance(entity)) {
                            break;
                        }
                        if (!filter.apply(entity)) {
                            entities = null;
                            break;
                        }
                    }
                if (entities != null)
                    return entities;
            }
        }
        entities = super.getEntitiesWithinAABB(clazz, aabb, filter);
        this.eeCache.put(aabb, (List<Entity>) entities);
        return entities;
    }
}