package ilib.client.model;

import ilib.ImpLib;
import roj.config.data.CMapping;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj233
 * @since 2022/4/18 22:49
 */
public final class ILFluidModel extends ModelInfo {
	private final String fluid;
	private final BlockFluidBase block;

	public ILFluidModel(String fluid, BlockFluidBase block) {
		this.fluid = fluid;
		this.block = block;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void apply() {
		CMapping cst = new CMapping();
		cst.put("fluid", fluid);

		ImpLib.proxy.getFluidMergedModel().setSingleVariantValue(fluid, "custom", cst);

		ModelResourceLocation path = new ModelResourceLocation(ImpLib.MODID + ":generated/fluids", fluid);

		Item item = Item.getItemFromBlock(block);
		SingleTexture def = new SingleTexture(path);
		if (item != Items.AIR) ModelLoader.setCustomMeshDefinition(item, def);
		ModelLoader.setCustomStateMapper(block, def);
	}
}
