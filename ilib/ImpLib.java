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
import ilib.asm.util.MCHooks;
import ilib.block.BlockLootrChest;
import ilib.client.GeneratedModelRepo;
import ilib.client.model.ItemModelInfo;
import ilib.command.*;
import ilib.command.sub.CmdSubCmd;
import ilib.command.sub.CommandStructure;
import ilib.command.sub.MySubs;
import ilib.command.sub.we.CommandFill;
import ilib.command.sub.we.CommandSet;
import ilib.command.sub.we.ModificationCache;
import ilib.event.*;
import ilib.item.ItemBlockMI;
import ilib.item.ItemPlaceHere;
import ilib.item.ItemSelectTool;
import ilib.item.ItemSpeedModifier;
import ilib.misc.MiscOptimize;
import ilib.util.BlockHelper;
import ilib.util.ForgeUtil;
import ilib.util.Hook;
import ilib.util.PlayerUtil;
import ilib.util.freeze.FreezeRegistryInjector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;
import roj.text.TextUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.Item;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.asm.transformers.AccessTransformer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.JarDiscoverer;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static Logger logger;

    public static Logger logger() {
        if (logger == null) logger = LogManager.getLogger(MODID);
        return logger;
    }

    @SidedProxy(serverSide = "ilib.ServerProxy", clientSide = "ilib.ClientProxy")
    public static ServerProxy proxy;

    public ImpLib() {
        Thread.currentThread().setName(isClient ? "客" : "服");
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

        Registry.namespace(MODID);

        if (Config.registerItem) {
            Item item = new ItemSelectTool();
            Registry.items().add(item.setRegistryName(MODID, "select_tool").setTranslationKey("ilib.select_tool"));
            Registry.model(new ItemModelInfo(item, true));

            item = new ItemSpeedModifier();
            Registry.items().add(item.setRegistryName(MODID, "speed_modifier").setTranslationKey("ilib.speed_modifier"));
            Registry.model(new ItemModelInfo(item, true));

            item = new ItemPlaceHere();
            Registry.items().add(item.setRegistryName(MODID, "place_here").setTranslationKey("ilib.place_here"));
            Registry.model(new ItemModelInfo(item, true));
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

    @SideOnly(Side.CLIENT)
    public static void onPreInitDone() {
        GeneratedModelRepo.preInitDone();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        HOOK.triggerOnce("init");
        if (isClient && Config.betterKeyboard) {
            Keyboard.enableRepeatEvents(true);
        }

        if(Config.sizeEvent > 0)
            SizeEvent.register(Config.sizeEvent > 1);
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
            MCHooks.initRecipes();
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
                .register(new CommandServerManage())
                .register(new CommandStructure())
                .register(MySubs.REGEN)
                .register(
                        (new CmdSubCmd("debug")).setHelp("command.mi.help.debug.1")
                                .registerSubCommand(MySubs.RENDER_INFO)
                                .registerSubCommand(MySubs.BLOCK_UPDATE)
                                .registerSubCommand(MySubs.TILE_TEST))
                .register(
                        new CmdSubCmd("we")
                                .registerSubCommand(new CommandFill())
                                .registerSubCommand(new CommandSet())
                                .registerSubCommand(new MySubs("redo") {
                                    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                                        try {
                                            if (ModificationCache.redo()) {
                                                PlayerUtil.sendTo(sender, "command.ilib.we.affected", ModificationCache.getAffectBlocks());
                                            } else {
                                                throw new CommandException("command.ilib.we.cant_redo");
                                            }
                                        } catch (IllegalStateException e) {
                                            throw new CommandException("command.ilib.we.async_working");
                                        }
                                    }
                                })
                                .registerSubCommand(new MySubs("undo") {
                                    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                                        try {
                                            if (ModificationCache.undo()) {
                                                PlayerUtil.sendTo(sender, "command.ilib.we.affected", ModificationCache.getAffectBlocks());
                                            } else {
                                                throw new CommandException("command.ilib.we.cant_undo");
                                            }
                                        } catch (IllegalStateException e) {
                                            throw new CommandException("command.ilib.we.async_working");
                                        }
                                    }
                                })
                                .registerSubCommand(new MySubs("mem") {
                                    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                                        switch (args.length) {
                                            case 0: {
                                                long bytes = ModificationCache.getMemoryBytes();
                                                PlayerUtil.sendTo(sender, "当前重做+撤销缓存已使用内存: " + TextUtil.scaledNumber(bytes) + "B");
                                                PlayerUtil.sendTo(sender, "当前重做缓存已使用数量: " + ModificationCache.redo.size() + '/' + ModificationCache.maxRedoCount);
                                                PlayerUtil.sendTo(sender, "当前撤销缓存已使用数量: " + ModificationCache.undo.size() + '/' + ModificationCache.maxUndoCount);
                                                return;
                                            }
                                            case 1: {
                                                if (args[0].equals("clear")) {
                                                    ModificationCache.undo.clear();
                                                    ModificationCache.redo.clear();
                                                    PlayerUtil.sendTo(sender, "command.ilib.ok");
                                                    return;
                                                }
                                            }
                                            break;
                                            case 2: {
                                                switch (args[0]) {
                                                    case "set": {
                                                        switch (args[1]) {
                                                            case "on": {
                                                                ModificationCache.setEnable(true);
                                                                PlayerUtil.sendTo(sender, "command.ilib.ok");
                                                                return;
                                                            }
                                                            case "off": {
                                                                ModificationCache.setEnable(false);
                                                                PlayerUtil.sendTo(sender, "command.ilib.ok");
                                                                return;
                                                            }
                                                        }
                                                    }
                                                    break;
                                                    case "async": {
                                                        switch (args[1]) {
                                                            case "on": {
                                                                ModificationCache.setAsync(true);
                                                                PlayerUtil.sendTo(sender, "command.ilib.ok");
                                                                return;
                                                            }
                                                            case "off": {
                                                                ModificationCache.setAsync(false);
                                                                PlayerUtil.sendTo(sender, "command.ilib.ok");
                                                                return;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            break;
                                            case 3: {
                                                if (args[0].equals("mem")) {
                                                    try {
                                                        int maxUndo = Integer.parseInt(args[1]);
                                                        int maxRedo = Integer.parseInt(args[2]);

                                                        ModificationCache.maxUndoCount = maxUndo;
                                                        ModificationCache.maxRedoCount = maxRedo;

                                                        PlayerUtil.sendTo(sender, "command.ilib.ok");
                                                    } catch (NumberFormatException ignored) {
                                                        PlayerUtil.sendTo(sender, "command.ilib.int");
                                                    }
                                                    return;
                                                }
                                            }
                                            break;
                                        }
                                        PlayerUtil.sendTo(sender, "使用/il we mem <maxUndo> <maxRedo> 设置最大重做和撤销数量");
                                        PlayerUtil.sendTo(sender, "使用/il we mem set <on/off> 启用/关闭重做和撤销功能");
                                        PlayerUtil.sendTo(sender, "使用/il we mem async <on/off> 启用/关闭异步操作(不是所有功能都支持)");
                                        PlayerUtil.sendTo(sender, "使用/il we mem clear 清空撤销数据");
                                    }
                                }))
                .register(MySubs.GC)
                .register(MySubs.UNLOAD_CHUNKS)
                .register(MySubs.TPS_CHANGE)
        );
    }

    public static MTIssueFixer issueFixer;

    @EventHandler
    public void onServerStart(FMLServerAboutToStartEvent event) {
        if (isClient) {
            if (Config.fixThreadIssues && issueFixer == null) {
                Thread thread = new Thread(issueFixer = new MTIssueFixer(), "Issue Fixer");
                thread.setDaemon(true);
                thread.start();
            }
            event.getServer().setOnlineMode(false);
        }
    }

    @EventHandler
    public void onServerStart(FMLServerStartingEvent event) {
        Thread.currentThread().setName("服");

        HOOK.trigger("ServerStart");
        CommonEvent.onServerStart(event.getServer());
        COMMANDS.forEach(event::registerServerCommand);
        new CommandListenerIL();

        final Thread thread = Thread.currentThread();
        proxy.setServerThread(thread);
        if (issueFixer != null) {
            issueFixer.trigger = 2;
            synchronized (issueFixer) {
                issueFixer.notifyAll();
            }
        }
    }

    @EventHandler
    public void onServerStart(FMLServerStoppingEvent event) {
        HOOK.trigger("ServerStop");
        proxy.setServerThread(null);
    }

    @EventHandler
    public void onServerStart(FMLServerStoppedEvent event) {
        if (issueFixer != null) {
            issueFixer.trigger = 1;
            synchronized (issueFixer) {
                issueFixer.notifyAll();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void cleanTrash() {
        FieldAccessor acc;
        try {
            LaunchClassLoader loader = Launch.classLoader;

            acc = ReflectionUtils.access(ReflectionUtils.getField(AccessTransformer.class, "modifiers"));

            for (IClassTransformer transformer : loader.getTransformers()) {
                if (transformer instanceof AccessTransformer) {
                    acc.setInstance(transformer);
                    Multimap<String, Object> map = (Multimap<String, Object>) acc.getObject();

                    if (map != null) {
                        logger().info("" + map.size() + " entries fixed");

                        if (!(map instanceof ilib.collect.MyImmutableMultimap)) {
                            acc.setObject(new ilib.collect.MyImmutableMultimap(map));
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

            logger().info(count + " entries cleared");

        } catch (Exception e) {
            logger().catching(e);
        }

        try {
            Class<?> mixinClz = Class.forName("org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo$Map");

            acc = ReflectionUtils.access(ReflectionUtils.getField(mixinClz, "NO_GROUP"));

            Object loader = acc.getObject();

            acc = ReflectionUtils.access(ReflectionUtils.getField(loader.getClass(), "members"));
            acc.setInstance(loader);

            List<?> injectCache = (List<?>) acc.getObject();

            int count = injectCache.size();
            logger().info(count + " groups cleared");

            injectCache.clear();

        } catch (ClassNotFoundException e) {
            logger().debug("Mixin not found");
        } catch (Exception e) {
            logger().catching(e);
        }
    }

    private static class MTIssueFixer implements Runnable {
        public Thread self;
        public int trigger = 0;

        @Override
        public void run() {
            Thread self = this.self = Thread.currentThread();
            Thread server = null;
            while (!self.isInterrupted()) {
                if (ImpLib.proxy.serverThread != null) {
                    server = ImpLib.proxy.serverThread;
                }

                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException e) {
                    return;
                }

                if (trigger != 0) {
                    switch (trigger) {
                        case 1: { // STOP
                            if (server != null) {
                                //server.setDaemon(true);

                                try {
                                    server.join(10000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                if (server.isAlive()) {
                                    server.interrupt();
                                    try {
                                        server.stop();
                                    } catch (Throwable e) {
                                        ImpLib.logger().error("无法终止进程", e);
                                        try {
                                            Thread.sleep(50);
                                        } catch (InterruptedException e1) {
                                            e1.printStackTrace();
                                        }
                                        //while (true) {
                                        //    System.exit(17);
                                        //}
                                    }
                                }
                                server = null;

                                trigger = 0;
                            }
                        }
                        break;
                        case 2: { // MAXIMUM
                            if (server != null) {

                                int sop = server.getPriority();

                                server.setPriority(Thread.MAX_PRIORITY);
                                final Thread client = ((ClientProxy) proxy).clientThread;

                                int cop = client.getPriority();

                                client.setPriority(Thread.MIN_PRIORITY);

                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                server.setPriority(sop);
                                client.setPriority(cop);

                                trigger = 0;
                            }
                        }
                        break;
                    }
                }
            }
        }
    }
}
