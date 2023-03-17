package ilib.asm.nx.client.async;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Roj233
 * @since 2022/5/20 6:58
 */
//!!AT [["net.minecraftforge.client.model.ModelLoader$VariantLoader", ["<$extend>"]],["net.minecraftforge.client.model.ModelLoader$VanillaLoader", ["<$extend>"]]]
@Nixim("/")
class AsyncModelReg extends ModelLoaderRegistry {
	@Shadow("/")
	private static Set<ICustomModelLoader> loaders;
	@Shadow("/")
	private static Map<ResourceLocation, IModel> cache;
	@Shadow("/")
	private static Set<ResourceLocation> textures;
	@Shadow("/")
	private static Map<ResourceLocation, ResourceLocation> aliases;

	@Inject(value = "<clinit>", at = Inject.At.TAIL)
	static void afterClInit() {
		cache = new ConcurrentHashMap<>();
		textures = Collections.newSetFromMap(new ConcurrentHashMap<>());
	}

	@Inject("/")
	public static IModel getModel(ResourceLocation location) throws Exception {
		IModel cached = cache.get(location);
		if (cached != null) return cached;

		ResourceLocation alias;
		alias = aliases.get(location);
		if (alias != null) return getModel(alias);

		IModel model;
		ResourceLocation actual = getActualLocation(location);
		ICustomModelLoader accepted = null;
		for (ICustomModelLoader loader : loaders) {
			try {
				if (loader.accepts(actual)) {
					if (accepted != null) {
						throw new LoaderException(String.format("2 loaders (%s and %s) want to load the same model %s", accepted, loader, location));
					}

					accepted = loader;
				}
			} catch (Exception var16) {
				throw new LoaderException(String.format("Exception checking if model %s can be loaded with loader %s, skipping", location, loader), var16);
			}
		}

		if (accepted == null) {
			if (ModelLoader.VariantLoader.INSTANCE.accepts(actual)) {
				accepted = ModelLoader.VariantLoader.INSTANCE;
			} else {
				accepted = ModelLoader.VanillaLoader.INSTANCE;
			}
		}

		try {
			model = accepted.loadModel(actual);
		} catch (Exception e) {
			throw new LoaderException(String.format("Exception loading model %s with loader %s, skipping", location, accepted), e);
		}

		if (model == getMissingModel()) {
			throw new LoaderException(String.format("Loader %s returned missing model while loading model %s", accepted, location));
		}

		if (model == null) {
			throw new LoaderException(String.format("Loader %s returned null while loading model %s", accepted, location));
		}

		textures.addAll(model.getTextures());

		cache.put(location, model);
		for (ResourceLocation loc : model.getDependencies()) {
			getModelOrMissing(loc);
		}

		return model;
	}
}