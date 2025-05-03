package roj.plugins.ci;

import roj.config.data.CMap;

import java.util.Collections;

/**
 * @author Roj234
 * @since 2025/2/12 3:47
 */
public final class EmptyWorkspace {
	public static Workspace build(CMap config) {
		var workspace = new Workspace();
		workspace.type = "Empty";
		workspace.id = "Empty";
		workspace.depend = Collections.emptyList();
		workspace.mappedDepend = Collections.emptyList();
		workspace.unmappedDepend = Collections.emptyList();
		workspace.mapping = null;
		return workspace;
	}
}
