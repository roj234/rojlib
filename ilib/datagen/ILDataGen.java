package ilib.datagen;

import ilib.ImpLib;
import ilib.util.ForgeUtil;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * @author Roj234
 * @since 2022/10/29 0029 18:10
 */
@Mod(modid = "il_datagen", name = "ImpLibDataGenerator", version = ILDataGen.VERSION, acceptedMinecraftVersions = "[1.12, 1.13)",
	dependencies =
	"required" +
	":forge@[14.23.4.2768, ); required-after:ilib@[1.1.0, );", acceptableRemoteVersions = "*")
public class ILDataGen {
	public static final String VERSION = "0.1.0";

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		ForgeUtil.appendChildModTo(ImpLib.MODID);

	}
}

