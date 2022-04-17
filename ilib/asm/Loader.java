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

package ilib.asm;

import ilib.Config;
import ilib.asm.fasterforge.anc.JarInfo;
import ilib.asm.fasterforge.transformers.*;
import ilib.asm.util.SafeSystem;
import ilib.command.parser.CommandNeXt;
import io.netty.bootstrap.Bootstrap;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.spongepowered.asm.mixin.MixinEnvironment;
import roj.asm.AccessTransformer;
import roj.io.DummyOutputStream;
import roj.io.IOUtil;
import roj.io.MutableZipFile;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static ilib.asm.NiximProxy.Nx;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
@Name("ILASM")
@MCVersion("1.12.2")
@SortingIndex(Integer.MIN_VALUE)
@TransformerExclusions({"ilib.asm.", "roj."})
public class Loader implements IFMLLoadingPlugin {
    public static final Logger logger = LogManager.getLogger("ImpLib-ASM");

    public static ASMDataTable ASMTable;

    public Loader() throws IOException {
        AccessTransformer.readAndParseAt(Loader.class, "META-INF/IL_at.cfg");

        if (Config.safer) {
            try {
                SafeSystem.register();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        ClassReplacer.add("net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper", IOUtil.read("ilib/asm/fasterforge/FMLDeobfuscatingRemapper.class"));
        ClassReplacer.add("net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer", IOUtil.read("ilib/asm/fasterforge/DeobfuscationTransformer.class"));
        // RojASM注解读取
        Nx("ilib/asm/fasterforge/JarDiscoverer.class");
        Nx("ilib/asm/fasterforge/NiximASMModParser.class");
        Nx("ilib/asm/fasterforge/NiximModContainerFactory.class");
        Nx("ilib/asm/fasterforge/NiximModFind.class");

        // 投掷器炸服
        Nx("!CrashDispenser");
        // 村庄的门加载区块
        Nx("!VillageDoor");
        Nx("!VillagesDoor");
        // 熔炉破坏掉落经验
        Nx("!NxFurnaceExp");
        // 快速方块实体构建
        Nx("!FastTileConst");
        // 快速实体构建
        Nx("!FastEntityConst");
        // 快速矿物词典
        Nx("!NxOres");

        Nx("ilib/client/mirror/NxLockVD.class");

        try {
            LaunchInjector.patch();
        } catch (Throwable e) {
            if (Config.injectLWP) {
                File launcher;
                try {
                    launcher = new File(Launch.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                } catch (URISyntaxException e1) {
                    throw new IllegalArgumentException("injectLauncher操作失败", e1);
                }

                try (MutableZipFile mz = new MutableZipFile(launcher)) {
                    // noinspection all
                    ZipInputStream zis = new ZipInputStream(Loader.class.getClassLoader().getResourceAsStream("META-INF/LaunchWrapperInjector.jar"));
                    ZipEntry ze;
                    while ((ze = zis.getNextEntry()) != null) {
                        if (ze.getName().endsWith(".class")) {
                            mz.put(ze.getName(), new ByteList().readStreamFully(zis));
                        }
                    }
                    mz.store();
                } catch (Throwable e1) {
                    throw new IllegalArgumentException("injectLauncher操作失败", e1);
                }
                throw new RuntimeException("请重启Minecraft", e);
            }
        }

        if (Config.removePatchy) {
            File patchy;
            try {
                String loc = Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                loc = loc.substring("file:/".length(), loc.lastIndexOf('!'));
                patchy = new File(loc);
            } catch (Throwable e) {
                throw new IllegalArgumentException("removePatchy操作失败", e);
            }
            Config.instance.getConfig().put("功能.客户端.修复卡死", false);
            if (patchy.getAbsolutePath().contains("patchy")) {
                try(MutableZipFile mz = new MutableZipFile(patchy)) {
                    for(String key : mz.getEntries().keySet()) {
                        if (key.startsWith("io/netty")) {
                            mz.put(key, null);
                        }
                    }
                    mz.store();
                } catch (IOException e) {
                    throw new IllegalArgumentException("removePatchy操作失败", e);
                }
                Config.instance.save();
                throw new RuntimeException("patchy已删除,请重启Minecraft");
            }
        }

        if(Config.noCollision) {
            Nx("!NxWorldColl");
        }

        if(Config.packetBufferInfinity || Config.nbtMaxLength != 2097152) {
            Nx("!NxPacketSize");
        }

        if (Config.passLeaves) {
            Nx("!NxThroughLeaves");
        }

        if (Config.leaves) {
            Nx("!NxLeaves");
        }

        if (Config.networkOpt1) {
            // 网络相关
            Nx("!NoAutoFlush");
            Nx("!NoAutoFlush2");
            Nx("!Misc1");
            // 数据包范围
            Nx("!IterateAllEntities");
        }
        // 快速压缩
        if (Config.networkOpt2) {
            ClassReplacer.add("net.minecraft.network.NettyVarint21FrameEncoder", IOUtil.read("ilib/asm/nixim/VarIntEncoder.class"));
            ClassReplacer.add("net.minecraft.network.NettyVarint21FrameDecoder", IOUtil.read("ilib/asm/nixim/VarIntDecoder.class"));
            ClassReplacer.add("net.minecraft.network.NettyCompressionEncoder", IOUtil.read("ilib/asm/nixim/FastZip.class"));
            ClassReplacer.add("net.minecraft.network.NettyCompressionDecoder", IOUtil.read("ilib/asm/nixim/FastUnzip.class"));
        }

        if(Config.noEnchantTax) {
            Nx("!NoEnchantTax");
        }

        if (Config.enableTPSChange) {
            Nx("!NxChunkSave");
        }

        if (Config.shrinkLog) {
            System.setOut(new PrintStream(DummyOutputStream.INSTANCE));
            //System.setErr(new PrintStream(DummyOutputStream.INSTANCE));
            ((org.apache.logging.log4j.core.Logger) FMLLog.log).setLevel(Level.INFO);
            changeLevel(Level.INFO);
        }

        if (Config.eventInvoker) {
            Nx("!NxEventBus");
        }

        if (Config.replaceOIM) {
            ClassReplacer.add("net.minecraft.util.ObjectIntIdentityMap",
                              IOUtil.read("META-INF/nixim/ObjectIntIdentityMap.class"));
            Nx("META-INF/nixim/MapClear.class");
            Nx("META-INF/nixim/MapGet.class");
        }

        if (Config.entityAabbCache) {
            Nx("!NxRelAABB");
        }

        if (Config.fastRecipe) {
            Nx("!recipe/NxFastFurnace");
            Nx("!recipe/NxFastWorkbench");
            Nx("!recipe/NxInvCrafting");
            Nx("!recipe/NxOreIng");
        }

        if (Config.miscPickaxeOptimize) {
            Nx("!NxFastPickaxe");
        }

        if (Config.portalCache) {
            Nx("!NiximTeleporter");
        }

        if (Config.slabHelper) {
            Nx("!NiximSlab");
        }

        if (Config.noPistonGhost) {
            Nx("!NoGhostSlime");
        }

        if (Config.myNextTickList) {
            Nx("!NxUpdateTick");
        }

        Boolean isClient = testClientSide();

        if (isClient != Boolean.FALSE) {
            if ((Config.debug & 128) != 0) {
                Nx("!mock/VersionMock");
                Nx("!mock/MockMods");
                Nx("!mock/MockXRay");
                Nx("!mock/MockChannel");
                Nx("!mock/MockBtn");
                Nx("!mock/MockDisc");
            }

            if (Config.traceAABB) {
                Nx("!debug/NxTraceAABB");
            }
            if (Config.traceBP) {
                Nx("!debug/NxTraceBlock");
            }

            if (Config.separateDismount) {
                Nx("!client/SeparateDismount");
            }

            if (Config.lowerChunkBuf != 10) {
                Nx("!client/crd/NxMemoryLeak");
            }

            if (Config.fixCRD) {
                Nx("!client/crd/NxBlockInfo");
                Nx("!client/crd/NxMem2");
                Nx("!client/crd/NxMem3");
                Nx("!client/crd/NxLight");
                Nx("!client/crd/NxChunkCache");
                Nx("!client/crd/NxBiomeColor");
                Nx("!client/crd/NxBiomeDefault");
                Nx("!client/crd/NxBufCom");
                Nx("!client/crd/NxBuf");
            }

            if (Config.smallBuf) {
                Nx("!client/NxBuf2");
            }

            if (Config.clearLava) {
                Nx("!client/NxClearLava");
            }

            if (Config.fastFont) {
                Nx("!client/NxFastFont");
            }

            Nx("!client/CustomCreativeTab");
            Nx("!client/FakeTabsSupply");

            Nx("!ElytraRender");
            Nx("!GroupNPE");
            Nx("!SliderApply");

            //Nx("!client/NxNewFont");

            if (Config.betterRenderGlobal) {
                Nx("!client/NiximRenderGlobal");
            }

            if (Config.showySelectBox) {
                Nx("!client/NxSelectionBox");
            }
            if (Config.enablePinyinSearch) {
                Nx("!client/NiximPinyinSearch");
            }
            if (Config.commandEverywhere) {
                Nx("!client/NxPortal");
            }
            if (!Config.logChat || Config.chatLength != 100) {
                Nx("!client/NiximChatGui");
            }
            if (Config.changeWorldSpeed > 1) {
                Nx("!client/NxFastSpawn");
            }
            if (!Config.clientBrand.equals("vanilla")) {
                Nx("!client/NxClientBrand");
            }
            if (Config.maxParticleCountPerLayer != 16384) {
                Nx("!client/NxParticleCount");
            }
        }

        if (isClient != Boolean.TRUE) {
            if (Config.noDuplicateLogin) {
                Nx("!NoDuplicateLogin");
            }
        }

        if (Config.worldGenOpt) {
            Nx("!NxFalling");
            Nx("!NxWorldGen1");
        }

        if (Config.noSoManyBlockPos2) {
            ClassReplacer.add("net/minecraft/util/math/BlockPos$PooledMutableBlockPos",
                              IOUtil.read("ilib/asm/LMReplace.class"));
            Nx("!NxBP1");
            Nx("!NxBP2");
            Nx("!NxBP3");
            Nx("!NxBP4");
            Nx("!NxBP5");
            Nx("!NxGrass");
        }

        if (Config.noSoManyBlockPos) {
            Nx("!NxCachedPos");
            Nx("!NxCachedPos2");
            Nx("!NxCachedPos3");
        }

        if (Config.fastLightCheck) {
            Nx("!NiximChunkLight");
        }

        if (Config.fastMethod) {
            Nx("!NxGetCollision");
        }

        if (Config.otherWorldChange) {
            Nx("!NxTickChunk");
        }
    }

    private static WrappedTransformers wrapper;
    private static final ArrayList<IClassTransformer> ilTransformers = new ArrayList<>(5);
    private static boolean inProgress;

    static void onUpdate(WrappedTransformers list) {
        if (inProgress)
            return;
        inProgress = true;

        if (Config.replaceTransformer)
        for (int i = 0; i < list.size(); i++) {
            IClassTransformer ts = list.get(i);
            switch (ts.getClass().getName()) {
                case "$wrapper.net.minecraftforge.fml.common.asm.transformers.SideTransformer":
                    list.set(i, new SideTransformer());
                    break;
                case "net.minecraftforge.fml.common.asm.transformers.TerminalTransformer":
                    list.set(i, new TerminalTransformer());
                    break;
                case "$wrapper.net.minecraftforge.fml.common.asm.transformers.ModAPITransformer":
                    list.set(i, new ModAPITransformer());
                    break;
                case "$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer":
                    if (Config.noMixin) list.set(i, new EventSubTrans());
                    break;
                case "$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriberTransformer":
                    list.set(i, new EventSubscriberTransformer());
                    break;
                case "net.minecraftforge.fml.common.asm.transformers.ItemStackTransformer":
                    list.set(i, new FieldRedirect("ItemStack", "net.minecraft.item.ItemStack", "Lnet/minecraft/item/Item;", "getItemRaw"));
                    break;
                case "net.minecraftforge.fml.common.asm.transformers.ItemBlockTransformer":
                    list.set(i, new FieldRedirect("ItemBlock", "net.minecraft.item.ItemBlock", "Lnet/minecraft/block/Block;", "getBlockRaw"));
                    break;
                case "net.minecraftforge.fml.common.asm.transformers.ItemBlockSpecialTransformer":
                    list.set(i, new FieldRedirect("ItemBlockSpecial", "net.minecraft.item.ItemBlockSpecial", "Lnet/minecraft/block/Block;", "getBlockRaw"));
                    break;
                case "net.minecraftforge.fml.common.asm.transformers.PotionEffectTransformer":
                    list.set(i, new FieldRedirect("PotionEffect", "net.minecraft.potion.PotionEffect", "Lnet/minecraft/potion/Potion;", "getPotionRaw"));
                    break;
            }
        }

        HashSet<IClassTransformer> set = new LinkedHashSet<>(list);
        set.remove(ClassReplacer.INSTANCE);
        set.removeAll(ilTransformers);

        list.clear();
        list.addAll(set);
        list.add(Math.min(2, list.size()), ClassReplacer.INSTANCE);
        list.addAll(list.size() - 1, ilTransformers);

        inProgress = false;
    }

    public static void addTransformer(IClassTransformer transformer) {
        ilTransformers.add(transformer);
    }

    public static void handleASMData(ASMDataTable store, Map<String, JarInfo> info) {
        ASMTable = store;
        CommandNeXt.initStore();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        //用来获取实现了ModContainer的类
        return null;
    }

    @Override
    public String getSetupClass() {
        //用来获取实现了IFMLCallHook的类
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return ATProxy.class.getName();
    }

    private static void changeLevel(Level level) {
        final Level currLvl = LogManager.getRootLogger().getLevel();
        if (currLvl.equals(level)) return;
        // 设置根日志级别
        ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).setLevel(level);

        // 获取配置文件中的所有log4j对象
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration conf = ctx.getConfiguration();
        conf.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(level);
        ctx.updateLoggers(conf);

        ctx = (LoggerContext) LogManager.getContext(true);
        conf = ctx.getConfiguration();
        conf.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(level);
        ctx.updateLoggers(conf);
    }

    @SuppressWarnings("unchecked")
    public static void wrapTransformers() {
        if (wrapper == null) {
            try {
                ilTransformers.add(new Transformer());
                ilTransformers.add(NiximProxy.instance);

                FieldAccessor accessor = ReflectionUtils.access(LaunchClassLoader.class.getDeclaredField("transformers"));
                accessor.setInstance(Launch.classLoader);
                onUpdate(wrapper = new WrappedTransformers((List<IClassTransformer>) accessor.getObject()));
                accessor.setObject(wrapper);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }

            try {
                MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
                env.addTransformerExclusion(NiximProxy.class.getName());
                env.addTransformerExclusion(ClassReplacer.class.getName());
                env.addTransformerExclusion(EventSubTrans.class.getName());
                env.addTransformerExclusion(TerminalTransformer.class.getName());
            } catch (Throwable ignored) {}
        }
    }

    public static Boolean testClientSide() {
        File file = new File("./assets/");
        if (file.isDirectory()) {
            return true;
        }
        file = new File("./server.properties");
        if (file.isFile()) {
            return false;
        }
        return null;
    }
}