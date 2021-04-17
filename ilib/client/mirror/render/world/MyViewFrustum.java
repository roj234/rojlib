package ilib.client.mirror.render.world;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2022/4/28 13:46
 */
public class MyViewFrustum extends ViewFrustum {
	public long lastActive;
	public double frustumUpdatePosX = 4.9E-324D, frustumUpdatePosY = 4.9E-324D, frustumUpdatePosZ = 4.9E-324D;
	public int frustumUpdatePosChunkX = -2147483648, frustumUpdatePosChunkY = -2147483648, frustumUpdatePosChunkZ = -2147483648;

	public MyViewFrustum(World w, int rd, RenderGlobal rg, IRenderChunkFactory f) {
		super(w, rd, rg, f);
		lastActive = System.currentTimeMillis();
	}
}
