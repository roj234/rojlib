package ilib.client.model;

import ilib.ImpLib;
import ilib.Register;

import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class ILItemModel extends ModelInfo {
	private Item item;
	private int meta;
	@SideOnly(Side.CLIENT)
	private ModelResourceLocation model;

	private ILItemModel() {}

	@SideOnly(Side.CLIENT)
	private static ResourceLocation[] tmp;

	@Override
	@SideOnly(Side.CLIENT)
	public void apply() {
		ModelResourceLocation model = this.model;
		if (meta == 32767) {
			// 只是这样是没用的
			ModelLoader.setCustomMeshDefinition(item, new SingleTexture(model));
			// 因为，还要加载(注册)模型
			if (tmp == null) tmp = new ResourceLocation[1];
			tmp[0] = model;
			ModelBakery.registerItemVariants(item, tmp);
		} else {
			ModelLoader.setCustomModelResourceLocation(item, meta, model);
		}
	}

	/**
	 * 从注册名生成合并物品模型,材质路径为<ns>:textures/item/<path>.png
	 */
	public static void Tex(Item item) {
		ResourceLocation rk = item.getRegistryName();
		Tex(item, 0, rk.getNamespace() + ":items/" + rk.getPath());
	}

	/**
	 * 从注册名生成合并物品模型,材质路径为<ns>:textures/item/<path>.png
	 */
	public static void Tex(Item item, int meta) {
		ResourceLocation rk = item.getRegistryName();
		Tex(item, meta, rk.getNamespace() + ":items/" + rk.getPath());
	}

	/**
	 * 从注册名生成合并物品模型,材质路径为texture
	 */
	public static void Tex(Item item, String texture) {
		Tex(item, 0, texture);
	}

	/**
	 * 从<key>生成合并物品模型,材质路径为<ns>:textures/item/<path>.png
	 */
	public static void Tex(Item item, int meta, String texture) {
		if (!ImpLib.isClient) return;

		BlockStateBuilder imm = ImpLib.proxy.getItemMergedModel();

		ILItemModel info = new ILItemModel();
		info.item = item;
		info.meta = meta;

		ResourceLocation rk = item.getRegistryName();
		String typeId = Integer.toString(rk.hashCode() ^ meta, 36);
		if (imm.variants.getOrCreateMap("type").containsKey(typeId)) {
			typeId = rk.getNamespace() + '_' + rk.getPath() + '$' + meta;
		}
		if (texture.indexOf(':') < 0) {
			texture = rk.getNamespace() + ":items/" + texture;
		}
		imm.setVariantTexture("type", typeId, "layer0", texture);

		info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/items", "type=" + typeId);
		Register.model(info);
	}

	/**
	 * 使用已有的合并方块模型, 来自默认的<key>
	 */
	public static void MergedBlk(Item item) {
		MergedBlk(item, 0, item.getRegistryName().getPath());
	}

	/**
	 * 使用已有的合并方块模型, 来自默认的<key>
	 */
	public static void MergedBlk(Item item, int meta) {
		MergedBlk(item, meta, item.getRegistryName().getPath());
	}

	/**
	 * 使用已有的合并方块模型
	 */
	public static void MergedBlk(Item item, int meta, String type) {
		if (!ImpLib.isClient) return;

		ILItemModel info = new ILItemModel();
		info.item = item;
		info.meta = meta;
		info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + type);
		Register.model(info);
	}

	/**
	 * 使用已有的合并物品模型
	 */
	public static void Merged(Item item) {
		Merged(item, 0, item.getRegistryName().getPath());
	}

	/**
	 * 使用已有的合并物品模型
	 */
	public static void Merged(Item item, int meta) {
		Merged(item, meta, item.getRegistryName().getPath());
	}

	/**
	 * 使用已有的合并物品模型
	 */
	public static void Merged(Item item, int meta, String type) {
		if (!ImpLib.isClient) return;

		ILItemModel info = new ILItemModel();
		info.item = item;
		info.meta = meta;
		info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/items", "type=" + type);
		Register.model(info);
	}

	/**
	 * 使用自定义的物品模型
	 */
	@SideOnly(Side.CLIENT)
	public static void Model(Item item, ModelResourceLocation location) {
		Model(item, 0, location);
	}

	/**
	 * 使用自定义的物品模型
	 */
	@SideOnly(Side.CLIENT)
	public static void Model(Item item, int meta, ModelResourceLocation location) {
		if (!ImpLib.isClient) return;

		ILItemModel info = new ILItemModel();
		info.item = item;
		info.meta = meta;
		info.model = location;
		Register.model(info);
	}

	/**
	 * 使用'默认'的物品模型
	 */
	public static void Vanilla(Item item) {
		Vanilla(item, 0);
	}

	/**
	 * 使用'默认'的物品模型
	 */
	public static void Vanilla(Item item, int meta) {
		if (!ImpLib.isClient) return;

		ILItemModel info = new ILItemModel();
		info.item = item;
		info.meta = meta;
		info.model = new ModelResourceLocation(item.getRegistryName(), "inventory");
		Register.model(info);
	}
}
