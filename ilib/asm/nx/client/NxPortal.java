package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.world.World;

@Nixim("net.minecraft.client.entity.EntityPlayerSP")
abstract class NxPortal extends EntityPlayerSP {
	@Shadow("field_71080_cy")
	private float prevTimeInPortal;
	@Shadow("field_71086_bY")
	private float timeInPortal;

	public NxPortal(Minecraft p_i47378_1_, World p_i47378_2_, NetHandlerPlayClient p_i47378_3_, StatisticsManager p_i47378_4_, RecipeBook p_i47378_5_) {
		super(p_i47378_1_, p_i47378_2_, p_i47378_3_, p_i47378_4_, p_i47378_5_);
	}

	/**
	 * @author Nakido
	 */
	@Inject(value = "func_70636_d")
	public void onLivingUpdate() {
		this.inPortal = false;
		super.onLivingUpdate();
		this.prevTimeInPortal = 0;
		this.timeInPortal = 0;
	}
}