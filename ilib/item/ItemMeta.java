package ilib.item;

import ilib.ImpLib;
import ilib.api.registry.IRegistry;
import ilib.api.registry.Indexable;
import ilib.api.registry.Propertied;
import ilib.api.registry.RegistryBuilder;
import ilib.client.model.TypedModelHelper;
import ilib.util.Hook;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 基于MI-Enumeration ({@link Propertied})创建Meta物品
 *
 * @see RegistryBuilder
 */
public class ItemMeta<T extends Indexable> extends ItemBase {
	public final IRegistry<T> wrapper;
	public final String name;
	private Object generateModel = true;

	public ItemMeta(String name, IRegistry<T> wrapper) {
		super();
		setHasSubtypes(true);
		setMaxDamage(0);
		//奇妙的“修复物品的设备”
		setNoRepair();

		this.name = name;
		this.wrapper = wrapper;
	}

	public ItemMeta(String name, String texturePath, IRegistry<T> wrapper) {
		this(name, wrapper);
		ImpLib.EVENT_BUS.add(Hook.MODEL_REGISTER, () -> registerModel(texturePath));
	}

	public static ItemMeta<RegistryBuilder.Std> standard(String name, String texture, String... list) {
		return new ItemMeta<>(name, texture, new RegistryBuilder(list).build());
	}

	public ItemMeta<T> setGenerateModel(Object generateModel) {
		this.generateModel = generateModel;
		return this;
	}

	@SideOnly(Side.CLIENT)
	public void registerModel(String texturePath) {
		if (generateModel == Boolean.TRUE) {
			TypedModelHelper.itemTypedModel(this, new ResourceLocation(modid(), "items/" + name), texturePath, wrapper);
		} else if (generateModel == Boolean.FALSE) {
			ResourceLocation base = new ResourceLocation(modid(), "items/" + texturePath);
			for (T t : wrapper.values()) {
				ModelLoader.setCustomModelResourceLocation(this, indexFor(t), new ModelResourceLocation(base, "type=" + t.getName()));
			}
		}
	}

	protected int indexFor(T t) {
		return t.getIndex();
	}

	@Override
	public final String getTranslationKey(ItemStack is) {
		T t = getTypeByStack(is);
		if (t == null) return "invalid";
		return "item.mi." + name + "." + t.getName();
	}

	@Override
	public void getSubItems(NonNullList<ItemStack> list) {
		for (T type : wrapper.values()) {
			list.add(new ItemStack(this, 1, indexFor(type)));
		}
	}

	public final ItemStack getStackByType(T type, int count) {
		return new ItemStack(this, count, indexFor(type));
	}

	public T getTypeByStack(final ItemStack is) {
		final int meta = is.getItemDamage();
		return wrapper.byId(meta);
	}
}