/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ilib;

import com.google.common.collect.Multimap;
import ilib.asm.Loader;
import ilib.asm.fasterforge.transformers.EventSubscriberTransformer;
import ilib.asm.fasterforge.transformers.SideTransformer;
import ilib.block.BlockLootrChest;
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
import ilib.misc.MCHooks;
import ilib.misc.MiscOptimize;
import ilib.util.BlockHelper;
import ilib.util.ForgeUtil;
import ilib.util.Hook;
import ilib.util.Registries;
import ilib.util.freeze.FreezeRegistryInjector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.item.Item;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.SplashProgress;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.asm.transformers.AccessTransformer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.JarDiscoverer;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.patcher.ClassPatchManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.dev.HRAgent;
import roj.io.NIOUtil;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.Helpers;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.Buffer;
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
@Mod.EventBusSubscriber
public class ImpLib {
    public static final String MODID = "ilib";
    public static final String NAME = "ImprovementLibrary";
    public static final String VERSION = "0.4.1";

    public static final boolean isClient = FMLCommonHandler.instance().getEffectiveSide().isClient();
    public static final Hook HOOK = new Hook();
    @Deprecated
    public static final List<CommandBase> COMMANDS = new SimpleList<>();

    public static boolean isAuthor = Minecraft.getMinecraft().getSession().getPlayerID().equals("Async");

    private static Logger logger;

    public static Logger logger() {
        if (logger == null) logger = LogManager.getLogger(MODID);
        return logger;
    }

    @SidedProxy(serverSide = "ilib.ServerProxy", clientSide = "ilib.ClientProxy")
    public static ServerProxy proxy;

    static {
        ForgeHooksClient.invalidateLog4jThreadCache();
        Thread.currentThread().setName(isClient ? "客" : "服");
        try {
            HRAgent.useRojLib();
        } catch (Throwable ignored) {}
        SideTransformer.lockdown();
        EventSubscriberTransformer.lockdown();
    }

    @EventHandler
    @SuppressWarnings("unchecked")
    public void preInit(FMLPreInitializationEvent event) {
        // region 处理TileRegister注解
        Set<ASMDataTable.ASMData> table = Loader.ASMTable.getAll("ilib.api.TileRegister");
        if (table != null) {
            for (ASMDataTable.ASMData c : table) {
                String cn = c.getClassName();
                try {
                    Class<?> clazz = Class.forName(cn, false, Launch.classLoader);
                    String str = (String) c.getAnnotationInfo().get("value");
                    if (str.equals("")) {
                        str = clazz.getSimpleName().toLowerCase();
                        if (str.startsWith("tileentity")) str = str.substring(10);
                        else if (str.startsWith("tile")) str = str.substring(4);
                        str = ForgeUtil.getCurrentModId() + ':' + str;
                    }
                    TileEntity.register(str, (Class<? extends TileEntity>) clazz);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("@TileRegister #" + cn + " is failed to load.");
                }
            }
        }
        // endregion

        ModMetadata metadata = event.getModMetadata();
        metadata.autogenerated = false;
        metadata.modId = ImpLib.MODID;
        metadata.version = ImpLib.VERSION;
        metadata.name = ImpLib.NAME;
        metadata.credits = "Asyncorized_MC";
        metadata.authorList.clear();
        metadata.authorList.add("Roj234");
        metadata.description = "Improve your minecraft";
        metadata.url = "https://www.mcmod.cn/";
        metadata.logoFile = "logo.png";

        proxy.preInit();

        HOOK.triggerOnce("preInit");

        if (Config.registerItem) {
            Item item = new ItemSelectTool();
            Registries.item().register(item.setRegistryName(MODID, "select_tool"));

            ILItemModel.Merged(item, 0, "select_tool");
            ILItemModel.Merged(item, 1, "speed_modifier");
            ILItemModel.Merged(item, 2, "place_here");
        }

        if (Config.lootR) {
            MinecraftForge.EVENT_BUS.register(LootrEvent.class);
            Block block = new BlockLootrChest();
            Registry.block("chest_loot", block, new ItemBlockMI(block), null, 24, true);
        }

        try {
            JarDiscoverer.class.getDeclaredMethod("save").invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("不支持的功能: annotationSave");
        }

        MiscOptimize.attributeRangeSet(Config.setAttributeRange);

        Config.instance.save();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        HOOK.triggerOnce("init");
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if(Config.powerShotRequirement != null) {
            MinecraftForge.EVENT_BUS.register(PowershotEvent.class);
            for (Map.Entry<String, CEntry> entry : Config.powerShotRequirement) {
                IBlockState state = BlockHelper.stateFromText(entry.getKey());
                PowershotEvent.powerRequirement.put(state, entry.getValue().asDouble());
            }
        }

        if(Config.freezeUnknownEntries.contains("item") ||
           Config.freezeUnknownEntries.contains("block") ||
           Config.freezeUnknownEntries.contains("entity")) {
            FreezeRegistryInjector.inject();
        }

        if (Config.fastRecipe) {
            MCHooks.cacheRecipes();
        }

        proxy.postInit();
        HOOK.triggerOnce("postInit");
    }

    @EventHandler
    public void onLoadFinish(FMLLoadCompleteEvent event) {
        if (Config.isTrashEnable)
            cleanTrash();

        HOOK.triggerOnce("LoadComplete");

        if (Config.reloadSound && isClient)
            ClientEvent.reloadSoundMgr(false);

        COMMANDS.add(new CommandItemNBT());
        COMMANDS.add(new CommandPlayerNBT());
        COMMANDS.add(new MasterCommand("implib", 3)
                .aliases("il")
                .register(MySubs.CHECK_OD)
                .register(new CmdSchematic())
                .register(MySubs.REGEN)
                .register(
                        (new CmdSubCmd("debug")).setHelp("command.mi.help.debug.1")
                                .register(MySubs.RENDER_INFO)
                                .register(MySubs.BLOCK_UPDATE)
                                .register(MySubs.TILE_TEST))
                .register(new CmdILFill())
                .register(MySubs.GC)
                .register(MySubs.UNLOAD_CHUNKS)
                .register(MySubs.TPS_CHANGE)
        );
    }

    private static MTIssueFixer fixer;

    @EventHandler
    public void onServerStart(FMLServerAboutToStartEvent event) {
        if (isClient) {
            if (Config.fixThreadIssues && fixer == null) {
                fixer = new MTIssueFixer();
                fixer.start();
            }
            event.getServer().setOnlineMode(false);
        }
    }

    @EventHandler
    public void onServerStart(FMLServerStartingEvent event) {
        ForgeHooksClient.invalidateLog4jThreadCache();
        Thread.currentThread().setName("服");
        proxy.setServerThread(Thread.currentThread());
        if (fixer != null) {
            fixer.trigger = 2;
            LockSupport.unpark(fixer);
        }

        HOOK.trigger("ServerStart");
        CommonEvent.onServerStart(event.getServer());

        CommandListenerIL listener = new CommandListenerIL(event.getServer());
        for (int i = 0; i < COMMANDS.size(); i++) {
            listener.serverCmd.registerCommand(COMMANDS.get(i));
        }
    }

    @EventHandler
    public void onServerStart(FMLServerStoppingEvent event) {
        HOOK.trigger("ServerStop");
        if (fixer != null) {
            fixer.trigger = 1;
            LockSupport.unpark(fixer);
        }
    }

    @EventHandler
    public void onServerStop(FMLServerStoppedEvent event) {
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
                    acc.setInstance(t);
                    Multimap<String, Object> map = (Multimap<String, Object>) acc.getObject();

                    if (map != null) {
                        if (!(map instanceof MyImmutableMultimap)) {
                            acc.setObject(new MyImmutableMultimap(map));
                            logger().info("固定的AT Entry: " + map.size());
                        }
                    }
                }
            }

            int count = 0;
            try {
                acc = ReflectionUtils.access(LaunchClassLoader.class.getDeclaredField("resourceCache"));
                acc.setInstance(loader);

                Map<String, byte[]> classCache = (Map<String, byte[]>) acc.getObject();
                count += classCache.size();
                classCache.clear();
            } catch (Throwable ignored) {}

            try {
                acc = ReflectionUtils.access(LaunchClassLoader.class.getDeclaredField("packageManifests"));
                acc.setInstance(loader);

                Map<Package, Manifest> manifestCache = (Map<Package, Manifest>) acc.getObject();

                count += manifestCache.size();
                manifestCache.clear();
            } catch (Throwable ignored) {}

            logger().info("清除的资源/manifest缓存: " + count);
        } catch (Exception e) {
            logger().warn("无法清除缓存1", e);
        }

        try {
            ClassPatchManager cpm = ClassPatchManager.INSTANCE;
            acc = ReflectionUtils.access(ClassPatchManager.class.getDeclaredField("patchedClasses"));
            acc.setInstance(cpm);

            Map<String, byte[]> patchedClasses = Helpers.cast(acc.getObject());
            logger().info("清除的补丁缓存: " + patchedClasses.size());
            patchedClasses.clear();
        } catch (Throwable e) {
            logger().warn("无法清除缓存2", e);
        }

        try {
            Field buf = SplashProgress.class.getDeclaredField("buf");
            buf.setAccessible(true);
            NIOUtil.clean((Buffer) buf.get(null));
            logger().info("清除Splash剩下的缓冲区: 16MB");
        } catch (Throwable e) {
            logger().warn("无法清除缓存3", e);
        }

        try {
            Class<?> mixinClz = Class.forName("org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo$Map");

            acc = ReflectionUtils.access(ReflectionUtils.getField(mixinClz, "NO_GROUP"));

            Object loader = acc.getObject();

            acc = ReflectionUtils.access(ReflectionUtils.getField(loader.getClass(), "members"));
            acc.setInstance(loader);

            List<?> injectCache = (List<?>) acc.getObject();
            logger().info("清除了Mixin group: " + injectCache.size());
            injectCache.clear();

        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            logger().warn("无法清除缓存4", e);
        }
    }
}
