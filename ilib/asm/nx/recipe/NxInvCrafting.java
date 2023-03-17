package ilib.asm.nx.recipe;

import ilib.misc.MCHooks;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;

/**
 * @author Roj234
 * @since 2021/4/21 22:51java
 */
@Nixim(value = "net.minecraft.inventory.InventoryCrafting", copyItf = true)
abstract class NxInvCrafting extends InventoryCrafting implements MCHooks.RecipeCache {
	// currentRecipe与CatServer冲突
	@Copy(unique = true)
	public IRecipe currentRecipe;

	private NxInvCrafting(Container p_i1807_1_, int p_i1807_2_, int p_i1807_3_) {
		super(p_i1807_1_, p_i1807_2_, p_i1807_3_);
	}

	@Override
	@Copy
	public IRecipe getRecipe() {
		return currentRecipe;
	}

	@Override
	@Copy
	public void setRecipe(IRecipe recipe) {
		this.currentRecipe = recipe;
	}
}
