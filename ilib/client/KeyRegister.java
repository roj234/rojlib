package ilib.client;

import ilib.Config;
import ilib.asm.nixim.debug.NxStorm;
import ilib.client.music.GuiMusic;
import ilib.event.DebugEvent;
import ilib.misc.EntityXRay;
import ilib.misc.XRay;
import ilib.util.Colors;
import ilib.util.EntityHelper;
import ilib.util.PlayerUtil;
import ilib.world.StormHandler;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import roj.dev.HRAgent;
import roj.math.Vec3d;

import static ilib.ClientProxy.mc;

/**
 * @author solo6975
 * @since 2022/4/3 23:54
 */
public class KeyRegister {
    public static final String KEY_CATEGORY_ILIB = "key.categories.ilib";

    public static KeyBinding keyShowLight = new KeyBinding("key.ilib.light", 0, KEY_CATEGORY_ILIB);
    public static KeyBinding keyMusicPlayer = new KeyBinding("key.ilib.music", 0, KEY_CATEGORY_ILIB);
    public static KeyBinding keyZoomIn = new KeyBinding("key.ilib.zoom_in", /*Keyboard.KEY_MINUS*/0, KEY_CATEGORY_ILIB);
    public static KeyBinding keyZoomOut = new KeyBinding("key.ilib.zoom_out", /*Keyboard.KEY_EQUALS*/0, KEY_CATEGORY_ILIB);
    public static KeyBinding keyFastPlace = new KeyBinding("key.ilib.fast_place", 0, KEY_CATEGORY_ILIB);


    public static KeyBinding keyAttachHR = new KeyBinding("重新挂载HRAgent", 0, KEY_CATEGORY_ILIB);
    public static KeyBinding keyDebugEvent = new KeyBinding("事件系统调试", 0, KEY_CATEGORY_ILIB);
    public static KeyBinding keyDebugRain = new KeyBinding("气候系统调试", 0, KEY_CATEGORY_ILIB);


    public static KeyBinding keyXRay = new KeyBinding("xray", 0, KEY_CATEGORY_ILIB);
    public static KeyBinding keyEntityXRay = new KeyBinding("entity_xray", 0, KEY_CATEGORY_ILIB);
    public static KeyBinding keyFreeCam = new KeyBinding("free_cam", 0, KEY_CATEGORY_ILIB);

    public static void init() {
        ClientRegistry.registerKeyBinding(keyShowLight);
        ClientRegistry.registerKeyBinding(keyMusicPlayer);
        ClientRegistry.registerKeyBinding(keyZoomIn);
        ClientRegistry.registerKeyBinding(keyZoomOut);
        ClientRegistry.registerKeyBinding(keyFastPlace);

        if ((Config.debug & 128) != 0) {
            ClientRegistry.registerKeyBinding(keyXRay);
            ClientRegistry.registerKeyBinding(keyEntityXRay);
            ClientRegistry.registerKeyBinding(keyFreeCam);
        }

        if ((Config.debug & 64) != 0) {
            ClientRegistry.registerKeyBinding(keyAttachHR);
            ClientRegistry.registerKeyBinding(keyDebugEvent);
            ClientRegistry.registerKeyBinding(keyDebugRain);
        }

        MinecraftForge.EVENT_BUS.register(KeyRegister.class);
    }

    public static int rightClickDelay = 4;
    public static boolean shouldDrawLight;

    public static Vec3d stormDirection = new Vec3d();
    public static float stormStrength;

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
        if (keyDebugEvent.isPressed()) {
            DebugEvent.init();
        }
        if (keyFastPlace.isPressed()) {
            rightClickDelay = rightClickDelay % 4 + 1;
            PlayerUtil.sendTo(null, Colors.GREY + " 当前右键延迟: " + Colors.ORANGE + rightClickDelay + "tick");
        }
        if (keyDebugRain.isPressed()) {
            MinecraftForge.EVENT_BUS.register(StormHandler.class);
            if (stormStrength == 0) {
                if (NxStorm.MyStormHandler.msp.lastStrength > 0) {
                    NxStorm.MyStormHandler.msp.lastStrength = 0;
                    return;
                }

                stormDirection = EntityHelper.vec(mc.player.getLookVec());

                float i = 0;
                NonNullList<ItemStack> inv = mc.player.inventory.mainInventory;
                for (int j = 0; j < inv.size(); j++) {
                    i += inv.get(j).getCount() / 10f;
                }
                stormStrength = i;
            } else {
                if (NxStorm.MyStormHandler.msp.lastStrength < stormStrength) {
                    NxStorm.MyStormHandler.msp.lastStrength = stormStrength;
                    return;
                }
                stormStrength = 0;
            }
        }
        if (keyAttachHR.isPressed()) {
            if (!HRAgent.isLoaded()) {
                HRAgent.premain("", null);
                PlayerUtil.sendTo(null, "尝试在本地4485端口上重新连接HR Server");
            }
        }
    }
}
