package ilib.client;

import ilib.Config;
import ilib.ImpLib;
import ilib.ServerProxy;
import ilib.client.music.GuiFunction;
import ilib.client.music.GuiMusic;
import ilib.gui.GuiHelper;
import ilib.misc.EntityXRay;
import ilib.misc.MCHooks;
import ilib.misc.XRay;
import ilib.util.ChatColor;
import ilib.util.PlayerUtil;
import org.lwjgl.input.Keyboard;

import net.minecraft.client.settings.KeyBinding;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

import static ilib.ClientProxy.mc;
import static net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding;

/**
 * @author solo6975
 * @since 2022/4/3 23:54
 */
public class KeyRegister {
	public static final String KEY_CATEGORY_ILIB = "key.categories.ilib";

	public static KeyBinding keyShowLight = new KeyBinding("key.ilib.light", 0, KEY_CATEGORY_ILIB);
	public static KeyBinding keyMusicPlayer = new KeyBinding("key.ilib.music", 0, KEY_CATEGORY_ILIB);
	public static KeyBinding keyZoomIn = new KeyBinding("key.ilib.zoom_in", 0, KEY_CATEGORY_ILIB);
	public static KeyBinding keyZoomOut = new KeyBinding("key.ilib.zoom_out", 0, KEY_CATEGORY_ILIB);
	public static KeyBinding keyFastPlace = new KeyBinding("key.ilib.fast_place", 0, KEY_CATEGORY_ILIB);
	public static KeyBinding keyFunction = new KeyBinding("key.ilib.function", 0, KEY_CATEGORY_ILIB);

	public static KeyBinding keyDismount = new KeyBinding("key.dismount", Keyboard.KEY_LSHIFT, "key.categories.movement");


	public static KeyBinding keyDebugEvent = new KeyBinding("调试", 0, KEY_CATEGORY_ILIB);


	public static KeyBinding keyXRay = new KeyBinding("xray", 0, KEY_CATEGORY_ILIB);
	public static KeyBinding keyEntityXRay = new KeyBinding("entity_xray", 0, KEY_CATEGORY_ILIB);
	public static KeyBinding keyFreeCam = new KeyBinding("free_cam", 0, KEY_CATEGORY_ILIB);

	public static void init() {
		registerKeyBinding(keyShowLight);
		registerKeyBinding(keyMusicPlayer);
		registerKeyBinding(keyZoomIn);
		registerKeyBinding(keyZoomOut);
		registerKeyBinding(keyFastPlace);
		registerKeyBinding(keyFunction);

		if ((Config.debug & 128) != 0) {
			registerKeyBinding(keyXRay);
			registerKeyBinding(keyEntityXRay);
			registerKeyBinding(keyFreeCam);
		}

		if ((Config.debug & 64) != 0) {
			registerKeyBinding(keyDebugEvent);
		}

		if (Config.separateDismount) {
			registerKeyBinding(keyDismount);
		}

		MinecraftForge.EVENT_BUS.register(KeyRegister.class);
	}

	public static int rightClickDelay = 4;
	public static boolean shouldDrawLight;

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (keyMusicPlayer.isPressed()) {
			mc.displayGuiScreen(new GuiMusic(mc.currentScreen));
		}
		if (keyShowLight.isPressed()) {
			shouldDrawLight = !shouldDrawLight;
		}
		if (keyXRay.isPressed()) {
			XRay.toggle();
		}
		if (keyEntityXRay.isPressed()) {
			EntityXRay.toggle();
		}
		if (keyFreeCam.isPressed()) {
			// not finished yet
		}
		if (keyFunction.isPressed()) {
			GuiHelper.openClientGui(new GuiFunction());
		}
		if (keyDebugEvent.isPressed()) {
			try {
				Field f = ServerProxy.class.getDeclaredField("serverThread");
				f.setAccessible(true);
				MCHooks.traceNew = MCHooks.traceNew == null ? (Thread) f.get(ImpLib.proxy) : null;
				System.err.println(MCHooks.traceNew);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				e.printStackTrace();
			}
			//DebugEvent.init();
		}
		if (keyFastPlace.isPressed()) {
			rightClickDelay = rightClickDelay % 4 + 1;
			PlayerUtil.sendTo(null, ChatColor.GREY + " 当前右键延迟: " + ChatColor.ORANGE + rightClickDelay + "tick");
		}
	}
}
