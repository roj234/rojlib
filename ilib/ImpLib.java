package ilib;

import com.google.common.collect.Multimap;
import ilib.asm.Loader;
import ilib.asm.NiximProxy;
import ilib.asm.util.XiBaoHelper;
import ilib.block.BlockLootrChest;
import ilib.client.AutoFPS;
import ilib.client.model.ILItemModel;
import ilib.collect.MyImmutableMultimap;
import ilib.command.CommandItemNBT;
import ilib.command.CommandListenerIL;
import ilib.command.CommandPlayerNBT;
import ilib.command.MasterCommand;
import ilib.command.sub.CmdILFill;
import ilib.command.sub.CmdSchematic;
import ilib.command.sub.CmdSubCmd;
import ilib.command.sub.MySubs;
import ilib.event.ClientEvent;
import ilib.event.CommonEvent;
import ilib.event.LootrEvent;
import ilib.event.PowershotEvent;
import ilib.item.ItemBlockMI;
import ilib.item.ItemSelectTool;
import ilib.misc.MiscOptimize;
import ilib.net.AddMinecraftPacket;
import ilib.util.BlockHelper;
import ilib.util.ForgeUtil;
import ilib.util.Hook;
import ilib.util.Registries;
import ilib.util.freeze.FreezeRegistryInjector;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.async.ThreadNameCachingStrategy;
import org.apache.logging.log4j.core.impl.ReusableLogEventFactory;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.io.NIOUtil;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.Helpers;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.item.Item;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.SplashProgress;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.asm.transformers.AccessTransformer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.patcher.ClassPatchManager;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.jar.Manifest;

/***<pre>
 *                    _ooOoo_
 *                   o8888888o
 *                   88" . "88
 *                   (| -_- |)
 *                    O\ = /O
 *                ____/`---'\____
 *              .   ' \\| |// `.
 *               / \\||| : |||// \
 *             / _||||| -:- |||||- \
 *               | | \\\ - /// | |
 *             | \_| ''\---/'' | |
 *              \ .-\__ `-` ___/-. /
 *           ___`. .' /--.--\ `. . __
 *        ."" '< `.___\_<|>_/___.' >'"".
 *       | | : `- \`.;`\ _ /`;.`/ - ` : | |
 *         \ \ `-. \_ __\ /__ _/ .-` / /
 * ======`-.____`-.___\_____/___.-`____.-'======
 *                    `=---='
 *
 * .............................................
 *          佛祖保佑             永无BUG
 */
@Mod(modid = ImpLib.MODID, name = ImpLib.NAME, version = ImpLib.VERSION, acceptedMinecraftVersions = "[1.12, 1.13)", dependencies = "required:forge@[14.23.4.2768,);", acceptableRemoteVersions = "*")
public class ImpLib {
	public static final String MODID = "ilib";
	public static final String NAME = "又一个优化mod";
	public static final String VERSION = "1.1.5";

	public static final boolean isClient = FMLCommonHandler.instance().getEffectiveSide().isClient();
	public static final Hook EVENT_BUS = Loader.EVENT_BUS;
	public static final List<CommandBase> COMMANDS = new SimpleList<>();

	public static Logger logger() {
		return Loader.logger;
	}

	@SidedProxy(serverSide = "ilib.ServerProxy", clientSide = "ilib.ClientProxy")
	public static ServerProxy proxy;

	static {
		Thread.currentThread().setName(isClient ? "客" : "服");
		simplifyThreadName();
		Loader.onModConstruct();
	}

	public static void simplifyThreadName() {
		try {
			Field nameField = ThreadNameCachingStrategy.class.getDeclaredField("THREADLOCAL_NAME");
			nameField.setAccessible(true);
			((ThreadLocal<?>) nameField.get(null)).set(null);
			Field logEventField = ReusableLogEventFactory.class.getDeclaredField("mutableLogEventThreadLocal");
			logEventField.setAccessible(true);
			((ThreadLocal<?>) logEventField.get(null)).set(null);
		} catch (NoClassDefFoundError | ReflectiveOperationException e) {
			logger().error("无法刷新线程缓存,日志中的线程可能不准确", e);
		}
	}

	@EventHandler
	@SuppressWarnings("unchecked")
	public void preInit(FMLPreInitializationEvent event) {
		// region 处理注解
		Set<ASMDataTable.ASMData> table = Loader.Annotations.getAll("ilib.api.TileRegister");
		if (table != null) {
			for (ASMDataTable.ASMData c : table) {
				String cn = c.getClassName();
				try {
					Class<?> clazz = Class.forName(cn, false, Launch.classLoader);
					String str = (String) c.getAnnotationInfo().get("value");
					if (str.equals("")) {
						str = clazz.getSimpleName().toLowerCase();
						if (str.startsWith("tileentity")) {str = str.substring(10);} else if (str.startsWith("tile")) str = str.substring(4);
						str = ForgeUtil.getCurrentModId() + ':' + str;
					}
					TileEntity.register(str, (Class<? extends TileEntity>) clazz);
				} catch (ClassNotFoundException e) {
					throw new RuntimeException("@TileRegister #" + cn + " is failed to load.");
				}
			}
		}

		table = Loader.Annotations.getAll("ilib.net.AddMinecraftPacket");
		if (table != null) {
			for (ASMDataTable.ASMData c : table) {
				String cn = c.getClassName();
				try {
					Class<?> clazz = Class.forName(cn, false, Launch.classLoader);
					Map<String, Object> info = c.getAnnotationInfo();
					int v = (Integer) info.getOrDefault("value", AddMinecraftPacket.ANY);
					ModAnnotation.EnumHolder h = (ModAnnotation.EnumHolder) info.get("state");
					ATHandler.registerNetworkPacket(EnumConnectionState.valueOf(h == null ? "PLAY" : h.getValue()), Helpers.cast(clazz), v == 0 ? null : v == AddMinecraftPacket.TO_CLIENT);
				} catch (ClassNotFoundException e) {
					throw new RuntimeException("@TileRegister #" + cn + " is failed to load.");
				}
			}
		}
		// endregion

		ModMetadata m = event.getModMetadata();
		m.autogenerated = false;
		m.modId = ImpLib.MODID;
		m.version = ImpLib.VERSION;
		m.name = ImpLib.NAME;
		m.credits = "Asyncorized_MC";
		m.authorList.clear();
		m.authorList.add("Roj234");
		m.description = "Improve your minecraft";
		m.url = "https://www.mcmod.cn/";
		m.logoFile = "logo.png";

		proxy.preInit();

		EVENT_BUS.triggerOnce("preInit");

		if (Config.registerItem) {
			Item item = new ItemSelectTool();
			Registries.item().register(item.setRegistryName(MODID, "select_tool"));

			ILItemModel.Merged(item, 0, "select_tool");
			ILItemModel.Merged(item, 1, "speed_modifier");
			ILItemModel.Merged(item, 2, "place_here");
		}

		if (Config.xiBao != 0) {
			MinecraftForge.EVENT_BUS.register(XiBaoHelper.class);
		}

		if (Config.lootR) {
			MinecraftForge.EVENT_BUS.register(LootrEvent.class);
			Block block = new BlockLootrChest();
			Register.block("chest_loot", block, new ItemBlockMI(block), null, 24, true);
		}

		MiscOptimize.attributeRangeSet(Config.setAttributeRange);

		List<String> errors = Config.checkIncompatibleMod();
		Config.instance.save();

		if (!errors.isEmpty()) {
			errors.add("");
			errors.add("上述功能已经自动关闭，但是需要重启MC才能应用更改");
			errors.add("按确认重启MC");

			try {
				JOptionPane.showMessageDialog(null, String.join("\n", errors), "《Minecraft》需要重启", JOptionPane.ERROR_MESSAGE);
			} catch (Throwable onServer) {}

			errors.add(0, "《Minecraft》需要重启:");
			String info = String.join("\n", errors);
			ForgeUtil.getCurrentMod().getMetadata().name = info;
			throw new IllegalStateException(info);
		}
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		proxy.init();
		EVENT_BUS.triggerOnce("init");
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		if (Config.powerShotRequirement != null) {
			MinecraftForge.EVENT_BUS.register(PowershotEvent.class);
			for (Map.Entry<String, CEntry> entry : Config.powerShotRequirement) {
				IBlockState state = BlockHelper.stateFromText(entry.getKey());
				PowershotEvent.powerRequirement.put(state, entry.getValue().asDouble());
			}
		}

		if (net.minecraftforge.fml.common.Loader.isModLoaded("astralsorcery")) {
			try {
				Class.forName("hellfirepvp.astralsorcery.common.constellation.perk.PerkEffectHelper");
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		if (Config.freezeUnknownEntries.contains("item") ||
			Config.freezeUnknownEntries.contains("block") ||
			Config.freezeUnknownEntries.contains("entity")) {
			try {
				FreezeRegistryInjector.inject();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("无法完成冻结的初始化操作,可能是forge版本异常！", e);
			}
		}

		// 删去了FastRecipe初始化(移动到了NxFastWorkbench.<clinit>)
		// 原因/目的: 加强模块耦合, MCHooks这种class本来不应该出现

		proxy.postInit();
		EVENT_BUS.triggerOnce("postInit");
	}

	@EventHandler
	public void onLoadFinish(FMLLoadCompleteEvent event) {
		if (Config.isTrashEnable) cleanTrash();

		logger().info("启动耗时: {}ms", System.currentTimeMillis() - Loader.beginTime);
		logger().info("Nixim剩余: " + NiximProxy.instance.getRegistry().values());

		if (Config.fpsLowTime > 0) {
			AutoFPS.init(Config.fpsLowTime, Config.fpsLowFPS, Config.fpsLowVol);
		}

		EVENT_BUS.triggerOnce("LoadComplete");

		if (Config.reloadSound && isClient) ClientEvent.reloadSoundMgr(false);

		COMMANDS.add(new CommandItemNBT());
		COMMANDS.add(new CommandPlayerNBT());
		COMMANDS.add(new MasterCommand("implib", 3).aliases("il")
												   .register(MySubs.CHECK_OD)
												   .register(new CmdSchematic())
												   .register(MySubs.REGEN)
												   .register((new CmdSubCmd("debug"))
													   .setHelp("command.mi.help.debug.1")
													   .register(MySubs.RENDER_INFO)
													   .register(MySubs.BLOCK_UPDATE)
													   .register(MySubs.TILE_TEST))
												   .register(new CmdILFill())
												   .register(MySubs.GC)
												   .register(MySubs.UNLOAD_CHUNKS)
												   .register(MySubs.TPS_CHANGE));
	}

	private static MTIssueFixer MTF_1;

	@EventHandler
	public void onServerStart(FMLServerAboutToStartEvent event) {
		proxy.setServerThread(Thread.currentThread());

		if (isClient) {
			Thread.currentThread().setName("服");
			simplifyThreadName();

			if (Config.fixThreadIssues && MTF_1 == null) {
				MTF_1 = new MTIssueFixer();
				MTF_1.start();
			}
			event.getServer().setOnlineMode(false);
		}
	}

	@EventHandler
	public void onServerStart(FMLServerStartingEvent event) {
		if (MTF_1 != null) {
			MTF_1.trigger = 2;
			LockSupport.unpark(MTF_1);
		}

		EVENT_BUS.trigger("ServerStart");
		CommonEvent.onServerStart(event.getServer());

		CommandListenerIL listener = new CommandListenerIL(event.getServer());
		for (int i = 0; i < COMMANDS.size(); i++) {
			listener.serverCmd.registerCommand(COMMANDS.get(i));
		}
	}

	@EventHandler
	public void onServerStart(FMLServerStoppingEvent event) {
		EVENT_BUS.trigger("ServerStop");
		proxy.setServerThread(null);
	}

	@SuppressWarnings("unchecked")
	private static void cleanTrash() {
		FieldAccessor acc;
		try {
			LaunchClassLoader loader = Launch.classLoader;

			acc = ReflectionUtils.access(ReflectionUtils.getField(AccessTransformer.class, "modifiers"));

			List<IClassTransformer> transformers = loader.getTransformers();
			for (int i = 0; i < transformers.size(); i++) {
				IClassTransformer t = transformers.get(i);
				if (t instanceof AccessTransformer) {
					Multimap<String, Object> map = (Multimap<String, Object>) acc.getObject(t);

					if (map != null) {
						if (!(map instanceof MyImmutableMultimap)) {
							acc.setObject(t, new MyImmutableMultimap(map));
							logger().info("固定的AT Entry: " + map.size());
						}
					}
				}
			}

			int count = 0;
			try {
				Map<String, byte[]> classCache = ReflectionUtils.getValue(loader, "resourceCache");
				count += classCache.size();
				classCache.clear();
			} catch (Throwable ignored) {}

			try {
				Map<Package, Manifest> manifestCache = ReflectionUtils.getValue(loader, "packageManifests");
				count += manifestCache.size();
				manifestCache.clear();
			} catch (Throwable ignored) {}

			logger().info("清除的资源/manifest缓存: " + count);
		} catch (Exception e) {
			logger().warn("无法清除缓存1", e);
		}

		try {
			Map<String, byte[]> patchedClasses = ReflectionUtils.getValue(ClassPatchManager.class, "patchedClasses");
			logger().info("清除的补丁缓存: " + patchedClasses.size());
			patchedClasses.clear();
		} catch (Throwable e) {
			logger().warn("无法清除缓存2", e);
		}

		if (isClient) {
			try {
				NIOUtil.clean(ReflectionUtils.getValue(null, SplashProgress.class, "buf"));
				logger().info("清除Splash剩下的缓冲区: 16MB");
			} catch (Exception e) {
				logger().warn("无法清除缓存3", e);
			}
		}

		try {
			Class<?> mixinClz = Class.forName("org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo$Map");

			acc = ReflectionUtils.access(ReflectionUtils.getField(mixinClz, "NO_GROUP"));

			Object loader = acc.getObject(null);

			acc = ReflectionUtils.access(ReflectionUtils.getField(loader.getClass(), "members"));

			List<?> injectCache = (List<?>) acc.getObject(loader);
			logger().info("清除了Mixin group: " + injectCache.size());
			injectCache.clear();
		} catch (ClassNotFoundException ignored) {
		} catch (Exception e) {
			logger().warn("无法清除缓存4", e);
		}

		try {
			Class<?> cInfo = Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo");
			acc = ReflectionUtils.access(ReflectionUtils.getField(cInfo, "cache"));

			Map<String, ?> cache = (Map<String, ?>) acc.getObject(null);

			final String OBJECT = "java/lang/Object";
			Object o = cache.get(OBJECT);
			cache.clear();
			cache.put(OBJECT, Helpers.cast(o));
		} catch (ClassNotFoundException ignored) {
		} catch (Exception e) {
			logger().warn("无法清除缓存5", e);
		}
	}
}
