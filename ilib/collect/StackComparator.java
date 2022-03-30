package ilib.collect;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import roj.collect.UnsortedMultiKeyMap;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/6 20:40
 */
public class StackComparator implements UnsortedMultiKeyMap.Keys<ItemStack, String> {
    public static final StackComparator COMPARE_ITEM = new StackComparator();
    public static final StackComparator INSTANCE     = new StackComparator();

    @Override
    public List<String> getKeys(ItemStack key, List<String> holder) {
        int[] oreIds = net.minecraftforge.oredict.OreDictionary.getOreIDs(key);
        if (oreIds.length > 0) {
            for (int i : oreIds) {
                holder.add(net.minecraftforge.oredict.OreDictionary.getOreName(i));
            }
        }
        String s = key.getItem().getRegistryName().toString();
        holder.add(s + ':' + key.getItemDamage());
        if (this == COMPARE_ITEM) {
            holder.add(s);
        }
        return holder;
    }

    @Override
    public String getPrimaryKey(ItemStack key) {
        int[] oreIds = net.minecraftforge.oredict.OreDictionary.getOreIDs(key);
        if (oreIds.length > 0) {
            return OreDictionary.getOreName(oreIds[0]);
        }
        return key.getItem().getRegistryName().toString() + ':' + key.getItemDamage();
    }
}
