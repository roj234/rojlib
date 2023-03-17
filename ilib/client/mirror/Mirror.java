package ilib.client.mirror;

import ilib.ImpLib;
import ilib.client.mirror.render.WorldRenderer;
import ilib.net.MyChannel;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class Mirror {
	public static int maxRecursion = 2;
	public static int maxRenderPerTick = 10;
	public static int maxRenderDistanceChunks = 6;
	public static int minStencilLevel = 1;

	public static MyChannel CHANNEL = new MyChannel("prt");

	public static void init() {
		CHANNEL.registerMessage(null, PktChunkData.class, 0, Side.CLIENT);
		CHANNEL.registerMessage(null, PktNewWorld.class, 1, Side.CLIENT);

		if (ImpLib.isClient) initClient();
	}

	@SideOnly(Side.CLIENT)
	private static void initClient() {
		MinecraftForge.EVENT_BUS.register(WorldRenderer.class);
	}
}
