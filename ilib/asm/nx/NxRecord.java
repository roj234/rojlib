package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/**
 * @author Roj233
 * @since 2022/5/23 17:43
 */
@Nixim("/")
class NxRecord extends ItemRecord {
	protected NxRecord() {
		super(null, null);
	}

	@Override
	@Copy
	public boolean isDamageable() {
		return true;
	}

	@Override
	@Copy
	public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
		target.attackEntityFrom(DamageSource.causeMobDamage(attacker), 1);
		return true;
	}

	@Override
	@Copy
	public int getMaxDamage(ItemStack stack) {
		return 5;
	}

	@Override
	@Copy
	public int getMaxItemUseDuration(ItemStack stack) {
		return 50000;
	}
}
