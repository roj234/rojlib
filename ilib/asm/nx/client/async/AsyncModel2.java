package ilib.asm.nx.client.async;

import org.apache.logging.log4j.Logger;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.SimpleList;
import roj.util.Helpers;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author Roj233
 * @since 2022/5/20 6:33
 */
@Nixim("/")
class AsyncModel2 extends ModelBakery {
	@Shadow
	private static Logger LOGGER;
	@Shadow
	private final Map<ResourceLocation, ModelBlock> models;
	@Shadow
	private final Map<ModelBlockDefinition, Collection<ModelResourceLocation>> multipartVariantMap;
	@Copy(unique = true)
	private final Function<ResourceLocation, ModelBlock> GENMODEL;
	@Shadow
	private final Map<ResourceLocation, ModelBlockDefinition> blockDefinitions;

	@Inject(value = "/", at = Inject.At.TAIL)
	public AsyncModel2(IResourceManager a, TextureMap b, BlockModelShapes c) {
		super(null, null, null);
		multipartVariantMap = new ConcurrentHashMap<>();
		blockDefinitions = new ConcurrentHashMap<>();
		models = new ConcurrentHashMap<>();
		GENMODEL = (loc) -> {
			try {
				return this.loadModel(loc);
			} catch (IOException e) {
				Helpers.athrow(e);
				return null;
			}
		};
	}

	@Inject("/")
	public void loadBlock(BlockStateMapper mapper, Block block, ResourceLocation loc) {
		ModelBlockDefinition def = getModelBlockDefinition(loc);

		Map<IBlockState, ModelResourceLocation> map = mapper.getVariants(block);
		if (def.hasMultipartData()) {
			def.getMultipartData().setStateContainer(block.getBlockState());
			Collection<ModelResourceLocation> list = multipartVariantMap.computeIfAbsent(def, Helpers.fnArrayList());
			for (ModelResourceLocation tex : map.values()) {
				if (!loc.equals(tex)) continue;
				list.add(tex);
			}

			registerMultipartVariant(def, list);
		}

		for (ModelResourceLocation tex : map.values()) {
			if (!loc.equals(tex)) continue;

			try {
				this.registerVariant(def, tex);
			} catch (Exception e) {
				if (!def.hasMultipartData()) {
					LOGGER.warn("无法加载变种: " + tex.getVariant() + " from " + tex, e);
				}
			}
		}
	}

	@Inject
	private ModelBlockDefinition loadMultipartMBD(ResourceLocation loc, ResourceLocation file) {
		SimpleList<ModelBlockDefinition> list;

		try {
			List<IResource> res = this.resourceManager.getAllResources(file);
			list = new SimpleList<>(res.size());
			for (int i = 0; i < res.size(); i++) {
				list.add(loadModelBlockDefinition(loc, res.get(i)));
			}
		} catch (IOException e) {
			Helpers.athrow(e);
			return null;
		}

		return list.size() == 1 ? list.get(0) : new ModelBlockDefinition(list);
	}

	@Inject("/")
	protected void loadVariantList(ModelResourceLocation tex, VariantList vList) {
		List<Variant> list = vList.getVariantList();
		for (int i = 0; i < list.size(); i++) {
			Variant v = list.get(i);
			ResourceLocation loc = v.getModelLocation();
			try {
				models.computeIfAbsent(loc, GENMODEL);
			} catch (Exception e) {
				LOGGER.warn("无法加载方块模型: '{}' 变种: '{}': {} ", loc, tex, e);
			}
		}
	}

	@Inject("/")
	protected ModelBlockDefinition getModelBlockDefinition(ResourceLocation location) {
		ResourceLocation tex = this.getBlockstateLocation(location);
		return blockDefinitions.computeIfAbsent(tex, (tex1) -> {
			return loadMultipartMBD(location, tex1);
		});
	}

	@Shadow(value = "func_188631_b", owner = "net.minecraft.client.renderer.block.model.ModelBakery")
	private ResourceLocation getBlockstateLocation(ResourceLocation location) {
		return null;
	}

	@Shadow("func_188636_a")
	private ModelBlockDefinition loadModelBlockDefinition(ResourceLocation location, IResource resource) {
		return null;
	}
}
