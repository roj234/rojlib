package ilib.asm.nx;

import ilib.misc.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.Iterator;
import java.util.Map;

/**
 * @author Roj234
 * @since 2020/10/3 1:03
 */
@Nixim("/")
abstract class TrimNBT2 extends EntityLivingBase {
	@Shadow
	private int revengeTimer;
	@Shadow
	private Map<Potion, PotionEffect> activePotionsMap;

	TrimNBT2() {
		super(null);
	}

	@Inject("/")
	public void writeEntityToNBT(NBTTagCompound tag) {
		float health = getHealth();
		if (health != health || health > 1e100 || health < 0) {
			setHealth(health = 0);
		}
		tag.setFloat("Health", health);

		if (hurtTime > 0) tag.setShort("HurtTime", (short) hurtTime);
		if (revengeTimer > 0) tag.setInteger("HurtByTimestamp", revengeTimer);
		if (deathTime > 0) tag.setShort("DeathTime", (short) deathTime);
		if (getAbsorptionAmount() > 0) tag.setFloat("AbsorptionAmount", getAbsorptionAmount());

		AbstractAttributeMap map = this.getAttributeMap();

		EntityEquipmentSlot[] slots = MCHooks.slots();
		int i;
		EntityEquipmentSlot slot;
		ItemStack stack;
		for (i = 0; i < slots.length; ++i) {
			slot = slots[i];
			stack = this.getItemStackFromSlot(slot);
			if (!stack.isEmpty()) {
				map.removeAttributeModifiers(stack.getAttributeModifiers(slot));
			}
		}

		tag.setTag("Attributes", SharedMonsterAttributes.writeBaseAttributeMapToNBT(getAttributeMap()));

		for (i = 0; i < slots.length; ++i) {
			slot = slots[i];
			stack = this.getItemStackFromSlot(slot);
			if (!stack.isEmpty()) {
				map.applyAttributeModifiers(stack.getAttributeModifiers(slot));
			}
		}

		if (!activePotionsMap.isEmpty()) {
			NBTTagList list = new NBTTagList();

			Iterator<PotionEffect> itr = activePotionsMap.values().iterator();
			while (itr.hasNext()) {
				list.appendTag(itr.next().writeCustomPotionEffectToNBT(new NBTTagCompound()));
			}

			tag.setTag("ActiveEffects", list);
		}

		if (isElytraFlying()) tag.setBoolean("FallFlying", isElytraFlying());
	}
}
