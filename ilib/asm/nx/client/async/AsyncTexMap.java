package ilib.asm.nx.client.async;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.concurrent.task.AsyncTask;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ProgressManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * @author Roj233
 * @since 2022/5/20 1:41
 */
//!!AT [["net.minecraft.client.renderer.texture.TextureAtlasSprite", ["<init>"]]]
@Nixim("/")
abstract class AsyncTexMap extends TextureMap {
	private static class MyTask extends AsyncTask<Integer> {
		private final AsyncTexMap map;
		private final int k;
		private final Map.Entry<String, TextureAtlasSprite> entry;

		public MyTask(AsyncTexMap map, Map.Entry<String, TextureAtlasSprite> entry, int k) {
			this.map = map;
			this.entry = entry;
			this.k = k;
		}

		@Override
		protected Integer invoke() throws Exception {
			return map.loadTexture(new ResourceLocation(entry.getKey()), entry.getValue(), 2147483647, k);
		}
	}

	AsyncTexMap() {
		super("");
	}

	@Shadow
	private static Logger LOGGER;
	@Shadow
	private List<TextureAtlasSprite> listAnimatedSprites;
	@Shadow
	private Map<String, TextureAtlasSprite> mapRegisteredSprites;
	@Shadow
	private Map<String, TextureAtlasSprite> mapUploadedSprites;
	@Shadow
	private int mipmapLevels;
	@Shadow("/")
	private Set<ResourceLocation> loadedSprites;

	@Copy(unique = true)
	private IResourceManager tmp0;
	@Copy(unique = true)
	private Stitcher tmp1;

	@Copy(unique = true, staticInitializer = "init1", targetIsFinal = true)
	static Function<String, TextureAtlasSprite> FN;
	@Copy(unique = true)
	Function<ResourceLocation, TextureAtlasSprite> FN_2;

	static void init1() {
		FN = TextureAtlasSprite::new;
	}

	@Inject(value = "<init>", at = Inject.At.TAIL)
	void gfd(String msg) {
		mapRegisteredSprites = new ConcurrentHashMap<>();
		loadedSprites = Collections.newSetFromMap(new ConcurrentHashMap<>());
	}

	@Inject("/")
	public void loadTextureAtlas(IResourceManager rman) {
		mapUploadedSprites.clear();
		listAnimatedSprites.clear();
		loadedSprites.clear();

		tmp0 = rman;
		int i = Minecraft.getGLMaximumTextureSize();
		Stitcher st = tmp1 = new Stitcher(i, i, 0, mipmapLevels);

		FMLLog.log.info("最大材质大小: {}", i);

		FN_2 = (l) -> {
			return mapRegisteredSprites.get(l.toString());
		};

		List<AsyncTask<Integer>> tasks = new SimpleList<>(mapRegisteredSprites.size());
		Iterator<Map.Entry<String, TextureAtlasSprite>> itr = mapRegisteredSprites.entrySet().iterator();
		while (itr.hasNext()) {
			tasks.add(new MyTask(this, itr.next(), 1 << mipmapLevels));
		}

		TaskPool pool = AsyncTexHook.pool;
		for (int j = 0; j < tasks.size(); j++) {
			pool.pushTask(tasks.get(j));
		}

		ProgressManager.ProgressBar bar = ProgressManager.push("TexUpload", mapRegisteredSprites.size() / 100 + 1);
		int lv = Integer.MAX_VALUE;
		for (int j = 0; j < tasks.size(); j++) {
			if (j % 100 == 0) bar.step(Integer.toString(j));
			try {
				lv = Math.min(tasks.get(j).get(), lv);
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}

		this.finishLoading(st, bar, lv, 1 << mipmapLevels);
	}

	@Inject(value = "/", at = Inject.At.REMOVE)
	abstract int loadTexture(Stitcher a, IResourceManager b, ResourceLocation c, TextureAtlasSprite d, ProgressManager.ProgressBar e, int j, int k);

	@Copy(unique = true)
	public int loadTexture(ResourceLocation location, TextureAtlasSprite tex, int j, int k) {
		if (this.loadedSprites.contains(location)) return j;

		ResourceLocation path = this.getResourceLocation(tex);
		IResource res = null;

		try {
			Iterator<ResourceLocation> itr;
			itr = tex.getDependencies().iterator();
			while (itr.hasNext()) {
				ResourceLocation dep = itr.next();
				TextureAtlasSprite depTex = mapRegisteredSprites.computeIfAbsent(dep.toString(), FN);
				j = loadTexture(dep, depTex, j, k);
			}

			try {
				if (tex.hasCustomLoader(tmp0, path)) {
					if (tex.load(tmp0, path, FN_2)) {
						return j;
					}
				} else {
					PngSizeInfo inf = PngSizeInfo.makeFromResource(tmp0.getResource(path));
					res = tmp0.getResource(path);
					tex.loadSprite(inf, res.getMetadata("animation") != null);
				}
			} catch (RuntimeException e) {
				FMLClientHandler.instance().trackBrokenTexture(path, e.getMessage());
				return j;
			} catch (IOException e) {
				FMLClientHandler.instance().trackMissingTexture(path);
				return j;
			} finally {
				IOUtils.closeQuietly(res);
			}

			j = Math.min(j, Math.min(tex.getIconWidth(), tex.getIconHeight()));
			int minl = Math.min(Integer.lowestOneBit(tex.getIconWidth()), Integer.lowestOneBit(tex.getIconHeight()));
			if (minl < k) {
				LOGGER.warn(
					"Texture {} with size {}x{} will have visual artifacts at mip level {}, it can only support level {}. Please report to the mod author that the texture should be some multiple of 16x16.",
					path, tex.getIconWidth(), tex.getIconHeight(), MathHelper.log2(k), MathHelper.log2(minl));
			}

			if (generateMipmaps(tmp0, tex)) {
				tmp1.addSprite(tex);
			}

			return j;
		} finally {
			loadedSprites.add(location);
		}
	}

	@Shadow("/")
	private void finishLoading(Stitcher stitcher, ProgressManager.ProgressBar bar, int j, int k) {

	}

	@Shadow
	private boolean generateMipmaps(IResourceManager man, TextureAtlasSprite texture) {
		return false;
	}

	@Shadow
	private ResourceLocation getResourceLocation(TextureAtlasSprite p_184396_1_) {
		return null;
	}
}
