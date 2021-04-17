package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;

/**
 * @author Roj234
 * @since 2020/8/18 0:21
 */
@Nixim("net.minecraft.item.ItemPickaxe")
class NxFastPickaxe {
	@Shadow(value = "field_77862_b", owner = "net.minecraft.item.ItemTool")
	Item.ToolMaterial toolMaterial;

	@Inject("func_150897_b")
	public boolean canHarvestBlock(IBlockState state) {
		if ("pickaxe".equals(state.getBlock().getHarvestTool(state)) && state.getBlock().getHarvestLevel(state) >= this.toolMaterial.getHarvestLevel()) return true;
		Material mat = state.getMaterial();
		return mat == Material.ROCK || mat == Material.IRON || mat == Material.ANVIL;
	}
}
