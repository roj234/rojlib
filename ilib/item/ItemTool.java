package ilib.item;

import ilib.ImpLib;
import ilib.api.item.ITooltip;
import ilib.api.tile.ToolTarget;
import ilib.api.tile.ToolTarget.Type;
import ilib.util.ChatColor;
import ilib.util.MCTexts;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Deprecated
public class ItemTool extends ItemRightClick implements ITooltip {
	private final Type type;

	public static final ItemTool WRENCH = new ItemTool(Type.WRENCH), CUTTER = new ItemTool(Type.CUTTER);

	ItemTool(Type type) {
		super();
		setNoRepair();
		this.type = type;
		setMaxDamage(type.MAX_DAMAGE);
	}

	@Override
	protected ItemStack onRightClickBlock(World world, BlockPos pos, BlockPos clickPos, EntityPlayer player, ItemStack stack, EnumFacing side) {
		if (world.isRemote) return stack;
		TileEntity te = world.getTileEntity(clickPos);

		stack = player.capabilities.isCreativeMode ? stack : duraShrink1(stack);

		if (te instanceof ToolTarget) {
			ToolTarget tile = (ToolTarget) te;

			int bin = tile.canUse(type.getIndex(), player.isSneaking());
			if (bin < 0) {
				return null;
			}

			switch (bin) {
				case ToolTarget.TOGGLE:
					tile.toggleByTool(type.getIndex(), side);
					break;
				case ToolTarget.TURN:
					boolean flag = world.getBlockState(clickPos).getBlock().rotateBlock(world, clickPos, side);
					return flag ? stack : null;
				case ToolTarget.DESTROY:
					tile.destroyByTool(type.getIndex());
					break;
				case ToolTarget.SPECIAL:
					break;
				default:
					ImpLib.logger().warn("ToolTarget: undefined type: " + bin);
			}
			return stack;
		} else if (!player.isSneaking() && type.getIndex() == 0) {
			boolean flag = world.getBlockState(clickPos).getBlock().rotateBlock(world, clickPos, side);

			return flag ? stack : null;
		}
		return null;
	}

	private static ItemStack duraShrink1(ItemStack stack) {
		int dura = stack.getItemDamage();
		if (dura < stack.getMaxDamage()) {
			stack.setItemDamage(++dura);
			return stack;
		} else {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public void addTooltip(ItemStack stack, List<String> tooltip) {
		tooltip.add(ChatColor.DARK_BLUE + MCTexts.format("tooltip.mi.tool." + type.getName()));
	}
}