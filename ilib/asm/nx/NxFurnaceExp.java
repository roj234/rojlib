package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * @author solo6975
 * @since 2022/4/5 19:54
 */
@Nixim("net.minecraft.block.BlockFurnace")
abstract class NxFurnaceExp extends BlockContainer {
	@Shadow("field_149934_M")
	static boolean keepInventory;

	protected NxFurnaceExp(Material _lvt_1_) {
		super(_lvt_1_);
	}

	@Inject(value = "func_180663_b", at = Inject.At.REPLACE)
	public void breakBlock(World w, BlockPos p, IBlockState s) {
		if (!keepInventory) {
			TileEntity tile = w.getTileEntity(p);
			if (tile instanceof TileEntityFurnace) {
				TileEntityFurnace furnace = (TileEntityFurnace) tile;

				ItemStack out = furnace.getStackInSlot1(2);
				float each = FurnaceRecipes.instance().getSmeltingExperience(out);

				if (each > 0) {
					float total = out.getCount() * each;

					int j = MathHelper.floor(total);
					if (j < MathHelper.ceil(total) && Math.random() < (total - j)) j++;

					BlockPos pos = tile.getPos();
					while (j > 0) {
						int ball = EntityXPOrb.getXPSplit(j);
						j -= ball;
						w.spawnEntity(new EntityXPOrb(w, pos.getX(), pos.getY() + 0.5, pos.getZ(), ball));
					}
				}

				InventoryHelper.dropInventoryItems(w, p, furnace);

				w.updateComparatorOutputLevel(p, this);
			}
		}

		super.breakBlock(w, p, s);
	}
}
