package ilib.util;

import roj.reflect.DirectAccessor;

import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.entity.Entity;

import net.minecraftforge.fml.client.FMLClientHandler;

import java.util.List;

/**
 * @author Roj233
 * @since 2021/8/25 1:06
 */
public interface ReflectionClient {
	ReflectionClient HELPER = preloadNecessaryClassesBeforeDefine(DirectAccessor.builder(ReflectionClient.class))
		.access(GuiMainMenu.class, "field_73975_c", null, "setMainMenuSplash")
		.access(FMLClientHandler.class, "resourcePackList")
		.access(SoundHandler.class, "field_147694_f", "getSoundManager", null)
		.access(SoundManager.class, "field_148617_f", "getInited", null)
		.delegate(Render.class, "func_177067_a", "renderName")
		.build();

	static DirectAccessor<ReflectionClient> preloadNecessaryClassesBeforeDefine(DirectAccessor<ReflectionClient> builder) {
		return builder;
	}

	// region Field
	void setMainMenuSplash(GuiMainMenu menu, String splash);

	List<IResourcePack> getResourcePackList(FMLClientHandler handler);

	void setResourcePackList(FMLClientHandler handler, List<IResourcePack> list);

	SoundManager getSoundManager(SoundHandler h);

	boolean getInited(SoundManager h);

	// endregion
	// region Method
	void renderName(Render<Entity> r, Entity e, double x, double y, double z);
	// endregion
}
