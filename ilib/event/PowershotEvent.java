package ilib.event;

import ilib.ImpLib;
import roj.collect.ToDoubleMap;

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

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/5/31 1:05
 */
public class PowershotEvent implements ICapabilityProvider {
	public static final ToDoubleMap<String> enchantmentMultipliers = new ToDoubleMap<>();
	public static final ToDoubleMap<IBlockState> powerRequirement = new ToDoubleMap<>();

	private final double power;

	private PowershotEvent(double power) {
		this.power = power;
	}

	public boolean hasCapability(@Nonnull Capability<?> cap, EnumFacing facing) {
		return cap == POWER;
	}

	@SuppressWarnings("unchecked")
	public <T> T getCapability(@Nonnull Capability<T> cap, EnumFacing facing) {
		if (cap == POWER) return (T) this;
		return null;
	}

	public static final ResourceLocation POWER_ID = new ResourceLocation(ImpLib.MODID, "a_power");

	@CapabilityInject(PowershotEvent.class)
	public static Capability<PowershotEvent> POWER;

	@SubscribeEvent
	public static void onAttachCapability(AttachCapabilitiesEvent<Entity> event) {
		final Entity entity = event.getObject();

		if (!entity.world.isRemote && entity instanceof EntityArrow) {
			double power = computePower((EntityArrow) entity);
			if (power == power) event.getCapabilities().put(POWER_ID, new PowershotEvent(power));
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
					double mul = enchantmentMultipliers.getOrDefault(ench.getString("id"), Double.NaN);
					if (mul == mul) power += MathHelper.clamp(ench.getInteger("lvl"), 0, 255) * mul;
				}
				return power;
			}
		}
		return Double.NaN;
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onImpactBlock(ProjectileImpactEvent.Arrow event) {
		EntityArrow arrow = event.getArrow();
		PowershotEvent cap = arrow.getCapability(POWER, null);
		if (arrow.world.isRemote || cap == null) return;

		RayTraceResult result = event.getRayTraceResult();

		if (result == null || result.getBlockPos() == null) {
			return;
		}

		BlockPos pos = result.getBlockPos();
		IBlockState state = arrow.world.getBlockState(pos);

		double require = powerRequirement.getOrDefault(state, Double.NaN);
		if (require == require && cap.power >= require) {
			arrow.world.destroyBlock(pos, true);
			event.setCanceled(true);
		}
	}
}
