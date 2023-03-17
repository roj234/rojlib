package ilib.asm.nx.client.async;

import com.google.common.collect.Maps;
import roj.collect.MyHashSet;
import roj.concurrent.TaskPool;
import roj.opengl.texture.TextureManager;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
import net.minecraft.util.ResourceLocation;

import java.util.IdentityHashMap;

/**
 * @author Roj233
 * @since 2022/5/20 5:45
 */
public class AsyncTexHook {
	public static final ThreadLocal<AsyncTexHook> local = ThreadLocal.withInitial(AsyncTexHook::new);
	public static final TaskPool pool = new TaskPool(0, Math.min(Runtime.getRuntime().availableProcessors() * 2, 24), 1, 4000, "材质上传");
	public final TextureManager tman = new TextureManager();
	public IdentityHashMap<IBlockState, ModelResourceLocation> models = Maps.newIdentityHashMap();
	public MyHashSet<ResourceLocation> paths = new MyHashSet<>();
	public IStateMapper def = new MyDefMap();
}
