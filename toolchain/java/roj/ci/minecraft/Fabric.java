package roj.ci.minecraft;

import roj.ci.Env;
import roj.ui.EasyProgressBar;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/10/09 20:56
 */
final class Fabric extends MinecraftWorkspace {
	@Override
	Env.Workspace init(EasyProgressBar bar, MinecraftClientInfo clientInfo) throws IOException {
		return null;
	}

	@Override
	boolean mergeLibraryHook(String libName, File file) {
		return false;
	}
}
