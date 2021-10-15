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

import ilib.asm.util.MCHooks;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.command.CommandResultStats;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;

import java.util.List;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/3 1:03
 */
@Nixim("net.minecraft.entity.Entity")
public abstract class NiximRelAABB extends Entity {
    @Shadow("field_184244_h")
    private List<Entity> riddenByEntities;
    @Shadow("field_190534_ay")
    private int fire;
    @Shadow("field_83001_bt")
    private boolean invulnerable;
    @Shadow("field_174837_as")
    private CommandResultStats cmdResultStats;
    @Shadow("field_184236_aF")
    private Set<String> tags;
    @Shadow("customEntityData")
    private NBTTagCompound customEntityData;
    @Shadow("capabilities")
    private CapabilityDispatcher capabilities;

    public NiximRelAABB(World worldIn) {
        super(worldIn);
    }

    @Copy
    protected static NBTTagList newDoubleNBTList1(double a, double b, double c) {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagDouble(a));
        list.appendTag(new NBTTagDouble(b));
        list.appendTag(new NBTTagDouble(c));

        return list;
    }

    @Inject("func_189511_e")
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        try {
            MCHooks.writeAABB(this, tag);
            tag.setTag("Pos", newDoubleNBTList1(this.posX, this.posY, this.posZ));
            tag.setTag("Motion", newDoubleNBTList1(this.motionX, this.motionY, this.motionZ));
            tag.setTag("Rotation", this.newFloatNBTList(this.rotationYaw, this.rotationPitch));
            tag.setFloat("FallDistance", this.fallDistance);
            tag.setShort("Fire", (short) this.fire);
            tag.setShort("Air", (short) this.getAir());
            tag.setBoolean("OnGround", this.onGround);
            tag.setInteger("Dimension", this.dimension);
            tag.setBoolean("Invulnerable", this.invulnerable);
            tag.setInteger("PortalCooldown", this.timeUntilPortal);
            tag.setUniqueId("UUID", this.getUniqueID());
            if (this.hasCustomName()) {
                tag.setString("CustomName", this.getCustomNameTag());
            }

            if (this.getAlwaysRenderNameTag()) {
                tag.setBoolean("CustomNameVisible", this.getAlwaysRenderNameTag());
            }

            this.cmdResultStats.writeStatsToNBT(tag);
            if (this.isSilent()) {
                tag.setBoolean("Silent", true);
            }

            if (this.hasNoGravity()) {
                tag.setBoolean("NoGravity", true);
            }

            if (this.glowing) {
                tag.setBoolean("Glowing", true);
            }

            tag.setBoolean("UpdateBlocked", this.updateBlocked);

            if (!this.tags.isEmpty()) {
                NBTTagList list = new NBTTagList();

                for (String s : this.tags) {
                    list.appendTag(new NBTTagString(s));
                }

                tag.setTag("Tags", list);
            }

            if (this.customEntityData != null) {
                tag.setTag("ForgeData", this.customEntityData);
            }

            if (this.capabilities != null) {
                tag.setTag("ForgeCaps", this.capabilities.serializeNBT());
            }

            this.writeEntityToNBT(tag);
            if (this.isBeingRidden()) {
                NBTTagList list = new NBTTagList();

                for (Entity pas : this.riddenByEntities) {
                    NBTTagCompound tag1 = new NBTTagCompound();
                    if (pas.writeToNBTAtomically(tag1)) {
                        list.appendTag(tag1);
                    }
                }

                if (!list.isEmpty()) {
                    tag.setTag("Passengers", list);
                }
            }

            return tag;
        } catch (Throwable e) {
            CrashReport rpt = CrashReport.makeCrashReport(e, "Saving entity NBT");
            this.addEntityCrashInfo(rpt.makeCategory("Entity being saved"));
            throw new ReportedException(rpt);
        }
    }

    @Inject("func_70020_e")
    public void readFromNBT(NBTTagCompound tag) {
        try {
            NBTTagList pos = tag.getTagList("Pos", 6);
            this.posX = pos.getDoubleAt(0);
            this.posY = pos.getDoubleAt(1);
            this.posZ = pos.getDoubleAt(2);
            this.lastTickPosX = this.posX;
            this.lastTickPosY = this.posY;
            this.lastTickPosZ = this.posZ;
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;

            NBTTagList mot = tag.getTagList("Motion", 6);
            this.motionX = mot.getDoubleAt(0);
            if (Math.abs(this.motionX) > 10.0D) {
                this.motionX = 0.0D;
            }
            this.motionY = mot.getDoubleAt(1);
            if (Math.abs(this.motionY) > 10.0D) {
                this.motionY = 0.0D;
            }
            this.motionZ = mot.getDoubleAt(2);
            if (Math.abs(this.motionZ) > 10.0D) {
                this.motionZ = 0.0D;
            }

            NBTTagList rot = tag.getTagList("Rotation", 5);
            this.rotationYaw = rot.getFloatAt(0);
            this.rotationPitch = rot.getFloatAt(1);
            this.prevRotationYaw = this.rotationYaw;
            this.prevRotationPitch = this.rotationPitch;
            this.setRotationYawHead(this.rotationYaw);
            this.setRenderYawOffset(this.rotationYaw);

            this.fallDistance = tag.getFloat("FallDistance");
            this.fire = tag.getShort("Fire");
            this.setAir(tag.getShort("Air"));
            this.onGround = tag.getBoolean("OnGround");
            if (tag.hasKey("Dimension")) {
                this.dimension = tag.getInteger("Dimension");
            }

            this.invulnerable = tag.getBoolean("Invulnerable");
            this.timeUntilPortal = tag.getInteger("PortalCooldown");
            if (tag.hasUniqueId("UUID")) {
                this.entityUniqueID = tag.getUniqueId("UUID");
                this.cachedUniqueIdString = this.entityUniqueID.toString();
            }

            this.setPosition(this.posX, this.posY, this.posZ);
            this.setRotation(this.rotationYaw, this.rotationPitch);
            if (tag.hasKey("CustomName", 8)) {
                this.setCustomNameTag(tag.getString("CustomName"));
            }

            this.setAlwaysRenderNameTag(tag.getBoolean("CustomNameVisible"));
            this.cmdResultStats.readStatsFromNBT(tag);
            this.setSilent(tag.getBoolean("Silent"));
            this.setNoGravity(tag.getBoolean("NoGravity"));
            this.setGlowing(tag.getBoolean("Glowing"));
            this.updateBlocked = tag.getBoolean("UpdateBlocked");
            if (tag.hasKey("ForgeData")) {
                this.customEntityData = tag.getCompoundTag("ForgeData");
            }

            if (this.capabilities != null && tag.hasKey("ForgeCaps")) {
                this.capabilities.deserializeNBT(tag.getCompoundTag("ForgeCaps"));
            }

            if (tag.hasKey("Tags", 9)) {
                this.tags.clear();
                NBTTagList list = tag.getTagList("Tags", 8);
                int i = Math.min(list.tagCount(), 1024);

                for (int j = 0; j < i; ++j) {
                    this.tags.add(list.getStringTagAt(j));
                }
            }

            this.readEntityFromNBT(tag);
            if (this.shouldSetPosAfterLoading()) {
                this.setPosition(this.posX, this.posY, this.posZ);
            }
            MCHooks.readAABB(this, tag);

        } catch (Throwable var8) {
            CrashReport rpt = CrashReport.makeCrashReport(var8, "Loading entity NBT");
            this.addEntityCrashInfo(rpt.makeCategory("Entity being loaded"));
            throw new ReportedException(rpt);
        }
    }
}
