package ilib.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.ItemMeshDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.ItemStack;

/**
 * @author Roj233
 * @since 2022/4/18 22:52
 */
public class SingleTexture extends StateMapperBase implements ItemMeshDefinition {
	private final ModelResourceLocation path;

	public SingleTexture(ModelResourceLocation path) {
		this.path = path;
	}

	@Override
	protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
		return path;
	}

	@Override
	public ModelResourceLocation getModelLocation(ItemStack stack) {
		return path;
	}
}
