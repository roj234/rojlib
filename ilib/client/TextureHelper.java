package ilib.client;

import ilib.ClientProxy;
import ilib.Config;
import ilib.ImpLib;
import ilib.util.Hook;
import ilib.util.ReflectionClient;
import roj.collect.SimpleList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;

import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.fml.client.FMLClientHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

//!!AT [["net.minecraft.client.resources.SimpleReloadableResourceManager", ["field_110548_a", "func_110544_b"]], ["net.minecraft.client.resources.FallbackResourceManager", ["field_110540_a"]]]

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class TextureHelper implements ISelectiveResourceReloadListener {
	public static SimpleReloadableResourceManager resourceManager;
	private static ArrayList<IResourcePack> packLoad = new ArrayList<>();

	public static void preInit() {
		resourceManager = (SimpleReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();
		resourceManager.registerReloadListener(new TextureHelper());
		for (int i = 0; i < packLoad.size(); i++) {
			load0(packLoad.get(i));
		}
		packLoad = null;
	}

	public static void manualReload() {
		resourceManager.notifyReloadListeners();
	}

	public static void load(IResourcePack pack) {
		if (packLoad != null) {
			packLoad.add(pack);
		} else {
			load0(pack);
		}
	}

	private static void load0(IResourcePack pack) {
		ReflectionClient.HELPER.getResourcePackList(FMLClientHandler.instance()).add(pack);
		resourceManager.reloadResourcePack(pack);
	}

	public static List<IResourcePack> getLoadedResourcePacks() {
		List<IResourcePack> list = new SimpleList<>();
		for (FallbackResourceManager man : resourceManager.domainResourceManagers.values()) {
			list.addAll(man.resourcePacks);
		}
		return list;
	}

	@Override
	public void onResourceManagerReload(IResourceManager man, Predicate<IResourceType> pred) {
		if (pred.test(VanillaResourceType.LANGUAGES)) {
			ImpLib.EVENT_BUS.trigger(Hook.LANGUAGE_RELOAD);
			if (Config.fixFont) {
				FontRenderer fr = ClientProxy.mc.fontRenderer;
				if (fr != null) {
					fr.setUnicodeFlag(false);
					fr.setBidiFlag(false);
				}
			}
		}
	}
}