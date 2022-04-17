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
package ilib.misc;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EntityTransform {
    private Entity entity;
    private double x, y, z;
    private float yaw, pitch;

    public EntityTransform(Entity entity, double x, double y, double z, float yaw, float pitch) {
        this.entity = entity;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void apply() {
        if (entity.world.isRemote) {
            applyClient();
        } else {
            applyMain();
        }
    }

    //This fixes the hand bouncing around when you look around
    @SideOnly(Side.CLIENT)
    private void applyClient() {
        float prevYaw = 0.0F;
        float yaw = 0.0F;
        float prevPitch = 0.0F;
        float pitch = 0.0F;

        if (entity instanceof EntityPlayerSP) {
            EntityPlayerSP player = (EntityPlayerSP) entity;
            prevYaw = player.prevRotationYaw - player.prevRenderArmYaw;
            yaw = player.rotationYaw - player.renderArmYaw;
            prevPitch = player.prevRotationPitch - player.prevRenderArmPitch;
            pitch = player.rotationPitch - player.renderArmPitch;
        }

        applyMain();

        if (entity instanceof EntityPlayerSP) {
            EntityPlayerSP player = (EntityPlayerSP) entity;
            player.prevRenderArmYaw = player.prevRotationYaw;
            player.renderArmYaw = player.rotationYaw;
            player.prevRenderArmPitch = player.prevRotationPitch;
            player.renderArmPitch = player.rotationPitch;
            player.prevRenderArmYaw -= prevYaw;
            player.renderArmYaw -= yaw;
            player.prevRenderArmPitch -= prevPitch;
            player.renderArmPitch -= pitch;
        }
    }

    private void applyMain() {
        Entity entity = this.entity;
        entity.posX += x;
        entity.posY += y;
        entity.posZ += z;
        entity.prevPosX += x;
        entity.prevPosY += y;
        entity.prevPosZ += z;
        entity.lastTickPosX += x;
        entity.lastTickPosY += y;
        entity.lastTickPosZ += z;

        entity.rotationPitch += pitch;
        entity.rotationYaw += yaw;
        entity.prevRotationPitch += pitch;
        entity.prevRotationYaw += yaw;

        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            living.rotationYawHead += yaw;
            living.prevRotationYawHead += yaw;
        }
    }

    public void revert() {
        if (entity.world.isRemote) {
            revertClient();
        } else {
            revertMain();
        }
    }

    //This fixes the hand bouncing around when you look around
    @SideOnly(Side.CLIENT)
    private void revertClient() {
        float prevYaw = 0.0F;
        float yaw = 0.0F;
        float prevPitch = 0.0F;
        float pitch = 0.0F;

        if (entity instanceof EntityPlayerSP) {
            EntityPlayerSP player = (EntityPlayerSP) entity;
            prevYaw = player.prevRotationYaw - player.prevRenderArmYaw;
            yaw = player.rotationYaw - player.renderArmYaw;
            prevPitch = player.prevRotationPitch - player.prevRenderArmPitch;
            pitch = player.rotationPitch - player.renderArmPitch;
        }

        revertMain();

        if (entity instanceof EntityPlayerSP) {
            EntityPlayerSP player = (EntityPlayerSP) entity;
            player.prevRenderArmYaw = player.prevRotationYaw;
            player.renderArmYaw = player.rotationYaw;
            player.prevRenderArmPitch = player.prevRotationPitch;
            player.renderArmPitch = player.rotationPitch;
            player.prevRenderArmYaw -= prevYaw;
            player.renderArmYaw -= yaw;
            player.prevRenderArmPitch -= prevPitch;
            player.renderArmPitch -= pitch;
        }
    }

    private void revertMain() {
        Entity entity = this.entity;
        entity.posX -= x;
        entity.posY -= y;
        entity.posZ -= z;
        entity.prevPosX -= x;
        entity.prevPosY -= y;
        entity.prevPosZ -= z;
        entity.lastTickPosX -= x;
        entity.lastTickPosY -= y;
        entity.lastTickPosZ -= z;

        entity.rotationPitch -= pitch;
        entity.rotationYaw -= yaw;
        entity.prevRotationPitch -= pitch;
        entity.prevRotationYaw -= yaw;

        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            living.rotationYawHead -= yaw;
            living.prevRotationYawHead -= yaw;
        }
    }
}