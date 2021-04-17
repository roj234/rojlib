/*
 * This file is a part of MoreItems
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
package ilib.util.freeze;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;

/**
 * 冻结的实体
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/26 19:36
 */
public final class FreezedEntity extends Entity {
    public NBTTagCompound tag, tag1;

    public FreezedEntity(World worldIn) {
        super(worldIn);
    }

    @Override
    protected void entityInit() {}

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        try {
            NBTTagList poses = compound.getTagList("Pos", 6);
            this.posX = poses.getDoubleAt(0);
            this.posY = poses.getDoubleAt(1);
            this.posZ = poses.getDoubleAt(2);
            this.lastTickPosX = this.posX;
            this.lastTickPosY = this.posY;
            this.lastTickPosZ = this.posZ;
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;

            NBTTagList rotations = compound.getTagList("Rotation", 5);
            this.rotationYaw = rotations.getFloatAt(0);
            this.rotationPitch = rotations.getFloatAt(1);
            this.prevRotationYaw = this.rotationYaw;
            this.prevRotationPitch = this.rotationPitch;
            this.setRotationYawHead(this.rotationYaw);
            this.setRenderYawOffset(this.rotationYaw);

            if (compound.hasKey("Dimension")) {
                this.dimension = compound.getInteger("Dimension");
            }

            if (compound.hasUniqueId("UUID")) {
                this.entityUniqueID = compound.getUniqueId("UUID");
                this.cachedUniqueIdString = this.entityUniqueID.toString();
            }

            this.setEntityInvulnerable(true);
            this.setPosition(this.posX, this.posY, this.posZ);
            this.setRotation(this.rotationYaw, this.rotationPitch);

            this.setCustomNameTag("冻结的实体 " + compound.getString("id") + " == " + this.cachedUniqueIdString);
            this.setAlwaysRenderNameTag(true);
            this.setNoGravity(true);
            this.setGlowing(true);
            this.updateBlocked = true;
        } catch (Throwable var8) {
            new RuntimeException("无法加载实体NBT数据 " + compound.getString("id"), var8).printStackTrace();
        }
    }

    @Override
    public void setDead() {}

    @Override
    public void onKillCommand() {}

    @Override
    public void onEntityUpdate() {
        this.firstUpdate = false;
    }

    @Override
    public boolean writeToNBTAtomically(NBTTagCompound compound) {
        String s = this.getEntityString();
        if (s != null) {
            compound.setString("id", s);
            this.writeToNBT(compound);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        String s = this.getEntityString();
        if (s != null && !this.isRiding()) {
            compound.setString("id", s);
            this.writeToNBT(compound);
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    @Override
    public Entity changeDimension(int dimensionIn, ITeleporter teleporter) {
        return null;
    }

    @Override
    public void applyEntityCollision(Entity entityIn) {}

    @Override
    public EnumActionResult applyPlayerInteraction(EntityPlayer player, Vec3d vec, EnumHand hand) {
        return EnumActionResult.FAIL;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if(this.tag == null) {
            return tag;
        }
        for(String key : this.tag.getKeySet()) {
            tag.setTag(key, this.tag.getTag(key));
        }
        return tag;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        this.tag1 = tag;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        if(this.tag1 == null) {
            return;
        }
        for(String key : this.tag1.getKeySet()) {
            tag.setTag(key, this.tag1.getTag(key));
        }
    }
}
