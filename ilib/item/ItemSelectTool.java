package ilib.item;

import ilib.ImpLib;
import ilib.math.Arena;
import ilib.math.SelectionCache;
import ilib.util.ChatColor;
import ilib.util.MCTexts;
import ilib.util.PlayerUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ItemSelectTool extends ItemRightClick {
	public static Item INSTANCE;

	public ItemSelectTool() {
		INSTANCE = this;
		setCreativeTab(CreativeTabs.TOOLS);
		setMaxStackSize(1);
		setHasSubtypes(true);
		MinecraftForge.EVENT_BUS.register(ItemSelectTool.class);
	}

	@Override
	public String getTranslationKey(ItemStack stack) {
		switch (stack.getItemDamage()) {
			case 0:
				return "item.ilib.select_tool";
			case 1:
				return "item.ilib.speed_modifier";
			case 2:
				return "item.ilib.place_here";
		}
		return "tile.air.name";
	}

	@Override
	protected void getSubItems(NonNullList<ItemStack> list) {
		for (int i = 0; i < 3; i++) {
			list.add(new ItemStack(this, 1, i));
		}
	}

	public float getDestroySpeed(ItemStack stack, IBlockState state) {
		return stack.getItemDamage() == 2 ? 1 : 0;
	}

	@Override
	public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
		return stack.getItemDamage() == 2;
	}

	@Override
	public EnumAction getItemUseAction(ItemStack stack) {
		return stack.getItemDamage() == 2 ? EnumAction.BLOCK : EnumAction.NONE;
	}

	@Override
	protected ItemStack onRightClick(World world, EntityPlayer player, ItemStack stack, EnumHand hand) {
		switch (stack.getItemDamage()) {
			case 0: {
				long UUID = player.getUniqueID().getMostSignificantBits();
				if (player.isSneaking()) {
					if (SelectionCache.remove(UUID) && world.isRemote) {
						PlayerUtil.sendTo(player, "command.ilib.sel.clear");
						player.playSound(SoundEvents.BLOCK_NOTE_PLING, 1, 0);
						return stack;
					}
					return null;
				}

				RayTraceResult mop = this.rayTrace(world, player, true);

				if (mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK) {
					return null;
				}

				BlockPos pos = mop.getBlockPos();
				Arena arena = SelectionCache.get(UUID);
				if (arena != null && pos.equals(arena.getP2())) return null;

				int count = SelectionCache.set(UUID, 2, pos).getSelectionSize();

				if (world.isRemote) {
					PlayerUtil.sendTo(player, MCTexts.format("command.ilib.sel.pos2") + ": " + pos.getX() + ',' + pos.getY() + ',' + pos.getZ());
					if (count > 0) PlayerUtil.sendTo(player, "command.ilib.sel.size", count);
					player.playSound(SoundEvents.BLOCK_NOTE_PLING, 1, 1);
				}
			}
			break;
			case 1: {
				PlayerCapabilities cap = player.capabilities;
				boolean sneaking = player.isSneaking();

				if (cap.isFlying) {
					float fly = cap.getFlySpeed();
					if (sneaking) {fly /= 2;} else fly *= 2;

					cap.setFlySpeed(MathHelper.clamp(fly, 0.00125f, 3.2f));
					if (world.isRemote) PlayerUtil.sendTo(player, ChatColor.GREY + "飞行速度: " + ChatColor.ORANGE + (fly / 0.05f) + 'x');
				} else {
					float walk = cap.getWalkSpeed();
					if (sneaking) {walk /= 2;} else walk *= 2;

					cap.setPlayerWalkSpeed(MathHelper.clamp(walk, 0.00125f, 3.2f));
					if (world.isRemote) PlayerUtil.sendTo(player, ChatColor.GREY + "走路速度: " + ChatColor.ORANGE + (walk / 0.1f) + 'x');
				}
			}
			break;
			case 2:
				RayTraceResult mop = this.rayTrace(world, player, false);
				BlockPos pos = mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK ? player.getPosition() : mop.getBlockPos().offset(mop.sideHit);

				if (world.isAirBlock(pos)) {
					if (!world.isRemote) {
						world.setBlockState(pos, Blocks.GLASS.getDefaultState(), 2);
						world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 1, 1);
					}
				} else {
					return null;
				}
				break;
		}

		return stack;
	}

	@Override
	protected void addTooltip(ItemStack stack, List<String> list) {
		if (stack.getItemDamage() == 1) {
			list.add(MCTexts.format("tooltip.ilib.speed.1"));
			list.add(MCTexts.format("tooltip.ilib.speed.2"));
		}
	}

	@SubscribeEvent
	public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
		ItemStack stack = event.getItemStack();
		if (stack.getItem() == INSTANCE && stack.getItemDamage() == 0) {
			event.setCanceled(true);

			long UUID = event.getEntityPlayer().getUniqueID().getMostSignificantBits();
			Arena arena = SelectionCache.get(UUID);
			BlockPos pos = event.getPos();

			if (arena != null && pos.equals(arena.getP1())) return;

			// 本地服务器会共享静态字段
			if (ImpLib.isClient && !event.getWorld().isRemote) return;

			int count = SelectionCache.set(UUID, 1, pos).getSelectionSize();

			if (event.getWorld().isRemote) {
				EntityPlayer p = event.getEntityPlayer();
				PlayerUtil.sendTo(p, MCTexts.format("command.ilib.sel.pos1") + ": " + pos.getX() + ',' + pos.getY() + ',' + pos.getZ());
				if (count > 0) PlayerUtil.sendTo(p, "command.ilib.sel.size", count);
				p.playSound(SoundEvents.BLOCK_NOTE_PLING, 1, 1);
			}

		}
	}
}
