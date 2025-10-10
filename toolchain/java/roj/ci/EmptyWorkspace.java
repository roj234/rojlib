package roj.ci;

import roj.config.node.MapValue;

/**
 * @author Roj234
 * @since 2025/2/12 3:47
 */
public final class EmptyWorkspace {
	public static Env.Workspace build(MapValue config) {
		var workspace = new Env.Workspace();
		workspace.type = "empty";
		workspace.id = "empty";
		return workspace;
	}
}
