package ilib.util;

import ilib.ImpLib;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.FMLInjectionData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ForgeUtil {
	public static String getMcVersion() {
		return (String) FMLInjectionData.data()[4];
	}

	public static boolean isInModInitialisation() {
		return !hasReachedState(LoaderState.AVAILABLE);
	}

	public static boolean hasReachedState(LoaderState state) {
		return getLoader().hasReachedState(state);
	}

	@Nonnull
	public static Loader getLoader() {
		return Loader.instance();
	}

	@Nullable
	public static ModContainer findModById(String modId) {
		Iterator<ModContainer> i = getLoader().getActiveModList().iterator();

		ModContainer mc;
		do {
			if (!i.hasNext()) return null;
			mc = i.next();
		} while (!mc.getModId().equals(modId));

		return mc;
	}

	public static String getCurrentModId() {
		ModContainer container = getCurrentMod();
		return container == null ? ImpLib.MODID : container.getModId();
	}

	public static ModContainer getCurrentMod() {
		return Loader.instance().activeModContainer();
	}

	public static void appendChildModTo(String modid) {
		ModContainer mc = ForgeUtil.findModById(modid);
		if (mc == null) throw new IllegalStateException("Unknown dependency");
		mc.getMetadata().childMods.add(getCurrentMod());
	}
}
