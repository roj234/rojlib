package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;

import java.util.Map;
import java.util.Set;

/**
 * @author solo6975
 * @since 2022/6/9 0:10
 */
@Nixim("net.minecraft.advancements.PlayerAdvancements")
class FastAdvancement {
	@Shadow
	private Map<Advancement, AdvancementProgress> progress;
	@Shadow
	private Set<Advancement> visible, visibilityChanged, progressChanged;

	@Inject
	private void ensureVisibility(Advancement a) {
		ensureVisibility0(a, 0);
	}

	@Shadow
	private boolean shouldBeVisible(Advancement a) {
		return false;
	}

	@Copy
	private void ensureVisibility0(Advancement a, int from) {
		boolean newVis = shouldBeVisible(a);
		boolean oldVis = visible.contains(a);
		if (newVis) {
			if (!oldVis) {
				visible.add(a);
				visibilityChanged.add(a);
				if (progress.containsKey(a)) {
					progressChanged.add(a);
				}
			}
		} else if (oldVis) {
			visible.remove(a);
			visibilityChanged.add(a);
		}

		if (newVis != oldVis && a.getParent() != null) {
			ensureVisibility0(a.getParent(), from == 1 ? -1 : 0);
		}

		if (from == -1) return;

		for (Advancement child : a.getChildren()) {
			ensureVisibility0(child, 1);
		}
	}
}
