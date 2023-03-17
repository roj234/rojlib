package ilib.asm.nx;

import ilib.util.MCTexts;
import ilib.util.TimeUtil;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/23 17:10
 */
@Nixim("net.minecraftforge.fml.common.FMLCommonHandler")
class INFINITYMods {
	@Shadow("/")
	private List<String> brandings;
	@Shadow("/")
	private List<String> brandingsNoMC;
	@Shadow("/")
	private IFMLSidedHandler sidedDelegate;
	@Copy(unique = true)
	private int modPos;
	@Copy(unique = true)
	private int displayTick;

	@Inject("/")
	public void computeBranding() {
		if (brandings == null) {
			List<String> list = new ArrayList<>();
			list.add(Loader.instance().getMCVersionString());
			list.add(Loader.instance().getMCPVersionString());
			list.add("Forge " + ForgeVersion.getVersion());
			if (sidedDelegate != null) {
				list.addAll(sidedDelegate.getAdditionalBrandingInformation());
			}

			if (Loader.instance().getFMLBrandingProperties().containsKey("fmlbranding")) {
				list.add(Loader.instance().getFMLBrandingProperties().get("fmlbranding"));
			}

			modPos = list.size();
			int tModCount = Loader.instance().getModList().size();
			int aModCount = Loader.instance().getActiveModList().size();
			list.add(String.format("%d mods loaded, %d mods active", tModCount, aModCount));

			brandings = list;
			brandingsNoMC = brandings.subList(1, brandings.size());
		} else {
			displayTick++;
			long tModCount = Loader.instance().getModList().size();
			long aModCount = Loader.instance().getActiveModList().size();
			String modStr;
			if (displayTick < 800) {
				tModCount *= Math.pow(10, displayTick / 50f);
				aModCount += Math.pow(10, displayTick / 50f);
				modStr = tModCount + " mods loaded, " + aModCount + " mods active";
			} else {
				TimeUtil.tick++;
				modStr = MCTexts.rainbowTooltip("Infinity") + "\u00a7r mods loaded, " + MCTexts.rainbowTooltip("Infinity") + "\u00a7r mods active";
			}
			brandings.set(modPos, modStr);
		}
	}

	@Inject("/")
	public List<String> getBrandings(boolean includeMC) {
		computeBranding();
		return includeMC ? brandings : brandingsNoMC;
	}
}
