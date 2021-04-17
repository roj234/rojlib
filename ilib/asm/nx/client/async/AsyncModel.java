package ilib.asm.nx.client.async;

import ilib.ImpLib;
import ilib.util.ForgeUtil;
import ilib.util.Registries;
import org.apache.logging.log4j.Logger;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.concurrent.task.AsyncTask;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.ProgressManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Roj233
 * @since 2022/5/20 5:51
 */
//!!AT [["net.minecraftforge.client.model.ModelLoaderRegistry", ["addAlias", "getMissingModel"]],["net.minecraftforge.client.model.ModelLoader$ItemLoadingException", ["<$extend>", "*"]]]
@Nixim(value = "net.minecraftforge.client.model.ModelLoader", flag = Nixim.COPY_ACCESSOR)
class AsyncModel extends ModelBakery {
	private static final class MyTask extends AsyncTask<Void> {
		private final AsyncModel mb;
		private final BlockStateMapper bsm;
		private Block block;
		private List<String> item;

		public MyTask(AsyncModel mb, BlockStateMapper bsm, Block block) {
			this.mb = mb;
			this.bsm = bsm;
			this.block = block;
		}

		public MyTask(AsyncModel mb, BlockStateMapper bsm, List<String> item) {
			this.mb = mb;
			this.bsm = bsm;
			this.item = item;
		}

		@Override
		protected Void invoke() throws Exception {
			if (block != null) {
				for (ResourceLocation tex : bsm.getBlockstateLocations(block)) {
					try {
						mb.loadBlock(bsm, block, tex);
					} catch (Exception e) {
						ImpLib.logger().warn("无法加载模型 " + tex, e);
					}
				}
			} else {
				for (int i = 0; i < item.size(); i++) {
					String s = item.get(i);
					ModelResourceLocation bs = ModelLoader.getInventoryVariant(s);

					IModel model;
					try {
						model = ModelLoaderRegistry.getModel(bs);
					} catch (Exception e) {
						ResourceLocation file = mb.getItemLocation(s);
						try {
							model = ModelLoaderRegistry.getModel(file);
							ModelLoaderRegistry.addAlias(bs, file);
						} catch (Exception e2) {
							Exception e3 = new ModelLoader.ItemLoadingException("无法加载模型从 " + file + " 或blockstate ", e2, e);
							mb.storeException(bs, e3);
							model = ModelLoaderRegistry.getMissingModel(bs, e3);
						}
					}

					mb.stateModels.put(bs, model);
				}
			}
			return null;
		}
	}

	@Shadow(value = "field_177603_c", owner = "net.minecraft.client.renderer.block.model.ModelBakery")
	private static Logger LOGGER;
	@Shadow("/")
	private final Map<ModelResourceLocation, IModel> stateModels;
	@Shadow("/")
	private final Map<ResourceLocation, Exception> loadingExceptions;
	@Copy(unique = true)
	private SimpleList<MyTask> tasks;

	@Inject(value = "/", at = Inject.At.TAIL)
	AsyncModel(IResourceManager a, TextureMap b, BlockModelShapes c) {
		super(null, null, null);
		stateModels = new ConcurrentHashMap<>();
		loadingExceptions = new ConcurrentHashMap<>();
	}

	@Inject("/")
	protected void loadBlocks() {
		int size = Registries.block().getEntries().size();
		List<MyTask> tasks = this.tasks = new SimpleList<>(size);
		TaskPool pool = AsyncTexHook.pool;

		BlockStateMapper bsm = blockModelShapes.getBlockStateMapper();
		for (Block block : Block.REGISTRY) {
			if (block.getRegistryName() == null) continue;
			MyTask e = new MyTask(this, bsm, block);
			tasks.add(e);
			pool.pushTask(e);
		}
	}

	@Inject("/")
	protected void loadItemModels() {
		this.registerVariantNames();

		int size = Registries.item().getEntries().size();
		SimpleList<MyTask> tasks = this.tasks;
		tasks.ensureCapacity(tasks.size() + size);
		TaskPool pool = AsyncTexHook.pool;

		BlockStateMapper bsm = this.blockModelShapes.getBlockStateMapper();
		for (Item item : Item.REGISTRY) {
			if (item.getRegistryName() == null) continue;
			MyTask task = new MyTask(this, bsm, getVariantNames(item));
			tasks.add(task);
			pool.pushTask(task);
		}

		ProgressManager.ProgressBar bar = ProgressManager.push("Wait Async Task", tasks.size());
		for (int j = 0; j < tasks.size(); j++) {
			bar.step(Integer.toString(j));
			try {
				tasks.get(j).get(1000, TimeUnit.MILLISECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				pool.clearTasks();
				Arrays.stream(pool.threads()).forEach(Thread::stop);
				pool.shutdown();

				ForgeUtil.getCurrentMod().getMetadata().name = "您的魔族列表可能不支持异步材质加载";
				throw new IllegalArgumentException("您的魔族列表可能不支持异步材质加载");
			}
		}
		ProgressManager.pop(bar);
	}

	@Inject(value = "/", at = Inject.At.REPLACE)
	protected ModelBlockDefinition getModelBlockDefinition(ResourceLocation location) {
		try {
			return super.getModelBlockDefinition(location);
		} catch (Exception var3) {
			this.storeException(location, new Exception("无法加载变种的模型 " + location, var3));
			return new ModelBlockDefinition(Collections.emptyList());
		}
	}

	@Shadow(value = "func_188632_a", owner = "net.minecraft.client.renderer.block.model.ModelBakery")
	private ModelBlockDefinition loadMultipartMBD(ResourceLocation loc, ResourceLocation file) {
		return null;
	}

	@Shadow("/")
	private void storeException(ResourceLocation tex, Exception ex) {}
}
