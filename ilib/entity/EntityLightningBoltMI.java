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

package ilib.entity;

import net.minecraft.entity.Entity;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/15 13:32
 */
public class EntityLightningBoltMI extends Entity {
    public long boltVertex;
    private int boltLivingTime;
    private byte lightningState;

    public EntityLightningBoltMI(World world) {
        super(world);
        this.lightningState = 2;
        this.boltVertex = this.rand.nextLong();
        this.boltLivingTime = this.rand.nextInt(3) + 1;
    }

    public EntityLightningBoltMI(World world, double x, double y, double z) {
        this(world);
        setLocationAndAngles(x, y, z, 0.0F, 0.0F);
    }

    public void onUpdate() {
        super.onUpdate();
        if (this.lightningState == 2) {
            this.world.playSound(null, this.posX, this.posY, this.posZ, SoundEvents.ENTITY_LIGHTNING_THUNDER, SoundCategory.WEATHER, 10000.0F, 0.8F + this.rand.nextFloat() * 0.2F);
            this.world.playSound(null, this.posX, this.posY, this.posZ, SoundEvents.ENTITY_LIGHTNING_IMPACT, SoundCategory.WEATHER, 2.0F, 0.5F + this.rand.nextFloat() * 0.2F);
        }
        this.lightningState--;
        if (this.lightningState < 0) {
            if (this.boltLivingTime == 0) {
                setDead();
            } else if (this.lightningState < -this.rand.nextInt(10)) {
                this.boltLivingTime--;
                this.lightningState = 1;
                this.boltVertex = this.rand.nextLong();
            }
        }
        if (this.lightningState >= 0 && this.world.isRemote) {
            this.world.setLastLightningBolt(2);
        }
    }

    @Override
    protected void entityInit() {
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbtTagCompound) {
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbtTagCompound) {
    }
}