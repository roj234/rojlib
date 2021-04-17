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
package ilib.client.renderer.mirror;

import ilib.ImpLib;
import ilib.network.ILChannel;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class MirrorSubSystem {
    public static int maxRecursion = 2;
    public static int maxRenderPerTick = maxRecursion;
    public static int maxRender = 0;
    public static int stencilValue = 1;

    public static ILChannel PORTAL = new ILChannel("prt");

    public static void init() {
        // config("maxRecursion", "stencilValue", "renderDistanceChunks", "maxRendersPerTick");

        MinecraftForge.EVENT_BUS.register(EventHandler.class);

        PORTAL.registerMessage((msg, ctx) -> {
            EntityPlayer player = ctx.getPlayer();
            Entity entity = player.getEntityWorld().getEntityByID(msg.id);

            final boolean isRemote = player.getEntityWorld().isRemote;

            if (entity != null && !(isRemote && player == entity)) {
                entity.setLocationAndAngles(msg.x, msg.y, msg.z, msg.yaw, msg.pitch);
                entity.motionX = msg.mX;
                entity.motionY = msg.mY;
                entity.motionZ = msg.mZ;
                if (!isRemote) {
                    if (entity instanceof EntityPlayerMP && entity == player) {
                        EntityPlayerMP mp = (EntityPlayerMP) entity;
                        mp.connection.lastPositionUpdate = mp.connection.networkTickCount;
                        mp.setPositionAndRotation(msg.x, msg.y, msg.z, msg.yaw, msg.pitch);
                        mp.motionX = msg.mX;
                        mp.motionY = msg.mY;
                        mp.motionZ = msg.mZ;
                    }
                    PORTAL.sendToAllTracking(msg, entity);
                } else {
                    float yawDifference = 0;
                    float prevYawDifference = 0;
                    EntityLivingBase living = null;
                    float riderYaw = 0;
                    float prevRiderYaw = 0;

                    if (entity instanceof EntityLivingBase) {
                        living = (EntityLivingBase) entity;
                        yawDifference = living.renderYawOffset - entity.rotationYaw;
                        prevYawDifference = living.prevRenderYawOffset - entity.rotationYaw;
                    }

                    for (Entity passenger : entity.getPassengers()) {
                        riderYaw = passenger.rotationYaw - entity.rotationYaw;
                        prevRiderYaw = passenger.prevRotationYaw - entity.rotationYaw;
                    }

                    entity.lastTickPosX = entity.prevPosX = msg.lX;
                    entity.lastTickPosY = entity.prevPosY = msg.lY;
                    entity.lastTickPosZ = entity.prevPosZ = msg.lZ;
                    entity.posX = msg.x;
                    entity.posY = msg.y;
                    entity.posZ = msg.z;
                    entity.prevRotationYaw = msg.prevYaw;
                    entity.prevRotationPitch = msg.prevPitch;
                    entity.rotationYaw = msg.yaw;
                    entity.rotationPitch = msg.pitch;
                    entity.setPosition(entity.posX, entity.posY, entity.posZ);

                    for (Entity passenger : entity.getPassengers()) {
                        entity.updatePassenger(passenger);
                        passenger.rotationYaw = passenger.prevRotationYaw = entity.rotationYaw;
                        passenger.rotationYaw += riderYaw;
                        passenger.prevRotationYaw += prevRiderYaw;
                    }

                    if (living != null) {
                        living.renderYawOffset = living.prevRenderYawOffset = entity.rotationYaw;
                        living.renderYawOffset += yawDifference;
                        living.prevRenderYawOffset += prevYawDifference;
                    } else if (entity instanceof EntityArrow) {
                        ((EntityArrow) entity).inGround = false;
                    }
                }
            }
        }, PktEntityData.class, 1, null);

        ImpLib.HOOK.add("ServerShutdown", EventHandler.monitoredEntities[1]::clear);
    }

    @SideOnly(Side.CLIENT)
    public static void initClient() {
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
    }
}
