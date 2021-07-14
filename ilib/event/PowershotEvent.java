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
package ilib.event;

import ilib.ImpLib;
import ilib.util.ItemUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import roj.collect.ToDoubleMap;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/31 1:05
 */
public class PowershotEvent {
    public static final ToDoubleMap<String> enchantmentMultipliers = new ToDoubleMap<>();
    public static final ToDoubleMap<IBlockState> powerRequirement = new ToDoubleMap<>();

    public static class Power implements ICapabilityProvider {
        public double power;

        public Power() {}

        public Power(double power) {
            this.power = power;
        }

        public boolean hasCapability(@Nonnull Capability<?> cap, EnumFacing facing) {
            return cap == POWER;
        }

        @SuppressWarnings("unchecked")
        public <T> T getCapability(@Nonnull Capability<T> cap, EnumFacing facing) {
            if (cap == POWER) {
                return (T) this;
            }
            return null;
        }
    }

    public static final ResourceLocation POWER_ID = new ResourceLocation(ImpLib.MODID, "arrow_power");
    @CapabilityInject(Power.class)
    public static Capability<Power> POWER;

    public static void onImpactBlock(AttachCapabilitiesEvent<Entity> event) {
        final Entity entity = event.getObject();

        if(!entity.world.isRemote && entity instanceof EntityArrow) {
            double power = computePower((EntityArrow) entity);
            if(power == power)
                event.getCapabilities().put(POWER_ID, new Power(power));
        }
    }

    static double computePower(EntityArrow arrow) {
        Entity shooter = arrow.shootingEntity;

        if (shooter instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) shooter;
            ItemStack stack = living.getHeldItem(living.getActiveHand());

            if (!stack.isEmpty()) {
                double power = 0;
                NBTTagList enchs = stack.getEnchantmentTagList();
                for (int i = 0; i < enchs.tagCount(); i++) {
                    NBTTagCompound ench = enchs.getCompoundTagAt(i);
                    double mul = enchantmentMultipliers.getDouble(ench.getString("id"));
                    if (mul == mul)
                        power += MathHelper.clamp(ench.getInteger("lvl"), 0, 255) * mul;
                }
                return power;
            }
        }
        return Double.NaN;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onImpactBlock(ProjectileImpactEvent.Arrow event) {
        EntityArrow arrow = event.getArrow();
        Power powerCap = arrow.getCapability(POWER, null);
        if(arrow.world.isRemote || powerCap == null) return;

        RayTraceResult result = event.getRayTraceResult();

        if(result == null || result.getBlockPos() == null) {
            return;
        }

        BlockPos pos = result.getBlockPos();
        IBlockState state = arrow.world.getBlockState(pos);

        double require = powerRequirement.get(state);
        if (require == require && powerCap.power >= require) {
            ItemUtils.breakBlock(arrow.world, pos);
            event.setCanceled(true);
        }
    }
}
