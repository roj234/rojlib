package ilib.asm.nx;

import com.mojang.authlib.GameProfile;
import ilib.misc.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2021/8/21 21:36
 */
@Nixim("/")
abstract class NoEnchantTax extends EntityPlayer {
	public NoEnchantTax(World worldIn, GameProfile gameProfileIn) {
		super(worldIn, gameProfileIn);
	}

	@Inject("/")
	public void onEnchant(ItemStack stack, int cost) {
		if (this.experienceLevel > 30 && cost == 30) {
			addScore(-MCHooks.ench30s);
		} else {
			this.experienceLevel -= cost;
			if (this.experienceLevel < 0) {
				this.experienceLevel = 0;
				this.experience = 0.0F;
				this.experienceTotal = 0;
			}
		}

		this.xpSeed = this.rand.nextInt();
	}
}
