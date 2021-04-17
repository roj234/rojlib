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

package ilib.world.structure.cascading;

import ilib.world.structure.cascading.api.IGenerationData;
import ilib.world.structure.cascading.api.IStructure;
import ilib.world.structure.cascading.api.IStructureData;
import ilib.world.structure.cascading.api.StructureGroup;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 新一代结构生成系统
 *//**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class StructureMap {
    protected Map<String, StructureGroup> categories;
    protected String start;
    protected int maxDepth;

    /**
     * 创建一个
     *
     * @param start 起始结构
     * @param list  结构列表
     */
    public StructureMap(String start, StructureGroup... list) {
        this.categories = new HashMap<>();
        for (StructureGroup st : list) {
            categories.put(st.getName(), st);
        }
        this.start = start;
    }

    public StructureMap(String start, Collection<StructureGroup> list) {
        this.categories = new HashMap<>();
        for (StructureGroup st : list) {
            categories.put(st.getName(), st);
        }
        this.start = start;
    }

    /**
     * 在world的pos开始生成
     *
     * @param world world
     * @param loc   pos
     * @param rand  random
     */
    public void generate(World world, BlockPos loc, Random rand) {
        cascade(world, new GenerateContext(start, loc, rand), maxDepth);
    }

    /**
     * 内部递归
     *
     * @param world   world
     * @param context 上下文
     * @param deep    递归深度
     */
    protected void cascade(World world, GenerateContext context, int deep) {
        final String groupName = context.getGroupName();

        StructureGroup group = categories.get(groupName);
        if (group == null)
            throw new RuntimeException("Structure " + groupName + " was not found.");

        IStructureData sData = group.getStructures(world, context);

        if (sData == null)
            return;

        final IStructure structure = sData.getStructure();

        context.offset(structure);

        structure.generate(world, context);

        context.afterGenerate(structure);

        for (IGenerationData data : sData.getNearby()) {
            if (data != null) {
                GenerateContext.Snapshot snapshot = context.makeSnapShot(data);

                cascade(world, context, deep - 1);
                context.restore(snapshot);
            }
        }
    }

    public enum Direction {
        NS, WE, XY, UD;

        public EnumFacing[] toFacings() {
            switch (this) {
                case NS:
                    return new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH};
                case UD:
                    return EnumFacing.Plane.VERTICAL.facings();
                case WE:
                    return new EnumFacing[]{EnumFacing.WEST, EnumFacing.EAST};
                case XY:
                    return EnumFacing.HORIZONTALS;
            }
            throw new IllegalArgumentException();
        }
    }
}