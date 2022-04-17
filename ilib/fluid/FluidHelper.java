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
package ilib.fluid;

import ilib.ImpLib;
import ilib.Registry;
import ilib.util.ForgeUtil;

import net.minecraft.block.material.Material;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51java
 */
public class FluidHelper {
    public static final ResourceLocation
            LAVA_STILL = new ResourceLocation(ImpLib.MODID, "fluid/base_lava"),
            LAVA_FLOW = new ResourceLocation(ImpLib.MODID, "fluid/base_lava_flow"),
            WATER_STILL = new ResourceLocation(ImpLib.MODID, "fluid/base_water"),
            WATER_FLOW = new ResourceLocation(ImpLib.MODID, "fluid/base_water_flow");

    public static Fluid registerFluid(String name, FluidBuilder builder) {
        Fluid prev = FluidRegistry.getFluid(name);
        if (prev != null) {
            ImpLib.logger().info("Fluid " + name + " has been registered.");
            return prev;
        }

        Fluid fluid = builder.fluid(name);

        FluidRegistry.registerFluid(fluid);
        FluidRegistry.addBucketForFluid(fluid);

        BlockFluidBase block = builder.hasColor() ?
                new BlockFluidColorable(fluid, builder.material) :
                new BlockFluidClassic(fluid, builder.material);
        fluid.setBlock(block);

        Registry.registerFluidBlock(name, block);
        return fluid;
    }

    public static void registerFluid(String name, FluidBuilder builder, Fluid fluid) {
        Fluid prev = FluidRegistry.getFluid(name);
        if (prev != null) {
            ImpLib.logger().info("Fluid " + name + " has been registered.");
            return;
        }

        fluid = builder.fluid(fluid, name);

        FluidRegistry.registerFluid(fluid);
        FluidRegistry.addBucketForFluid(fluid);

        BlockFluidBase block = builder.hasColor() ?
                new BlockFluidColorable(fluid, builder.material) :
                new BlockFluidClassic(fluid, builder.material);
        fluid.setBlock(block);

        Registry.registerFluidBlock(name, block);
    }

    public static class FluidBuilder {
        protected Integer color;
        protected Material material;
        protected int density;
        protected int viscosity;
        protected int luminosity;
        protected int temperature;
        protected SoundEvent soundFill;
        protected SoundEvent soundDrain;
        protected boolean isGas;
        protected ResourceLocation locStill;
        protected ResourceLocation locFlow;

        public FluidBuilder setColor(@Nullable Integer number) {
            this.color = number;
            return this;
        }

        public FluidBuilder setMaterial(Material material) {
            this.material = material;
            return this;
        }

        public FluidBuilder setSound(SoundEvent fill, SoundEvent drain) {
            this.soundFill = fill;
            this.soundDrain = drain;
            return this;
        }

        public FluidBuilder setGas(boolean e) {
            this.isGas = e;
            return this;
        }

        public FluidBuilder setResource(ResourceLocation resStill, ResourceLocation resFlow) {
            this.locStill = resStill;
            this.locFlow = resFlow;
            return this;
        }

        public FluidBuilder setDensity(int number) {
            this.density = number;
            return this;
        }

        public FluidBuilder setViscosity(int number) {
            this.viscosity = number;
            return this;
        }

        public FluidBuilder setLight(int number) {
            this.luminosity = number;
            return this;
        }

        public FluidBuilder setTemper(int number) {
            this.temperature = number;
            return this;
        }

        public FluidBuilder setWater() {
            return setMaterial(Material.WATER).setLight(0).setTemper(295).setDensity(1000).setViscosity(1000).setGas(false).setSound(SoundEvents.ITEM_BUCKET_FILL, SoundEvents.ITEM_BUCKET_EMPTY).setResource(WATER_STILL, WATER_FLOW);
        }

        public FluidBuilder setLava() {
            return setMaterial(Material.LAVA).setLight(15).setTemper(1300).setDensity(3000).setViscosity(3000).setGas(false).setSound(SoundEvents.ITEM_BUCKET_FILL_LAVA, SoundEvents.ITEM_BUCKET_EMPTY_LAVA).setResource(LAVA_STILL, LAVA_FLOW);
        }

        public boolean hasColor() {
            return this.color != null;
        }

        public Fluid fluid(Fluid originFluid, @Nonnull String name) {
            return originFluid
                    .setUnlocalizedName(ForgeUtil.getCurrentModId() + '.' + name)
                    .setDensity(density)
                    .setViscosity(viscosity)
                    .setLuminosity(luminosity)
                    .setTemperature(temperature)
                    .setGaseous(isGas)
                    .setColor(color == null ? -1 : color)

                    .setFillSound(soundFill)
                    .setEmptySound(soundDrain);
        }

        public Fluid fluid(@Nonnull String name) {
            return new Fluid(name, locStill, locFlow)
                    .setUnlocalizedName(ForgeUtil.getCurrentModId() + '.' + name)
                    .setDensity(density)
                    .setViscosity(viscosity)
                    .setLuminosity(luminosity)
                    .setTemperature(temperature)
                    .setGaseous(isGas)
                    .setColor(color == null ? -1 : color)

                    .setFillSound(soundFill)
                    .setEmptySound(soundDrain);
        }
    }
}
