package ilib.asm.nx.client;

import ilib.asm.util.FastSearchTree;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.util.Helpers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.util.SearchTree;
import net.minecraft.client.util.SearchTreeManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/10 16:52
 */
@Nixim("net.minecraft.client.Minecraft")
class NxSearch extends Minecraft {
	@Shadow("field_193995_ae")
	private SearchTreeManager searchTreeManager;

	public NxSearch() {
		super(null);
	}

	@Inject("/")
	public void populateSearchTreeManager() {
		SearchTree<ItemStack> tree = new FastSearchTree<>((stack) -> {
			List<String> list = new ArrayList<>();

			List<String> tooltip = stack.getTooltip(null, ITooltipFlag.TooltipFlags.NORMAL);
			for (int i = 0; i < tooltip.size(); i++) {
				String s = tooltip.get(i);
				s = TextFormatting.getTextWithoutFormattingCodes(s).trim();
				if (!s.isEmpty()) list.add(s);
			}
			return list;
		}, (stack) -> {
			return Collections.singleton(Item.REGISTRY.getNameForObject(stack.getItem()));
		});

		NonNullList<ItemStack> items = NonNullList.create();

		Iterator<Item> itr = Item.REGISTRY.iterator();
		while (itr.hasNext()) {
			itr.next().getSubItems(CreativeTabs.SEARCH, items);
			for (int i = 0; i < items.size(); i++) {
				tree.add(items.get(i));
			}
			items.clear();
		}

		searchTreeManager.register(SearchTreeManager.ITEMS, tree);
		searchTreeManager.register(SearchTreeManager.RECIPES, new SearchTree<>(Helpers.cast(Helpers.fnArrayList()), Helpers.cast(Helpers.fnArrayList())));
	}
}
