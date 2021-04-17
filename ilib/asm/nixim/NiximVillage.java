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

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.ToIntMap;

import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.village.VillageDoorInfo;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/23 12:26
 */
@Nixim("net.minecraft.village.Village")
public class NiximVillage extends Village {
    @Inject("<init>")
    public NiximVillage() {
        super();
        this.playerReputation = new ToIntMap<>();
    }

    @Inject("<init>")
    public NiximVillage(World worldIn) {
        super(worldIn);
        this.playerReputation = new ToIntMap<>();
    }

    @Shadow("field_75588_h")
    int numVillagers;
    @Shadow("field_75587_j")
    int numIronGolems;
    @Shadow("field_75583_e")
    int villageRadius;
    @Shadow("field_75586_a")
    World world;
    @Shadow("field_75582_d")
    BlockPos center;
    @Shadow("field_82693_j")
    Map<UUID, Integer> playerReputation;
    @Shadow("field_75584_b")
    List<VillageDoorInfo> villageDoorInfoList;
    @Shadow("field_75585_c")
    BlockPos centerHelper;

    @Copy
    AxisAlignedBB cacheBox;

    @Inject("func_75572_i")
    private void updateNumVillagers() {
        if (cacheBox == null) {
            cacheBox = new AxisAlignedBB(this.center.getX() - this.villageRadius, this.center.getY() - 4, this.center.getZ() - this.villageRadius,
                    this.center.getX() + this.villageRadius, this.center.getY() + 4, this.center.getZ() + this.villageRadius);
        }

        List<EntityVillager> list = this.world.getEntitiesWithinAABB(EntityVillager.class, cacheBox);
        this.numVillagers = list.size();
        if (this.numVillagers == 0) {
            this.playerReputation.clear();
        }
    }

    @Inject("func_75579_h")
    private void updateNumIronGolems() {
        if (cacheBox == null) {
            cacheBox = new AxisAlignedBB(this.center.getX() - this.villageRadius, this.center.getY() - 4, this.center.getZ() - this.villageRadius,
                    this.center.getX() + this.villageRadius, this.center.getY() + 4, this.center.getZ() + this.villageRadius);
        }

        List<EntityIronGolem> list = this.world.getEntitiesWithinAABB(EntityIronGolem.class, cacheBox);
        this.numIronGolems = list.size();
    }

    @Inject("func_75573_l")
    private void updateVillageRadiusAndCenter() {
        int i = this.villageDoorInfoList.size();
        if (i == 0) {
            this.center = BlockPos.ORIGIN;
            this.villageRadius = 0;
        } else {
            this.center = new BlockPos(this.centerHelper.getX() / i, this.centerHelper.getY() / i, this.centerHelper.getZ() / i);
            int dist = 0;

            List<VillageDoorInfo> list = this.villageDoorInfoList;
            for (int j = 0, doorInfoListSize = list.size(); j < doorInfoListSize; j++) {
                VillageDoorInfo info = list.get(j);
                dist = Math.max(info.getDistanceToDoorBlockSq(this.center), dist);
            }

            this.villageRadius = Math.max(32, (int) Math.sqrt(dist) + 1);
        }

        this.cacheBox = null;

    }
}
