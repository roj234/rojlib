package ilib.tile;

import ilib.api.TileRegister;
import ilib.item.handler.ListInventory;
import roj.collect.MyHashSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;

/**
 * @author Roj234
 * @since 2020/8/18 13:41
 */
@TileRegister("ilib:loot_chest")
public class TileEntityLootrChest extends TileEntityChest {
	private static final Random random = new Random();
	Set<String> opened = new MyHashSet<>();

	//@Override
	//public void setLootTable(ResourceLocation loc, long seed) {
	//    super.setLootTable(loc, seed);
	//}

	@Override
	public void fillWithLoot(@Nullable EntityPlayer player) {
	}

	public void fillWithLoot(@Nonnull EntityPlayer player, IInventory inventory) {
		if (this.lootTable != null) {
			LootTable table = this.world.getLootTableManager().getLootTableFromLocation(this.lootTable);
			random.setSeed(this.lootTableSeed);

			LootContext.Builder builder = new LootContext.Builder((WorldServer) this.world);
			builder.withLuck(player.getLuck()).withPlayer(player);

			table.fillInventory(inventory, random, builder.build());
		}
	}

	public boolean addPlayerOpened(EntityPlayer player) {
		return this.lootTable == null || opened.add(player.getName().toLowerCase());
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		if (opened.size() != 0) {
			NBTTagList list = new NBTTagList();
			for (String s : opened) {
				list.appendTag(new NBTTagString(s));
			}
			compound.setTag("opened", list);
		}
		super.readFromNBT(compound);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		NBTTagList list = compound.getTagList("opened", 8);
		for (int i = 0; i < list.tagCount(); i++) {
			opened.add(list.getStringTagAt(i));
		}
		return super.writeToNBT(compound);
	}

	public IInventory getInv(EntityPlayer player) {
		ListInventory fakeInventory = new ListInventory(3 * 9);
		if (addPlayerOpened(player)) {
			fillWithLoot(player, fakeInventory);
		}

		return fakeInventory;
	}
}
