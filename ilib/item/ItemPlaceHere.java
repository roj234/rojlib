package ilib.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2022/4/4 14:18
 */
public class ItemPlaceHere extends ItemBase {
    public ItemPlaceHere() {
        setCreativeTab(CreativeTabs.TOOLS);
        setMaxStackSize(1);
    }

    @Nonnull
    @Override
    public EnumAction getItemUseAction(@Nonnull ItemStack stack) {
        return EnumAction.BLOCK;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, EntityPlayer player, @Nonnull EnumHand hand) {
        RayTraceResult mop = this.rayTrace(world, player, false);

        if (!world.isRemote) {
            if (mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK) {
                world.setBlockState(player.getPosition(), Blocks.GLASS.getDefaultState());
            } else {
                world.setBlockState(mop.getBlockPos().offset(mop.sideHit), Blocks.GLASS.getDefaultState());
            }
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
