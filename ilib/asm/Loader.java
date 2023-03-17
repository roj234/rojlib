package ilib.asm;

import ilib.Config;
import ilib.asm.FeOrg.ctr.*;
import ilib.asm.util.SafeSystem;
import io.netty.bootstrap.Bootstrap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.spongepowered.asm.mixin.MixinEnvironment;
import roj.archive.zip.ZipArchive;
import roj.asm.ATList;
import roj.io.DummyOutputStream;
import roj.io.IOUtil;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.Helpers;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static ilib.asm.NiximProxy.Nx;
import static ilib.asm.NiximProxy.niximUser;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
@Name("ILASM")
@MCVersion("1.12.2")
@SortingIndex(Integer.MIN_VALUE)
@TransformerExclusions({"ilib.asm.", "roj."})
public class Loader implements IFMLLoadingPlugin {
	public static final Logger logger = LogManager.getLogger("又一个优化mod");
	public static final long beginTime = System.currentTimeMillis();

	public static boolean hasOF;
	public static ASMDataTable Annotations;

	public Loader() {
		ATList.parse(Loader.class, "META-INF/IL_at.cfg");

		if (Config.safer) {
			try {
				SafeSystem.register();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		// RojASM注解读取
		Nx("ilib/asm/FeOrg/JarDiscoverer.class");
		Nx("ilib/asm/FeOrg/NiximASMModParser.class");
		Nx("ilib/asm/FeOrg/NiximModContainerFactory.class");

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

				try (ZipArchive mz = new ZipArchive(launcher)) {
					// noinspection all
					ZipInputStream zis = new ZipInputStream(Loader.class.getClassLoader().getResourceAsStream("META-INF/LaunchWrapperInjector.jar"));
					ZipEntry ze;
					while ((ze = zis.getNextEntry()) != null) {
						if (ze.getName().endsWith(".class")) {
							mz.put(ze.getName(), ByteList.wrap(IOUtil.getSharedByteBuf().readStreamFully(zis, false).toByteArray()));
						}
					}
					zis.close();
					mz.store();
				} catch (Throwable e1) {
					throw new IllegalArgumentException("injectLauncher操作失败", e1);
				}

				Config.instance.getConfig().put("调试.注入LaunchWrapper", false);
				Config.instance.save();

				throw new RuntimeException("请重启Minecraft");
			}
		}

		if (Config.removePatchy) {
			File patchy;
			try {
				patchy = Helpers.getJarByClass(Bootstrap.class);
			} catch (Exception e) {
				throw new IllegalArgumentException("removePatchy操作失败", e);
			}
			Config.instance.getConfig().put("功能.客户端.修复卡死", false);
			if (patchy.getAbsolutePath().contains("patchy")) {
				try (ZipArchive mz = new ZipArchive(patchy)) {
					for (String key : mz.getEntries().keySet()) {
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
	}

	private static void registerNixims() throws IOException {
		// region 测试中的优化

		if (Config.disableAllNixim) return;

		//Nx("!NxRecord");
		//Nx("!FastETE");
		//Nx("!TrackerAdd");
		// todo 用上LargestRectangle+shader
		// todo 3-side rendering, 先写一个方法int getSideBits(Vec3d obsPos, Vec3f obsVec, Vec3d tgPos, Vec3f tgVec)
		// todo ScreenShotHelper (GL_GET INT_ARGB -> Direct -> BufferImage int[])

		// 快速压缩
		if (Config.fastPacketProc) {
			ClassReplacer.FIRST.add("net.minecraft.network.NettyVarint21FrameEncoder", IOUtil.read("ilib/asm/rpl/VarIntEncoder.class"));
			ClassReplacer.FIRST.add("net.minecraft.network.NettyVarint21FrameDecoder", IOUtil.read("ilib/asm/rpl/VarIntDecoder.class"));
			ClassReplacer.FIRST.add("net.minecraft.network.NettyCompressionEncoder", IOUtil.read("ilib/asm/rpl/FastZip.class"));
			//ClassReplacer.FIRST.add("net.minecraft.network.NettyCompressionDecoder", IOUtil.read("ilib/asm/rpl/FastUnzip.class"));
		}

		if (Config.myNextTickList) {
			Nx("!NxUpdateTick");
		}

		if (Config.fastLightCheck) {
			Nx("!NiximChunkLight");
		}

		if (Config.fastMobSpawn) {
			ClassReplacer.FIRST.add("net.minecraft.world.WorldEntitySpawner", IOUtil.read("ilib/asm/rpl/MobSpawn.class"));
		}

		// endregion

		if (Config.shrinkLog) {
			try (ZipFile zf = new ZipFile(Helpers.getJarByClass(FMLLog.class))) {
				zf.getEntry("catserver/server/CatServer.class").getName();
			} catch (Exception e) {
				System.setOut(new PrintStream(DummyOutputStream.INSTANCE));
				//System.setErr(new PrintStream(DummyOutputStream.INSTANCE));
			}
			((org.apache.logging.log4j.core.Logger) FMLLog.log).setLevel(Level.INFO);
			changeLevel(Level.INFO);
		}

		// region bug修复
		// 投掷器炸服
		Nx("!CrashDispenser");
		// 村庄的门加载区块
		Nx("!VillageDoor");
		Nx("!VillagesDoor");
		// 熔炉破坏掉落经验
		Nx("!NxFurnaceExp");
		// 服务端生成粒子bug
		Nx("!ServerSpawnParticle");
		// 熔炉矿车燃料
		Nx("!FixFurnaceCart");
		// endregion

		boolean isClient = testClientSide();

		if (isClient) {
			try {
				Class.forName("optifine.OptiFineForgeTweaker", false, Loader.class.getClassLoader());
				hasOF = true;
				logger.info("检测到OF, 部分优化已经关闭");
			} catch (Throwable ignored) {}

			if (!hasOF) {
				// 镜子
				Nx("ilib/client/mirror/NxLockVD.class");
			}

			if ((Config.debug & 128) != 0) {
				Nx("!mock/VersionMock");
				Nx("!mock/MockMods");
				Nx("!mock/MockXRay");
				Nx("!mock/MockChannel");
				Nx("!mock/MockDisc");
			}

			if (!Config.noAnim.isEmpty()) {
				Nx("!NoTextureAnim");
			}

			if (Config.showPlayerName) {
				Nx("!client/PlayerNameSeen");
			}

			if (Config.infMods) {
				Nx("!INFINITYMods");
			}

			if (Config.fastCaps) {
				Nx("!FastCaps0");
				Nx("!FastCaps1");
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

			if (!hasOF && Config.lowerChunkBuf != 10) {
				Nx("!client/crd/NxMemoryLeak");
			}

			if (Config.noShade) {
				Nx("!client/NoShade");
			}

			if (Config.fixCRD) {
				Nx("!client/crd/NxBlockInfo");
				Nx("!client/crd/NxMem2");
				Nx("!client/crd/NxMem3");
				Nx("!client/crd/NxLight");
				Nx("!client/crd/NxChunkCache");
				Nx("!client/crd/NxBiomeDefault");
				Nx("!client/crd/NxBufCom");
				if (Config.otherSort) Nx("!client/crd/NxBuf");
			}

			if (Config.fastBiomeBlend) {
				Nx("!client/crd/NxBiomeColor");
			}

			if (Config.betterTooltip) {
				Nx("!client/NxTooltip");
			}

			if (Config.clearLava) {
				Nx("!client/NxClearLava");
			}

			if (!Config.customFont.isEmpty()) {
				// @Dynamic merged
				Nx("!client/NxFastFont");
				Nx("!client/NxNewFont");
			} else {
				if (Config.fastFont) {
					Nx("!client/NxFastFont");
				}
			}

			if (Config.noRecipeBook) {
				Nx("!client/NxSearch");
			}

			Nx("!client/CustomCreativeTab");
			Nx("!client/FakeTabsSupply");
			Nx("!client/NxAnvilLen");

			Nx("!ElytraRender");
			Nx("!GroupNPE");
			Nx("!SliderApply");
			Nx("!client/GlobalShaderHook");

			if (Config.farSight) {
				Nx("!client/FarSight");
			}

			if (Config.xiBao != 0) {
				if ((Config.xiBao & 1) != 0) {
					Nx("!client/XiBao");
				}
				if ((Config.xiBao & 2) != 0) {
					Nx("!client/XiBao2");
				}
				Nx("!client/XiBao3");
			}

			if (Config.asyncTexLoad) {
				Nx("!NoStripColor");

				Nx("!client/FastResPack");
				Nx("!client/FastDefaultPack");

				if (!hasOF) {
					Nx("!client/async/AsyncTexMap");
					niximUser("!client/async/AsyncTexMap$MyTask", true);
				}
				if (Config.asyncTexLoadEx) {
					Nx("!client/async/AsyncKillSplash");
					Nx("!client/async/AsyncModel");
					niximUser("!client/async/AsyncModel$MyTask", true);
					Nx("!client/async/AsyncModel2");
					Nx("!client/async/AsyncModelReg");
				}
				Nx("!client/async/NxBSM");
				Nx("!client/async/NxSprite");
				Nx("!client/async/NxLoaderEx");
				Nx("!client/async/NxTexUtil");
				ClassReplacer.LAST.add("net.minecraft.client.renderer.texture.Stitcher", IOUtil.read("ilib/asm/rpl/RplStitcher.class"));
			}

			if (Config.minimumFPS > 0 || Config.entityCulling) {
				if (Config.minimumFPS == 0) Config.minimumFPS = 20;
				Nx("!client/NiximRenderGlobal");
			}

			if (Config.showySelectBox) {
				Nx("!client/NxSelectionBox");
			}
			if (Config.commandEverywhere) {
				Nx("!client/NxPortal");
			}
			if (!Config.logChat || Config.chatLength != 100 || Config.chatHeight != 160) {
				Nx("!client/NiximChatGui");
			}
			if (Config.changeWorldSpeed > 1) {
				Nx("!client/NxFastSpawn");
			}
			if (!Config.clientBrand.equals("vanilla")) {
				Nx("!client/NxClientBrand");
			}
			if (Config.noRenderUpdateWhenUnload) {
				Nx("!client/NxChunkUnload");
			}
			if (Config.fastUri) {
				Nx("!client/NxFastUri");
			}
			if (Config.noSignLimit) {
				Nx("!client/NxEditSign");
			}
			if (Config.overlayShadow) {
				Nx("!client/NxOverlayShadow");
			}
			if (Config.entityCulling || Config.particleCulling) {
				Nx("!client/NxGetCamera");
				if (Config.particleCulling) {
					Nx("!client/NxParticleCull");
				}
			}
			if (Config.noSoManyAABB) {
				Nx("!client/NxBP4");
			}
			ClassReplacer.LAST.add("net.minecraft.client.renderer.block.model.ModelBlock$Deserializer", IOUtil.read("ilib/asm/nx/client/model/ModelBlockDeserializer.class"));
		} else {
			if (Config.noDuplicateLogin) {
				Nx("!NoDuplicateLogin");
			}
		}

		// 实体数据包.
		Nx("!IterateAllEntities");

		// 快速方块实体构建
		Nx("!FastTileConst");
		// 快速实体构建
		Nx("!FastEntityConst");

		// BlockPos$PooledMutableBlockPos的无锁化
		ClassReplacer.LAST.addDeep("net.minecraft.util.math.BlockPos$PooledMutableBlockPos", IOUtil.read("ilib/asm/rpl/PMB_TL.class"));

		// 重复利用ChunkOutputStream的缓冲区
		ClassReplacer.FIRST.add("net.minecraft.world.chunk.storage.RegionFile", IOUtil.read("ilib/asm/rpl/FastChunkSL.class"));

		// EntityDataManager无锁化
		Nx("!NoDataLock");

		if (Config.noPrefixWarning) {
			Nx("!NoPrefixWarning");
		}

		if (Config.chunkSaveChance != 100) {
			Nx("!NoUnusedChunks");
		}

		if (!Config.noAdvancement && Config.fastAdv) {
			Nx("!FastAdvancement");
		}

		if (Config.noJoinSpawn) {
			Nx("!NoJoinSpawn");
		}

		// 关闭实体碰撞
		if (Config.noCollision) {
			Nx("!NxWorldColl");
		}

		if (Config.packetBufferInfinity || (Config.nbtMaxLength != 2097152 && Config.nbtMaxLength != 0)) {
			Nx("!NxPacketSize");
		}

		if (Config.passLeaves) {
			Nx("!NxThroughLeaves");
		}

		if (Config.leavesMem) {
			Nx("!NxLeaves");
		}

		if (Config.noEnchantTax) {
			Nx("!NoEnchantTax");
		}

		// 开启TPS修改时不要过快的保存区块
		if (Config.enableTPSChange) {
			Nx("!NxChunkSave");
		}

		// 使用合并（没有两个方法）的ASM事件执行者
		if (Config.eventInvoker) {
			Nx("!NxEventBus");
		}

		if (Config.trimNBT) {
			Nx("!TrimNBT");
			Nx("!TrimNBT2");
			Nx("!TrimNBT3");
		}

		if (Config.replaceOIM) {
			ClassReplacer.FIRST.add("net.minecraft.util.ObjectIntIdentityMap", IOUtil.read("META-INF/nixim/ObjectIntIdentityMap.class"));
			Nx("META-INF/nixim/MapClear.class");
			Nx("META-INF/nixim/MapGet.class");
		}

		if (Config.asyncChunkGen) {
			Nx("!NxAsyncPCME");
			Nx("!NxPlayerChunks");
			ClassReplacer.LAST.addDeep("net.minecraft.world.gen.layer.IntCache", IOUtil.read("ilib/asm/rpl/FastIntCache.class"));
		}

		if (Config.entityAabbCache) {
			Nx("!NxRelAABB");
		}

		if (Config.fastRecipe) {
			Nx("!recipe/NxFastFurnace");
			Nx("!recipe/NxFastWorkbench");
			Nx("!recipe/NxInvCrafting");
			// 方便读取矿物词典
			Nx("!recipe/NxOreIng");
		}

		// 没意义的稿子优化
		if (Config.miscPickaxeOptimize) {
			Nx("!NxFastPickaxe");
		}

		if (Config.portalCache) {
			Nx("!NiximTeleporter");
		}

		if (Config.slabHelper) {
			Nx("!NiximSlab");
		}

		// 活塞移动完后再触发一次更新，避免幽灵方块
		if (Config.noPistonGhost) {
			Nx("!NoGhostSlime");
		}

		if (Config.boatFloat) {
			Nx("!BoatFloat");
		}

		// 删除部分颜色代码过滤
		if (Config.colorCode) {
			Nx("!NxColorCode");
		}

		if (Config.creativeNoClimb) {
			Nx("!NxCreativeFlyClimb");
		}

		if (Config.nxOre) {
			Nx("!NxOres");
		}

		// 攻击时判断目标实体碰撞箱的距离而不只是中心距离
		if (Config.attachFurther) {
			Nx("!NxAttackFurther");
		}

		// 优化方块掉落和世界生成
		if (Config.worldGenOpt) {
			Nx("!NxFalling");
			Nx("!NxWorldGen1");
		}

		if (Config.noSoManyBlockPos2) {
			Nx("!NxBP1");
			Nx("!NxBP2");
			Nx("!NxBP3");
			Nx("!NxBP5");
			Nx("!NxGrass");
		}

		if (Config.noSoManyBlockPos) {
			Nx("!NxRayTrace");
			Nx("!NxCachedPos");
			Nx("!NxCachedPos2");
			Nx("!NxCachedPos3");
		}

		if (Config.noSoManyAABB) {
			Nx("!EntityAABBFx");
		}

		if (Config.fastHopper) {
			Nx("!NxHopper");
		}

		// 物品合并优化
		if (Config.itemOpt) {
			Nx("!NxItemMerge");
		}

		if (Config.chunkOpt) {
			Nx("!NoAlloc");
			Nx("!NxChunkItr");
			Nx("!ChunkGetEntity");
		}

		if (Config.chunkOpt || !Config.inheritEntityType) {
			// 实体列表优化
			Nx("!FastEntitySet");
			Nx("!FastXpOrb");
		} else if (Config.xpCooldown != 2) {
			Nx("!FastXpOrb");
		}

		if (Config.fastNodeWalk) {
			Nx("ilib/misc/FastNodeWalk.class");
			Nx("ilib/misc/FastNodeFly.class");
		}

		if (Config.fastCollision) {
			Nx("!NxGetCollision");
			Nx("!NxCollision2");
			Nx("!NxCollision3");
			Nx("!NxCollision4");
		} else if (Config.noCollision) {
			Nx("!NxCollision2");
		}

		if (Config.fastRandomTick) {
			Nx("!NxTickChunk");
		}

		if (Config.autoNewUUID) {
			Nx("!AutoNewUUID");
		}
		//Nx("!FastMove");

		NiximProxy.instance.getParentMap().clear();
	}

	private static void makeModCompatible(Set<ASMDataTable.ASMData> mods) {
		for (ASMDataTable.ASMData mod : mods) {
			String cid = mod.getClassName().substring(mod.getClassName().lastIndexOf('.') + 1);
			switch (cid) {
				case "AstralSorcery":
					Nx("ilib/asm/modcompat/AstralFuckAsyncCheck.class");
					break;
			}
			logger.info("Loading mod compat for " + cid);
		}
	}

	private static WrappedTransformers wrapper;
	private static final ArrayList<IClassTransformer> ilTransformers = new ArrayList<>(5);
	private static boolean inProgress;

	static void onUpdate(WrappedTransformers list) {
		if (inProgress) return;
		inProgress = true;

		if (Config.replaceTransformer) {
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
					case "net.minecraftforge.fml.common.asm.transformers.ModAPITransformer":
						list.set(i, new ModAPITransformer());
						break;
					case "$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer":
						list.set(i, new EventSubTrans());
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
		}

		HashSet<IClassTransformer> set = new LinkedHashSet<>(list);
		set.remove(ClassReplacer.FIRST);
		set.remove(ClassReplacer.LAST);
		for (IClassTransformer ilTransformer : ilTransformers) {
			set.remove(ilTransformer);
		}

		net.minecraftforge.fml.common.asm.transformers.ModAPITransformer mtr = null;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof net.minecraftforge.fml.common.asm.transformers.ModAPITransformer) {
				mtr = (net.minecraftforge.fml.common.asm.transformers.ModAPITransformer) list.get(i);
				set.remove(mtr);
			}
		}

		list.clear();
		list.addAll(set);
		list.add(1, ClassReplacer.FIRST);
		list.addAll(ilTransformers);
		list.add(ClassReplacer.LAST);
		if (mtr != null) list.add(mtr);

		inProgress = false;
	}

	public static void addTransformer(IClassTransformer transformer) {
		ilTransformers.add(transformer);
	}

	public static void handleASMData(ASMDataTable store) {
		Annotations = store;
	}

	public static void onModConstruct() {
		NiximProxy.instance.getParentMap().clear();

		if (Config.replaceTransformer) {
			SideTransformer.lockdown();
			EventSubscriberTransformer.lockdown();
		}

		makeModCompatible(Annotations.getAll("net.minecraftforge.fml.common.Mod"));
		for (ModCandidate lib : Annotations.getCandidatesFor("ilib")) {
			lib.getClassList().remove("ilib/asm/util/EventInvokerV2");
		}
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
		try {
			registerNixims();
		} catch (Exception e) {
			Helpers.athrow(e);
		}
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
			synchronized (Loader.class) {
				if (wrapper != null) return;
				try {
					ilTransformers.add(new Transformer());
					ilTransformers.add(NiximProxy.instance);

					Field f = ReflectionUtils.getField(LaunchClassLoader.class, "transformers");

					onUpdate(wrapper = new WrappedTransformers((List<IClassTransformer>) f.get(Launch.classLoader)));
					f.set(Launch.classLoader, wrapper);
				} catch (Exception e) {
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
	}

	private static Boolean side;

	public static boolean testClientSide() {
		if (side != null) return side;
		try {
			Class.forName("net.minecraft.client.ClientBrandRetriever");
			return side = true;
		} catch (ClassNotFoundException e) {
			return side = false;
		}
	}
}