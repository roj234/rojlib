package ilib.fluid;

import ilib.ImpLib;
import ilib.Register;
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
	public static final ResourceLocation LAVA_STILL = new ResourceLocation(ImpLib.MODID, "fluid/base_lava"), LAVA_FLOW = new ResourceLocation(ImpLib.MODID,
																																			  "fluid/base_lava_flow"), WATER_STILL = new ResourceLocation(
		ImpLib.MODID, "fluid/base_water"), WATER_FLOW = new ResourceLocation(ImpLib.MODID, "fluid/base_water_flow");

	public static Fluid registerFluid(String name, FluidBuilder builder) {
		Fluid prev = FluidRegistry.getFluid(name);
		if (prev != null) {
			ImpLib.logger().info("Fluid " + name + " has been registered.");
			return prev;
		}

		Fluid fluid = builder.fluid(name);

		FluidRegistry.registerFluid(fluid);
		FluidRegistry.addBucketForFluid(fluid);

		BlockFluidBase block = new BlockFluidClassic(fluid, builder.material);
		fluid.setBlock(block);

		Register.registerFluidBlock(name, block);
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

		BlockFluidBase block = new BlockFluidClassic(fluid, builder.material);
		fluid.setBlock(block);

		Register.registerFluidBlock(name, block);
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

		public FluidBuilder color(@Nullable Integer color) {
			this.color = color;
			return this;
		}

		public FluidBuilder material(Material material) {
			this.material = material;
			return this;
		}

		public FluidBuilder sound(SoundEvent fill, SoundEvent drain) {
			this.soundFill = fill;
			this.soundDrain = drain;
			return this;
		}

		public FluidBuilder gas(boolean e) {
			this.isGas = e;
			return this;
		}

		public FluidBuilder texture(ResourceLocation still, ResourceLocation flow) {
			this.locStill = still;
			this.locFlow = flow;
			return this;
		}

		public FluidBuilder density(int number) {
			this.density = number;
			return this;
		}

		public FluidBuilder viscosity(int number) {
			this.viscosity = number;
			return this;
		}

		public FluidBuilder light(int number) {
			this.luminosity = number;
			return this;
		}

		public FluidBuilder temp(int number) {
			this.temperature = number;
			return this;
		}

		public FluidBuilder water() {
			return material(Material.WATER).light(0)
										   .temp(295)
										   .density(1000)
										   .viscosity(1000)
										   .gas(false)
										   .sound(SoundEvents.ITEM_BUCKET_FILL, SoundEvents.ITEM_BUCKET_EMPTY)
										   .texture(WATER_STILL, WATER_FLOW);
		}

		public FluidBuilder lava() {
			return material(Material.LAVA).light(15)
										  .temp(1300)
										  .density(3000)
										  .viscosity(3000)
										  .gas(false)
										  .sound(SoundEvents.ITEM_BUCKET_FILL_LAVA, SoundEvents.ITEM_BUCKET_EMPTY_LAVA)
										  .texture(LAVA_STILL, LAVA_FLOW);
		}

		public boolean hasColor() {
			return this.color != null;
		}

		public Fluid fluid(Fluid f, @Nonnull String name) {
			return f.setUnlocalizedName(ForgeUtil.getCurrentModId() + '.' + name)
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
			return fluid(new Fluid(name, locStill, locFlow), name);
		}
	}
}
