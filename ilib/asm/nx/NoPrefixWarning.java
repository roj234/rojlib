package ilib.asm.nx;

import roj.asm.nixim.Dynamic;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.common.FMLContainer;
import net.minecraftforge.fml.common.InjectedModContainer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.Locale;

@Nixim("net.minecraftforge.registries.GameData")
class NoPrefixWarning {
	@Dynamic({"forge","2768"})
	@Inject("/")
	public static ResourceLocation checkPrefix(String name, boolean warnOverrides) {
		ModContainer mc = Loader.instance().activeModContainer();
		String prefix = mc != null && (!(mc instanceof InjectedModContainer) || !(((InjectedModContainer) mc).wrappedContainer instanceof FMLContainer)) ? mc.getModId()
																																							 .toLowerCase(Locale.ROOT) : "minecraft";

		int index = name.lastIndexOf(58);
		String providedPrefix = index == -1 ? "" : name.substring(0, index).toLowerCase(Locale.ROOT);
		name = index == -1 ? name : name.substring(index + 1);

		if (warnOverrides && !providedPrefix.isEmpty()) {
			prefix = providedPrefix;
		}

		return new ResourceLocation(prefix, name);
	}
}
