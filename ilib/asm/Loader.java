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
import ilib.asm.fasterforge.transformers.FieldRedirect;
import ilib.asm.transformers.*;
import ilib.command.parser.CommandNeXt;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.spongepowered.asm.mixin.MixinEnvironment;
import roj.asm.nixim.NiximTransformer;
import roj.asm.transform.AccessTransformer;
import roj.io.DummyOutputStream;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.MutableZipFile;
import roj.reflect.IFieldAccessor;
import roj.reflect.ReflectionUtils;

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
import java.util.*;

@Name("ILASM")
@MCVersion("1.12.2")
@SortingIndex(Integer.MIN_VALUE)
@TransformerExclusions({"ilib.asm.", "roj."})
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 16:43
 */
public class Loader implements IFMLLoadingPlugin {
    private static final Logger logger = LogManager.getLogger("ImpLib-ASM");

    public static ASMDataTable asmInfo;
    @Nullable
    public static final Boolean isClient = determineClient();

    private static TransformerWrapperList transformers;
    private static final ArrayList<IClassTransformer>
            firstTransformers = new ArrayList<>(5),
            endTransformers = new ArrayList<>(5);

    public static void changeLevel(Level level) {
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

    public Loader() {
        AccessTransformer.readAndParseAt(Loader.class, "META-INF/IL_at.cfg");

        // todo Enum#values() fix
        // EntityEquipmentSlot.values() // EnumFacing.values() // etc

        if (Config.removePatchy) {
            for (File patchy : FileUtil.findAllFiles(new File("."), file -> file.getName().startsWith("patchy-") && file.getName().endsWith(".jar"))) {
                File mark = new File(patchy, "-mark");
                if (mark.isFile()) continue;

                try(MutableZipFile mz = new MutableZipFile(patchy)) {
                    for(String key : mz.getEntries().keySet()) {
                        if (key.startsWith("io/netty")) {
                            mz.setFileData(key, null, true);
                        }
                    }
                    mz.store();
                } catch (IOException e) {
                    throw new IllegalArgumentException("操作失败", e);
                }

                try {
                    if (!mark.createNewFile()) {
                        throw new IllegalArgumentException("Mark creation失败");
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("操作失败", e);
                }
            }
            Config.instance.getConfig().put("Tweak.Client.修复进存档或服务器卡死(只需打开一次)", false);
            Config.instance.save();
        }

        if(Config.noCollision) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/coll/NiximEntColl.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/coll/NiximEntLiveColl.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/coll/NiximWorldColl.class"));
        }

        if(Config.packetBufferInfinity || Config.nbtMaxLength != 2097152) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximPacketBuffer.class"));
        }

        if(Config.noAttackCD) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NoAttackCD.class"));
        }

        if(Config.noEnchantTax) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NoEnchantTax.class"));
        }

        if(Config.noRecipeBook) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximRecipeBook.class"));
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
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximEventBus.class"));
        }

        if (Config.replaceOIM) {
            ClassReplacer.addClass("net.minecraft.util.ObjectIntIdentityMap",
                                   IOUtil.getBytesS(Loader.class, "META-INF/nixim/ObjectIntIdentityMap.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "META-INF/nixim/MapClear.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "META-INF/nixim/MapGet.class"));
        }

        ClassReplacer.addClass("net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper", IOUtil.getBytesS(Loader.class, "ilib/asm/fasterforge/FMLDeobfuscatingRemapper.class"));
        ClassReplacer.addClass("net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer", IOUtil.getBytesS(Loader.class, "ilib/asm/fasterforge/DeobfuscationTransformer.class"));
        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/fasterforge/JarDiscoverer.class"));
        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/fasterforge/NiximASMModParser.class"));
        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/fasterforge/NiximModContainerFactory.class"));
        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/fasterforge/NiximModFind.class"));

        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/bug/FastDismount.class"));
        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/bug/GhostBlock.class"));
        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/bug/CrashDispenser.class"));

        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/FastTileConst.class"));
        NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/FastEntityConst.class"));

        if (Config.aabbCache > 0) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximAABB.class"));
        }
        if (Config.entityAabbCache) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximRelAABB.class"));
        }

        if (Config.fastRecipe) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximFastFurnace.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximFastWorkbench.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximInvCrafting.class"));
        }

        if (Config.miscPickaxeOptimize) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximPickaxe.class"));
        }

        if (Config.portalCache) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximTeleporter.class"));
        }

        if (isClient != Boolean.FALSE) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/CustomCreativeTab.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/FakeTabsSupply.class"));

            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/bug/ElytraRender.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/bug/GroupNPE.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/bug/SliderApply.class"));

            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/bug/LocalOnline.class"));
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/CNModGui.class"));

            if (Config.betterRenderGlobal) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/NiximRenderGlobal.class"));
            }

            if (Config.enablePinyinSearch) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/NiximPinyinSearch.class"));
            }
            if (Config.commandEverywhere) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/NiximNPFix.class"));
            }
            if (!Config.logChat || Config.chatLength != 100) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/NiximChatGui.class"));
            }
            if (Config.changeWorldSpeed > 1) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/NiximEntityClient.class"));
            }
            if (!Config.clientBrand.equals("disable")) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/NiximClientBrand.class"));
            }
            if (Config.noShitSound) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/NiximNoShitSound.class"));
            }
            if (Config.maxParticleCountPerLayer != 16384) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/client/NiximParticleManager.class"));
            }
        }
        if (isClient != Boolean.TRUE) {
            if (Config.noDuplicateLogin) {
                NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximPlayerList.class"));
            }
        }

        if (Config.noSoManyBlockPos) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximCreateBlockPos.class"));
        }

        if (Config.fastLightCheck) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximChunkLight.class"));
        }

        if (Config.fastMethod) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximSlowMethod.class"));
        }

        if (Config.otherWorldChange) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximWorldServer.class"));
        }

        if (Config.cacheBox2) {
            NiximTransformer.read(IOUtil.getBytesS(Loader.class, "ilib/asm/nixim/NiximVillage.class"));
        }
    }

    private static Boolean determineClient() {
        File file = new File("./assets/");
        if (file.isDirectory())
            return true;
        file = new File("./server.properties");
        return file.isFile() ? false : null;
    }

    public static Logger logger() {
        return logger;
    }

    @SuppressWarnings("unchecked")
    public static void tryPatch(IClassTransformer transformer) {
        if (transformers == null) {
            try {
                IFieldAccessor accessor = ReflectionUtils.accessField(LaunchClassLoader.class.getDeclaredField("transformers"));

                accessor.setInstance(Launch.classLoader);

                List<IClassTransformer> list = (List<IClassTransformer>) accessor.getObject();

                transformers = new TransformerWrapperList(list);
                onTransformerUpdate(transformers);

                accessor.setObject(transformers);

            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
    }

    private static int transformForgeTransformer = 0;
    private static boolean fullInit = false;
    private static boolean mixinFixed;
    private static boolean flag;

    public static void onTransformerUpdate(TransformerWrapperList list) {
        if (flag)
            return;
        flag = true;

        if (!mixinFixed) {
            tryFixMixinBug();
            mixinFixed = true;
        }

        if (!fullInit) {
            list.removeIf(t -> t.getClass().getName().startsWith("$wrapper.ilib.asm"));
            //list.removeIf(t -> t.getClass().getName().equals("ilib.asm.util.FxMixin"));

            /*if (Config.patchForge && foundFMLCorePlugins < 3*//*4*//*) {
                int count = 0;
                ListIterator<IClassTransformer> itr = list.listIterator();

                while (itr.hasNext()) {
                    IClassTransformer transformer = itr.next();
                    switch (transformer.getClass().getName()) {
                        case "$wrapper.net.minecraftforge.fml.common.asm.transformers.SideTransformer":
                            itr.set(new SideTransformer());
                            logger().warn("[WIP] Patched SideTransformer");
                            foundFMLCorePlugins++;
                            break;
                        case "$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer":
                            itr.set(new EventSubscriptionTransformer());
                            //logger().warn("[WIP] Patched EventSubscriptionTransformer");
                            foundFMLCorePlugins++;
                            break;
                        case "$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriberTransformer":
                            itr.set(new EventSubscriberTransformer());
                            //logger().warn("[WIP] Patched EventSubscriberTransformer");
                            foundFMLCorePlugins++;
                            break;
                        case "$wrapper.net.minecraftforge.fml.common.asm.transformers.SoundEngineFixTransformer":
                            //foundFMLCorePlugins++;
                            break;
                    }
                }
            }*/

            if (transformForgeTransformer < 4) {
                int count = 0;
                ListIterator<IClassTransformer> itr = list.listIterator();

                while (itr.hasNext()) {
                    IClassTransformer transformer = itr.next();
                    switch (transformer.getClass().getName()) {
                        case "net.minecraftforge.fml.common.asm.transformers.AccessTransformer":
                            //itr.set(new AT_PATCH_AT("AccessTrans", (net.minecraftforge.fml.common.asm.transformers.AccessTransformer) transformer));
                            //transformForgeTransformer ++;
                            break;
                        case "net.minecraftforge.fml.common.asm.transformers.ModAccessTransformer":
                            //itr.set(new AT_PATCH_AT("ModAccess", (net.minecraftforge.fml.common.asm.transformers.AccessTransformer) transformer));
                            //for (int i = 0; i < 10; i++) {
                            //    System.out.println("FUCK FORGE!!! for the package-private access of net/minecraftforge/fml/common/asm/transformers/AccessTransformer$Modifier");
                            //}
                            //transformForgeTransformer ++;
                            break;
                        case "net.minecraftforge.fml.common.asm.transformers.ItemStackTransformer":
                            itr.set(new FieldRedirect("ItemStack", "net.minecraft.item.ItemStack", "Lnet/minecraft/item/Item;", "getItemRaw"));
                            transformForgeTransformer++;
                            break;
                        case "net.minecraftforge.fml.common.asm.transformers.ItemBlockTransformer":
                            itr.set(new FieldRedirect("ItemBlock", "net.minecraft.item.ItemBlock", "Lnet/minecraft/block/Block;", "getBlockRaw"));
                            transformForgeTransformer++;
                            break;
                        case "net.minecraftforge.fml.common.asm.transformers.ItemBlockSpecialTransformer":
                            itr.set(new FieldRedirect("ItemBlockSpecial", "net.minecraft.item.ItemBlockSpecial", "Lnet/minecraft/block/Block;", "getBlockRaw"));
                            transformForgeTransformer++;
                            break;
                        case "net.minecraftforge.fml.common.asm.transformers.PotionEffectTransformer":
                            itr.set(new FieldRedirect("PotionEffect", "net.minecraft.potion.PotionEffect", "Lnet/minecraft/potion/Potion;", "getPotionRaw"));
                            transformForgeTransformer++;
                            break;
                        default:
                            /*if(transformer.getClass().getName().startsWith("org.spongepowered.asm")) {
                                itr.set(new ProxiedProxy(transformer));
                            }*/
                    }
                }
            } else {
                firstTransformers.add(ClassReplacer.INSTANCE);
                endTransformers.add(new AutoRegisterTransformer());
                endTransformers.add(new Transformer());

                NiximProxy.alreadyAtDeobfEnv = true;
                fullInit = true;
            }
        }

        /*if (!foundModApi) {
            int i = 0;
            for (ListIterator<IClassTransformer> iterator = list.listIterator(); iterator.hasNext(); ) {
                IClassTransformer modApi = iterator.next();
                if (modApi.getClass() == net.minecraftforge.fml.common.asm.transformers.ModAPITransformer.class) {
                    foundModApi = true;
                    iterator.set(new ModAPITransformer());
                    break;
                }
                i++;
            }
        }*/

        HashSet<IClassTransformer> transformerSet = new LinkedHashSet<>(list);

        transformerSet.removeAll(firstTransformers);
        transformerSet.removeAll(endTransformers);
        transformerSet.remove(NiximProxy.instance);

        list.clear();
        list.addAll(transformerSet);
        list.addAll(Math.min(2, list.size()), firstTransformers);

        //int endIndex = -1;
        //int i = 0;
        //for (IClassTransformer transformer : transformerSet) {
        //    i++;
        //    if (transformer.getClass().getName().endsWith("DeobfuscationTransformer")) {
        //        endIndex = i + 1;
        //        break;
        //    }
        //}
        //if (endIndex != -1 && endIndex < list.size()) {
        //    list.addAll(endIndex, endTransformers);
        //} else
            list.addAll(endTransformers);

        list.add(list.size() - 1, NiximProxy.instance);

        flag = false;
    }

    private static void tryFixMixinBug() {
        try {
            MixinEnvironment.getCurrentEnvironment().addTransformerExclusion(NiximProxy.class.getName());
            MixinEnvironment.getCurrentEnvironment().addTransformerExclusion(ClassReplacer.class.getName());
        } catch (Throwable ignored) {
        }
    }

    public static void last(IClassTransformer transformer) {
        endTransformers.add(transformer);
    }

    public static void first(IClassTransformer transformer) {
        firstTransformers.add(transformer);
    }

    public static void handleASMData(ASMDataTable store, Map<String, JarInfo> info) {
        asmInfo = store;
        AutoRegisterTransformer.initStore();
        CommandNeXt.initStore();
    }

    @Override
    public String[] getASMTransformerClass() {
        Preloader.doPreload();
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

}