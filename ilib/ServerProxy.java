package ilib;

import ilib.capabilities.Capabilities;
import ilib.client.mirror.Mirror;
import ilib.client.model.BlockStateBuilder;
import ilib.event.CommonEvent;
import ilib.misc.MiscOptimize;
import ilib.util.Hook;
import ilib.util.PlayerUtil;

import net.minecraft.util.ResourceLocation;

import java.util.concurrent.Callable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ServerProxy {
	Thread serverThread;

	public BlockStateBuilder getBlockMergedModel() {
		return null;
	}

	public BlockStateBuilder getItemMergedModel() {
		return null;
	}

	public BlockStateBuilder getFluidMergedModel() {
		return null;
	}

	public void registerTexture(ResourceLocation tex) {
		throw new IllegalStateException("Wrong side");
	}

	public boolean isOnThread(boolean client) {
		return !client && Thread.currentThread() == serverThread;
	}

	public void runAtMainThread(boolean client, Runnable run) {
		PlayerUtil.getMinecraftServer().addScheduledTask(run);
	}

	public void runAtMainThread(boolean client, Callable<?> run) {
		PlayerUtil.getMinecraftServer().callFromMainThread(run);
	}

	void setServerThread(Thread t) {
		this.serverThread = t;
	}

	public Thread getServerThread() {
		return serverThread;
	}

	public Thread getClientThread() {
		return null;
	}

	void preInit() {
		CommonEvent.init();
		Capabilities.init();

		MiscOptimize.fixVanillaTool();

		if (Config.moreEggs) MiscOptimize.giveMeSomeEggs();
	}

	void init() {
		ImpLib.EVENT_BUS.remove(Hook.MODEL_REGISTER);

		Mirror.init();
	}

	void postInit() {

	}
}
