package ilib.asm.nx.client.async;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.DefaultStateMapper;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/5/20 5:50
 */
public class MyDefMap extends DefaultStateMapper {
	@Override
	public Map<IBlockState, ModelResourceLocation> putStateModelLocations(Block blockIn) {
		mapStateModelLocations.clear();
		return super.putStateModelLocations(blockIn);
	}
}
