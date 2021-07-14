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

import ilib.util.BlockHelper;
import ilib.util.math.FastPath;
import ilib.util.math.PositionProvider;
import ilib.util.math.Section;
import ilib.world.structure.cascading.api.IGenerationData;
import ilib.world.structure.cascading.api.IStructure;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/19 21:03
 */
public class GenerateContext {
    /**
     * 当前结构
     */
    protected String nextGroup;
    /**
     * 已经生成的结构的位置列表, 防止碰撞
     */
    protected final FastPath<GenerationInfo> generatedPosition = new FastPath<>();
    protected final LinkedList<GenerationInfo> generated = new LinkedList<>();
    /**
     * random
     */
    protected Random rand;

    protected EnumFacing direction;
    protected GenerationInfo lastGenerated;

    public GenerateContext(GenerateContext context) {
        this.rand = context.getRand();
        this.generatedPosition.putAll(context.getGeneratedPosition());
        this.root = context.currPos.toImmutable();
    }

    protected void afterGenerate(IStructure structure) {
        Section box = structure.getSection(currPos);
        this.generatedPosition.put(lastGenerated = new GenerationInfo(box, structure));
        this.generated.add(lastGenerated);
    }


    /**
     * 获取下次生成的位置
     *
     * @param current 当前结构
     */
    protected void offset(@Nonnull IStructure current) {
        if (direction == null)
            return;
        switch (direction) {
            case UP:
                currPos.move(direction, current.getSection(BlockPos.ORIGIN).yLen());
                break;
            case DOWN:
                currPos.move(direction, lastGenerated.structure.getSection(BlockPos.ORIGIN).yLen());
                break;
            case EAST:
                currPos.move(direction, current.getSection(BlockPos.ORIGIN).xLen());
                break;
            case WEST:
                currPos.move(direction, lastGenerated.structure.getSection(BlockPos.ORIGIN).xLen());
                break;
            case SOUTH:
                currPos.move(direction, current.getSection(BlockPos.ORIGIN).zLen());
                break;
            case NORTH:
                currPos.move(direction, lastGenerated.structure.getSection(BlockPos.ORIGIN).zLen());
                break;
        }
    }

    /**
     * 生成原点
     */
    protected final BlockPos root;

    protected final BlockHelper.MutableBlockPos currPos = new BlockHelper.MutableBlockPos();

    public GenerateContext(String start, BlockPos loc, Random rand) {
        this.nextGroup = start;
        this.root = loc;
        this.rand = rand;
    }

    public List<GenerationInfo> getGeneratedStructures() {
        return Collections.unmodifiableList(this.generated);
    }

    public String getGroupName() {
        return nextGroup;
    }

    public FastPath<GenerationInfo> getGeneratedPosition() {
        return generatedPosition;
    }

    public Random getRand() {
        return rand;
    }

    public EnumFacing getDirection() {
        return direction;
    }

    @Nullable
    public GenerationInfo getLastGenerated() {
        return lastGenerated;
    }

    public BlockPos getRoot() {
        return root;
    }

    public BlockPos getCurrPos() {
        return currPos;
    }

    /**
     * 对于原点的offset
     */
    public void setCurrentGeneratePosition(int x, int y, int z) {
        currPos.setPos(root);
        currPos.add1(x, y, z);
    }

    /**
     * 防止新的结构"撞"进老的结构
     *
     * @param structure 准备生成的结构
     * @return 可以生成？
     */
    public boolean canStructureBeGenerated(IStructure structure) {
        return !generatedPosition.collidesWith(0, structure.getSection(currPos));
    }

    public Snapshot makeSnapShot(IGenerationData data) {
        Snapshot snapshot = new Snapshot(currPos, direction, lastGenerated, nextGroup);
        this.direction = data.getDirection();
        this.nextGroup = data.getNextGroup();
        this.currPos.setPos(data.getPos());
        return snapshot;
    }

    public void restore(Snapshot snapshot) {
        this.currPos.setPos(snapshot.pos);
        this.direction = snapshot.direction;
        this.lastGenerated = snapshot.data;
        this.nextGroup = snapshot.group;
    }

    public Section getMaxBox(BlockPos offset) {
        Section section = null;
        for (GenerationInfo data : this.generated) {
            if (section == null)
                section = data.section.copy();
            else
                section.merge(data.section);
        }

        return section.offset(offset);
    }


    public static class Snapshot {
        final BlockPos pos;
        final EnumFacing direction;
        final GenerationInfo data;
        final String group;

        public Snapshot(BlockPos pos, EnumFacing direction, GenerationInfo data, String group) {
            this.pos = pos.toImmutable();
            this.direction = direction;
            this.data = data;
            this.group = group;
        }
    }


    public static final class GenerationInfo implements PositionProvider {
        final Section section;
        public final IStructure structure;

        private GenerationInfo(Section section, IStructure structure) {
            this.section = section;
            this.structure = structure;
        }

        @Override
        public Section getSection() {
            return section;
        }

        @Override
        public int getWorld() {
            return 0;
        }

        @Override
        public boolean contains(BlockPos pos) {
            return section.contains(pos);
        }
    }
}
