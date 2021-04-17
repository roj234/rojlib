package ilib.util;

import ilib.ImpLib;
import ilib.api.Ownable;
import ilib.api.energy.METile;
import ilib.api.tile.ToolTarget;
import ilib.tile.TileBase;
import roj.text.TextUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/22 19:42
 */
public final class ItemUtils {
	/**
	 * String to itemstack like minecraft:stone@0*1{myNbt: 1b}
	 *
	 * @return block data
	 */
	public static ItemStack string2Stack(String data) {
		if (data == null || data.length() == 0) return ItemStack.EMPTY;

		int index = data.indexOf("@");
		if (index < 0) {
			return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation(data)));
		}
		String itemMeta = data.substring(index + 1);
		String itemName = data.substring(0, index);

		Item item = Item.REGISTRY.getObject(new ResourceLocation(itemName));
		if (item == null) return null;

		index = itemMeta.indexOf("*");

		if (index < 0) return new ItemStack(item, 1, Integer.parseInt(itemMeta));

		String itemCount = itemMeta.substring(index + 1);
		int _itemMeta = Integer.parseInt(itemMeta.substring(0, index));

		index = itemCount.indexOf("{");
		if (index < 0) return new ItemStack(item, Integer.parseInt(itemCount), _itemMeta);

		String itemNBT = itemCount.substring(index);
		itemCount = itemCount.substring(0, index);

		ItemStack stack = new ItemStack(item, Integer.parseInt(itemCount), _itemMeta);
		try {
			NBTTagCompound tag = JsonToNBT.getTagFromJson(itemNBT.replace("&", "\u00a7"));
			stack.setTagCompound(tag);
		} catch (NBTException e) {
			ImpLib.logger().warn("Couldn't get NBT by given tag: " + itemNBT);
			e.printStackTrace();
		}
		return stack;
	}

	public static String stack2String(ItemStack is) {
		String nbtStr = "{}";
		if (is.getTagCompound() != null) {
			nbtStr = is.getTagCompound().toString();
		}
		return is.getItem().getRegistryName() + "@" + is.getItemDamage() + "*1" + nbtStr;
	}

	public static String stackUuid(ItemStack is) {
		String nbtStr = "{}";
		if (is.getTagCompound() != null) {
			nbtStr = is.getTagCompound().toString();
		}
		return is.getItem().getRegistryName() + "@" + is.getItemDamage() + nbtStr;

	}

	public static void dropStacks(World world, List<ItemStack> stacks, BlockPos pos) {
		for (int i = 0; i < stacks.size(); i++) {
			dropStack(world, stacks.get(i), pos);
		}
	}

	public static void dropStack(World world, ItemStack stack, BlockPos pos) {
		if (!stack.isEmpty()) {
			float rx = world.rand.nextFloat() * 0.8F;
			float ry = world.rand.nextFloat() * 0.8F;
			float rz = world.rand.nextFloat() * 0.8F;

			EntityItem item = new EntityItem(world, pos.getX() + rx, pos.getY() + ry, pos.getZ() + rz, stack.copy());

			float factor = 0.05F;

			item.motionX = world.rand.nextGaussian() * factor;
			item.motionY = world.rand.nextGaussian() * factor + 0.2F;
			item.motionZ = world.rand.nextGaussian() * factor;
			item.setDefaultPickupDelay();
			world.spawnEntity(item);

			stack.setCount(0);
		}
	}

	public static void dropStacksInInventory(@Nonnull IItemHandler ih, World world, BlockPos pos) {
		for (int slot = 0; slot < ih.getSlots(); slot++) {
			ItemStack stack = ih.getStackInSlot(slot);
			dropStack(world, stack, pos);
		}
	}

	public static boolean dropWithOwner(World world, BlockPos pos) {
		IBlockState state = world.getBlockState(pos);
		ItemStack stack = state.getBlock().getItem(world, pos, state);
		stack = setOwner(world, stack, pos);
		if (stack == null) return false;
		dropStack(world, stack, pos);
		//world.removeTileEntity(pos); // Cancel drop logic
		//world.setBlockToAir(pos);
		return true;
	}

	public static ItemStack setOwner(IBlockAccess world, ItemStack stack, BlockPos pos) {
		if ((world instanceof World) && ((World) world).isRemote) return null;
		TileEntity tile = world.getTileEntity(pos);
		if (tile == null) return null;

		return setOwner(world, stack, pos, tile);
	}

	@Deprecated
	public static ItemStack setOwner(IBlockAccess world, ItemStack stack, BlockPos pos, TileEntity tile) {
		if (!(tile instanceof Ownable)) return null;
		Ownable t = (Ownable) tile;

		if (t.getOwnerManager() != null) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setInteger("OwnMode", t.getOwnType());
			tag.setString("Own", t.getOwnerManager().getId());
			ItemNBT.setCompound(stack, "Owner", tag);
		}

		if (tile instanceof METile) ItemNBT.setInt(stack, "MaxME", ((METile) tile).maxME());

		return stack;
	}

	public static void breakBlockSavingNBT(World world, BlockPos pos, @Nonnull ToolTarget block) {
		if (world.isRemote) return;
		NBTTagCompound tag = block.storeDestroyData();
		tag.removeTag("x");
		tag.removeTag("y");
		tag.removeTag("z");
		IBlockState state = world.getBlockState(pos);
		ItemStack stack = state.getBlock().getItem(world, pos, state);
		ItemNBT.setRootTag(stack, tag);
		if (block instanceof METile) {
			ItemNBT.setInt(stack, "MaxME", ((METile) block).maxME());
		}
		dropStack(world, stack, pos);
		world.removeTileEntity(pos); // Cancel drop logic
		world.setBlockToAir(pos);
	}

	public static void breakBlock(World world, BlockPos pos) {
		IBlockState state = world.getBlockState(pos);
		ItemStack stack = state.getBlock().getItem(world, pos, state);
		dropStack(world, stack, pos);
		state.getBlock().breakBlock(world, pos, state);
		world.removeTileEntity(pos); // Cancel drop logic
		world.setBlockToAir(pos);
	}

	/**
	 * Call this after onBlockPlacedBy to write saved data to the stack if present
	 *
	 * @param world The world
	 * @param pos The block position
	 * @param stack The stack that had the tag
	 */
	public static void writeStackNBTToBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull ItemStack stack) {
		//if
		TileEntity tile = world.getTileEntity(pos);
		if (tile != null) {
			NBTTagCompound tag = ItemNBT.getRootTagNullable(stack);
			if (tag == null) return;
			tag = tag.copy();
			tag.setInteger("x", pos.getX()); // Add back MC tags
			tag.setInteger("y", pos.getY());
			tag.setInteger("z", pos.getZ());
			tile.readFromNBT(tag);
			if (tile instanceof TileBase) {
				((TileBase) tile).sendDataUpdate();
			}
		} else if (stack.hasTagCompound()) {
			ImpLib.logger().warn("Try to write but there is not a tileentity[Data re-write failed]");
		}
	}

	/**
	 * 找到首个矿物名
	 */
	@Nullable
	public static String firstOredictName(@Nullable ItemStack i) {
		if (i == null) return null;
		try {
			return OreDictionary.getOreName(OreDictionary.getOreIDs(i)[0]);
		} catch (IndexOutOfBoundsException ignored) {}
		return null;
	}

	public static ItemStack[] string2Stacks(String string) {
		int index = string.indexOf(":");
		if (index > 0) {
			String s = string.substring(index);
			if (s.equals("ore")) {
				List<ItemStack> list = OreDictionary.getOres(s.substring(index + 1), false);
				if (list.isEmpty()) throw new IllegalArgumentException("The ore specified " + s.substring(index + 1) + " does not have suitable item.");
				return list.toArray(new ItemStack[list.size()]);
			}
		}
		return new ItemStack[] {string2Stack(string)};
	}

	public static void setOreDict(Item item, int meta, String oreName) {
		if (oreName.equals("cancel")) return;
		ItemStack stack = new ItemStack(item, 1, meta);
		for (String name : TextUtil.split(oreName, ',')) {
			OreDictionary.registerOre(name, stack);
		}
	}

	public static void setOreDict(Block block, int meta, String oreName) {
		if (oreName.equals("cancel")) return;
		ItemStack stack = new ItemStack(block, 1, meta);
		for (String name : TextUtil.split(oreName, ',')) {
			OreDictionary.registerOre(name, stack);
		}
	}
}