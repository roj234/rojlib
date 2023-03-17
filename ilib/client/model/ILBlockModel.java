package ilib.client.model;

import ilib.ImpLib;
import ilib.Register;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class ILBlockModel extends ModelInfo {
	private Block block;
	private ModelResourceLocation model;

	private ILBlockModel() {}

	@Override
	@SideOnly(Side.CLIENT)
	public void apply() {
		ModelResourceLocation model = this.model;
		if (model != null) {
			ModelLoader.setCustomStateMapper(block, new SingleTexture(model));
			Item item = Item.getItemFromBlock(block);
			if (item != Items.AIR) {
				ModelLoader.setCustomModelResourceLocation(item, 0, model);
			}
		}
	}

	public static void Tex6(Block block, String texture) {
		if (!ImpLib.isClient) return;

		BlockStateBuilder imm = ImpLib.proxy.getBlockMergedModel();

		ILBlockModel info = new ILBlockModel();
		info.block = block;

		ResourceLocation rk = block.getRegistryName();
		String typeId = Integer.toString(rk.hashCode(), 36);

		if (imm.variants.getOrCreateMap("type").containsKey(typeId)) {
			typeId = rk.getNamespace() + "_" + rk.getPath();
		}
		if (texture.indexOf(':') < 0) {
			texture = rk.getNamespace() + ":blocks/" + texture;
		}
		imm.setVariantTexture("type", typeId, "all", texture);

		info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + typeId);
		Register.model(info);
	}

	public static void MergedModel(Block block, String model) {
		if (!ImpLib.isClient) return;

		BlockStateBuilder imm = ImpLib.proxy.getBlockMergedModel();

		ILBlockModel info = new ILBlockModel();
		info.block = block;

		ResourceLocation rk = block.getRegistryName();
		String typeId = Integer.toString(rk.hashCode(), 36);

		if (imm.variants.getOrCreateMap("type").containsKey(typeId)) {
			typeId = rk.getNamespace() + "_" + rk.getPath();
		}
		if (model.indexOf(':') < 0) {
			model = rk.getNamespace() + ":" + model;
		}
		imm.setVariantModel("type", typeId, model);

		info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + typeId);
		Register.model(info);
	}

	public static void Merged(Block block) {
		Merged(block, block.getRegistryName().getPath());
	}

	public static void Merged(Block block, String type) {
		if (!ImpLib.isClient) return;

		ILBlockModel info = new ILBlockModel();
		info.block = block;
		info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + type);
		Register.model(info);
	}

	public static void Air(Block block) {
		if (!ImpLib.isClient) return;

		ILBlockModel info = new ILBlockModel();
		info.block = block;
		info.model = new ModelResourceLocation("ilib:air");
		Register.model(info);
	}

	@SideOnly(Side.CLIENT)
	public static void Model(Block block, ModelResourceLocation location) {
		if (!ImpLib.isClient) return;

		ILBlockModel info = new ILBlockModel();
		info.block = block;
		info.model = location;
		Register.model(info);
	}
}
