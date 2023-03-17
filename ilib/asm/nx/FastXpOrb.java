package ilib.asm.nx;

import ilib.Config;
import roj.asm.nixim.*;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;

@Nixim("/")
@Dynamic({"forge","2768"})
abstract class FastXpOrb extends EntityXPOrb {
	FastXpOrb() {
		super(null);
	}

	@Copy
	public void move(MoverType type, double x, double y, double z) {
		if (!world.isRemote) move0(type, x, y, z);
	}

	@Shadow(value = "func_70091_d", owner = "net/minecraft/entity/Entity")
	private void move0(MoverType type, double x, double y, double z) {}

	@Inject("/")
	public void onCollideWithPlayer(EntityPlayer pl) {
		if (!world.isRemote && delayBeforeCanPickup == 0 && pl.xpCooldown == 0) {
			if (MinecraftForge.EVENT_BUS.post(new PlayerPickupXpEvent(pl, this))) {
				return;
			}

			pl.xpCooldown = Config.xpCooldown;
			pl.onItemPickup(this, 1);

			ItemStack stack = EnchantmentHelper.getEnchantedItem(Enchantments.MENDING, pl);
			if (!stack.isEmpty() && stack.isItemDamaged()) {
				float ratio = stack.getItem().getXpRepairRatio(stack);
				int i = Math.min(roundAverage(xpValue * ratio), stack.getItemDamage());
				xpValue -= roundAverage(i / ratio);
				stack.setItemDamage(stack.getItemDamage() - i);
			}

			if (xpValue > 0) pl.addExperience(xpValue);

			setDead();
		}

	}

	@Shadow("/")
	private static int roundAverage(float value) {return 0;}
}
