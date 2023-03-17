package ilib.asm.nx.client.async;

import com.google.common.collect.Maps;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj233
 * @since 2022/5/20 5:37
 */
@Nixim("/")
class NxBSM extends BlockStateMapper {
	@Shadow
	private Map<Block, IStateMapper> blockStateMap;
	@Shadow
	private Set<Block> setBuiltInBlocks;

	@Inject("/")
	public Map<IBlockState, ModelResourceLocation> putAllStateModelLocations() {
		AsyncTexHook entry = AsyncTexHook.local.get();
		Map<IBlockState, ModelResourceLocation> map = entry == null ? Maps.newIdentityHashMap() : entry.models;
		map.clear();

		for (Block block : Block.REGISTRY) {
			map.putAll(getVariants(block));
		}

		return map;
	}

	@Inject("/")
	public Set<ResourceLocation> getBlockstateLocations(Block block) {
		if (this.setBuiltInBlocks.contains(block)) {
			return Collections.emptySet();
		}
		IStateMapper mapper = blockStateMap.get(block);
		if (mapper == null) {
			return Collections.singleton(Block.REGISTRY.getNameForObject(block));
		}

		AsyncTexHook entry = AsyncTexHook.local.get();
		Set<ResourceLocation> set = entry == null ? new MyHashSet<>() : entry.paths;
		set.clear();

		Iterator<ModelResourceLocation> itr = mapper.putStateModelLocations(block).values().iterator();
		while (itr.hasNext()) {
			ModelResourceLocation mloc = itr.next();
			set.add(new ResourceLocation(mloc.getNamespace(), mloc.getPath()));
		}

		return set;
	}

	@Inject("/")
	public Map<IBlockState, ModelResourceLocation> getVariants(Block blockIn) {
		if (setBuiltInBlocks.contains(blockIn)) return Collections.emptyMap();

		IStateMapper mapper = blockStateMap.getOrDefault(blockIn, AsyncTexHook.local.get().def);
		return mapper.putStateModelLocations(blockIn);
	}
}
