package ilib.asm.nx.client;

import ilib.asm.util.CameraAccess;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.NiximSystem;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;

/**
 * @author solo6975
 * @since 2022/5/3 1:03
 */
@Nixim(value = "net.minecraft.client.renderer.RenderGlobal", copyItf = true)
class NxGetCamera extends RenderGlobal implements CameraAccess {
	public NxGetCamera() {
		super(null);
	}

	@Copy(unique = true)
	private ICamera camera;

	@Override
	@Inject(value = "/", at = Inject.At.HEAD)
	public void setupTerrain(Entity viewEntity, double partialTicks, ICamera camera, int frameCount, boolean playerSpectator) {
		this.camera = camera;
		NiximSystem.SpecMethods.$$$VALUE_V();
	}

	@Copy
	@Override
	public ICamera getCamera() {
		return camera;
	}
}
