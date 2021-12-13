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
import ilib.asm.fasterforge.transformers.EventSubscriberTransformer;
import ilib.asm.fasterforge.transformers.FieldRedirect;
import ilib.asm.fasterforge.transformers.SideTransformer;
import ilib.command.parser.CommandNeXt;
import io.netty.bootstrap.Bootstrap;
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
import roj.reflect.IFieldAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;

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

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 16:43
 */
@Name("ILASM")
@MCVersion("1.12.2")
@SortingIndex(Integer.MIN_VALUE)
@TransformerExclusions({"ilib.", "roj."})
public class Loader implements IFMLLoadingPlugin {
    public static final Logger logger = LogManager.getLogger("ImpLib-ASM");

    public static ASMDataTable ASMTable;
    @Nullable
    public static final Boolean isClient = testClientSide();

    static long classLoadElapse = 0;

    public Loader() throws IOException {
        AccessTransformer.readAndParseAt(Loader.class, "META-INF/IL_at.cfg");
        if (/*Config.injectLWP*/true) {
            try {
                LaunchInjector.patch();
            } catch (Throwable e) {
                File launcher;
                try {
                    launcher = new File(Launch.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsoluteFile();
                } catch (URISyntaxException e1) {
                    throw new IllegalArgumentException("injectLauncher操作失败", e1);
                }

                try (MutableZipFile mz = new MutableZipFile(launcher)) {
                    // noinspection all
                    ZipInputStream zis = new ZipInputStream(Loader.class.getClassLoader().getResourceAsStream("META-INF/LaunchWrapperInjector.jar"));
                    ZipEntry ze;
                    while ((ze = zis.getNextEntry()) != null) {
                        if (ze.getName().endsWith(".class")) {
                            mz.setFileData(ze.getName(), new ByteList().readStreamFully(zis));
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
                // todo bug fix
                System.out.println(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                patchy = new File(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsoluteFile();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("removePatchy操作失败", e);
            }
            if (patchy.getAbsolutePath().contains("patchy")) {
                try(MutableZipFile mz = new MutableZipFile(patchy)) {
                    for(String key : mz.getEntries().keySet()) {
                        if (key.startsWith("io/netty")) {
                            mz.setFileData(key, null);
                        }
                    }
                    mz.store();
                } catch (IOException e) {
                    throw new IllegalArgumentException("removePatchy操作失败", e);
                }
            }
            Config.instance.getConfig().put("Tweak.Client.修复进存档或服务器卡死(只需打开一次)", false);
            Config.instance.save();
            throw new RuntimeException("请重启Minecraft");
        }

        if(Config.noCollision) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/coll/NiximEntColl.class"));
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/coll/NiximEntLiveColl.class"));
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/coll/NiximWorldColl.class"));
        }

        if(Config.packetBufferInfinity || Config.nbtMaxLength != 2097152) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximPacketBuffer.class"));
        }

        if(Config.noAttackCD) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NoAttackCD.class"));
        }

        if(Config.noEnchantTax) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NoEnchantTax.class"));
        }

        if(Config.noRecipeBook) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximRecipeBook.class"));
        }

        if(Config.IwantLight) {
            // todo
        }

        if (Config.shrinkLog) {
            if(Config.debug == 0)
                System.setOut(new PrintStream(DummyOutputStream.INSTANCE));
            ((org.apache.logging.log4j.core.Logger) FMLLog.log).setLevel(Level.INFO);
            changeLevel(Level.INFO);
        }

        if (Config.eventInvoker) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximEventBus.class"));
        }

        if (Config.replaceOIM) {
            ClassReplacer.add("net.minecraft.util.ObjectIntIdentityMap",
                              IOUtil.read("META-INF/nixim/ObjectIntIdentityMap.class"));
            NiximProxy.read(IOUtil.read("META-INF/nixim/MapClear.class"));
            NiximProxy.read(IOUtil.read("META-INF/nixim/MapGet.class"));
        }

        ClassReplacer.add("net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper", IOUtil.read("ilib/asm/fasterforge/FMLDeobfuscatingRemapper.class"));
        ClassReplacer.add("net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer", IOUtil.read("ilib/asm/fasterforge/DeobfuscationTransformer.class"));
        NiximProxy.read(IOUtil.read("ilib/asm/fasterforge/JarDiscoverer.class"));
        NiximProxy.read(IOUtil.read("ilib/asm/fasterforge/NiximASMModParser.class"));
        NiximProxy.read(IOUtil.read("ilib/asm/fasterforge/NiximModContainerFactory.class"));
        NiximProxy.read(IOUtil.read("ilib/asm/fasterforge/NiximModFind.class"));

        NiximProxy.read(IOUtil.read("ilib/asm/nixim/bug/FastDismount.class"));
        //NiximTransformer.read(IOUtil.read("ilib/asm/nixim/bug/GhostBlock.class"));
        NiximProxy.read(IOUtil.read("ilib/asm/nixim/bug/CrashDispenser.class"));

        NiximProxy.read(IOUtil.read("ilib/asm/nixim/FastTileConst.class"));
        NiximProxy.read(IOUtil.read("ilib/asm/nixim/FastEntityConst.class"));

        if (Config.aabbCache > 0) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximAABB.class"));
        }
        if (Config.entityAabbCache) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximRelAABB.class"));
        }

        if (Config.fastRecipe) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximFastFurnace.class"));
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximFastWorkbench.class"));
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximInvCrafting.class"));
        }

        if (Config.miscPickaxeOptimize) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximPickaxe.class"));
        }

        if (Config.portalCache) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximTeleporter.class"));
        }

        if (isClient != Boolean.FALSE) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/CustomCreativeTab.class"));
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/FakeTabsSupply.class"));

            NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/bug/ElytraRender.class"));
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/bug/GroupNPE.class"));
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/bug/SliderApply.class"));

            NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/bug/LocalOnline.class"));
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/CNModGui.class"));

            if (Config.betterRenderGlobal) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/NiximRenderGlobal.class"));
            }

            if (Config.enablePinyinSearch) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/NiximPinyinSearch.class"));
            }
            if (Config.commandEverywhere) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/NiximNPFix.class"));
            }
            if (!Config.logChat || Config.chatLength != 100) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/NiximChatGui.class"));
            }
            if (Config.changeWorldSpeed > 1) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/NiximEntityClient.class"));
            }
            if (!Config.clientBrand.equals("disable")) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/NiximClientBrand.class"));
            }
            if (Config.noShitSound) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/NiximNoShitSound.class"));
            }
            if (Config.maxParticleCountPerLayer != 16384) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/client/NiximParticleManager.class"));
            }
        }
        if (isClient != Boolean.TRUE) {
            if (Config.noDuplicateLogin) {
                NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximPlayerList.class"));
            }
        }

        if (Config.noSoManyBlockPos) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximCreateBlockPos.class"));
        }

        if (Config.fastLightCheck) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximChunkLight.class"));
        }

        if (Config.fastMethod) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximSlowMethod.class"));
        }

        if (Config.otherWorldChange) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximWorldServer.class"));
        }

        if (Config.cacheBox2) {
            NiximProxy.read(IOUtil.read("ilib/asm/nixim/NiximVillage.class"));
        }
    }

    private static WrappedTransformers wrapper;
    private static final ArrayList<IClassTransformer> ilTransformers = new ArrayList<>(5);
    private static boolean inProgress;

    static void onUpdate(WrappedTransformers list) {
        if (inProgress)
            return;
        inProgress = true;

        for (int i = 0; i < list.size(); i++) {
            IClassTransformer ts = list.get(i);
            switch (ts.getClass().getName()) {
                case "$wrapper.net.minecraftforge.fml.common.asm.transformers.SideTransformer":
                    list.set(i, new SideTransformer());
                    break;
                case "$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer":
                    //list.set(new EventSubscriptionTransformer());
                    break;
                case "$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriberTransformer":
                    list.set(i, new EventSubscriberTransformer());
                    break;
                case "net.minecraftforge.fml.common.asm.transformers.AccessTransformer":
                    //itr.set(new AT_PATCH_AT("A", (net.minecraftforge.fml.common.asm.transformers.AccessTransformer) transformer));
                    break;
                case "net.minecraftforge.fml.common.asm.transformers.ModAccessTransformer":
                    //itr.set(new AT_PATCH_AT("M", (net.minecraftforge.fml.common.asm.transformers.AccessTransformer) transformer));
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

        System.err.println("!!INS " + list);

        inProgress = false;
    }

    public static void addTransformer(IClassTransformer transformer) {
        ilTransformers.add(transformer);
    }

    public static void handleASMData(ASMDataTable store, Map<String, JarInfo> info) {
        ASMTable = store;
        Preloader.preload();
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
    public void injectData(Map<String, Object> data) {
    }

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

                IFieldAccessor accessor = ReflectionUtils.access(LaunchClassLoader.class.getDeclaredField("transformers"));
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
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private static Boolean testClientSide() {
        File file = new File("./assets/");
        if (file.isDirectory())
            return true;
        file = new File("./server.properties");
        return file.isFile() ? false : null;
    }
}