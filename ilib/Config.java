package ilib;

import ilib.asm.Loader;
import ilib.event.PowershotEvent;
import roj.asm.nixim.NiximSystem;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.FileConfig;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.reflect.ClassDefiner;
import roj.util.Helpers;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static net.minecraftforge.fml.common.Loader.isModLoaded;

/**
 * @author Roj234
 * @since 2021/5/31 21:20
 */
public final class Config extends FileConfig {
	public static boolean reloadSound, replaceOIM, replaceEntityList, isTrashEnable, fastLeaveDecay, mobSpawnFullBlock,
		noticeItemChange, noRepairCost, enchantOverload, fastDismount, noRenderUpdateWhenUnload, fastCaps,
		jumpAttack, enableMissingItemCreation, fixFont, noShitSound, enableTPSChange, fixMinecart, fastRecipe, shrinkLog,
		noDuplicateLogin, commandEverywhere, logChat, registerItem, autoClimb, disablePotionShift, showySelectBox,
		noSoManyBlockPos, fastLightCheck, fastCollision, lootR, fastRandomTick, miscPickaxeOptimize, modelCache,
		showDPS, moreEggs, portalCache, attachFurther, entityCulling, noSoManyAABB, fastHopper, autoNewUUID,
		entityAabbCache, noAdvancement, eventInvoker, removePatchy, slabHelper, packetBufferInfinity,
		IwantLight, noRecipeBook, fixNaNHealth, fixThreadIssues, noCollision, noEnchantTax, noAnvilTax, betterF3,
		injectLWP, clearLava, F3_ins, F3_render, F3_hard, safer, fastFont, noGhostBlock, checkStackDepth,
		betterKeyboard, separateDismount, noPistonGhost, myNextTickList, fixCRD, oshi886, replaceEnumFacing,
		noSoManyBlockPos2, replaceTransformer, itemOpt, smallBuf, soundRecycle, tractDirectMem, traceAABB,
		traceBP, passLeaves, leavesMem, worldGenOpt, betterTooltip, nxOre, fastMobSpawn, fastUri,
		noSignLimit, creativeNoClimb, overlayShadow, fixSBOverF3, noMoreBats, particleCulling, boatFloat,
		chunkOpt, colorCode, controlViaFile, fastNodeWalk, asyncTexLoad, otherSort, asyncChunkGen,
		trimEvent, fastEntityList, inheritEntityType, infMods, asyncPopulate, fastAdv, noJoinSpawn,
		noShade, trimNBT, guiButton, doorMod, noPrefixWarning, fixUUIDDup, noDeathAnim, fastBiomeBlend,
		fastPacketProc, biomeBlendFabulously, asyncTexLoadEx, WTFIsIt, farSight, showPlayerName,
		fastExplosion, fastExplosion2;

	public static int fpsLowTime, fpsLowFPS, clientNetworkTimeout, chatLength, debug, maxParticleCountPerLayer,
		maxChunkTick, siFrameTime, pagedTooltipLen, tooltipFlag, pagedTooltipTime, lowerChunkBuf, chatHeight,
		minimumFPS, maxChunkBufCnt, chunkSaveChance, xpCooldown, biomeBlendCache, biomeBlendRegion, xiBao;

	public static byte threadPriority, dynamicViewDistance, changeWorldSpeed;

	public static long maxChunkTimeTick, nbtMaxLength;

	public static String title, clientBrand, customFont;

	public static Set<String> disableTileEntities, siTimeExcludeTargets, siTimeExcludeDmgs, siTimeExcludeAttackers, noUpdate, noAnim;

	public static CMapping setAttributeRange;

	public static float attackCD, fpsLowVol, complexModelThreshold;

	public static Set<Map.Entry<String, CEntry>> powerShotRequirement;

	public static MyHashSet<String> freezeUnknownEntries;

	public static Map<String, String> idReplacement;

	public static int compressionLevel = -1;

	public static final Config instance;

	public static boolean disableAllNixim;

	static {
		instance = new Config();
		if (threadPriority != -1) {
			Thread.currentThread().setPriority(threadPriority);
		}
		// todo
		//  1. phosphor
	}

	// 往下不是配置项，而是算出来的
	public static boolean shouldAsyncWorldTick;

	// region Incompatible check
	public static final int ENTITYCULLING = 1, PARTICLECULLING = 2, UNIDICT = 4, SUPERPORTALCACHE = 8, DEUF = 16, BETTERBIOMEBLEND = 32, AQUAACROBATICS = 64;
	private static int incompatibleMods;

	public static List<String> checkIncompatibleMod() {
		SimpleList<String> error = new SimpleList<>();
		if (nxOre && isModLoaded("unidict")) {
			instance.getConfig().put("优化.矿物词典优化", nxOre = false);
			error.add("矿物词典优化与Unidict不兼容");
		}
		if (isModLoaded("foamfix")) {
			Configuration cfg = new Configuration(new File("config/foamfix.cfg"));
			cfg.load();
			Property prop = cfg.get("coremod", "fasterEntityLookup", true, "");
			if (prop.getBoolean()) {
				prop.set(false);
				cfg.save();
				error.add("实体获取优化与Foamfix的子功能不兼容");
			}
		}
		return error;
	}

	@Override
	protected void init() {
		Loader.logger.info("开始检测存在文件注入冲突的模组");
		try {
			//noinspection all
			File mods = Helpers.getJarByClass(Config.class).getParentFile();
			//noinspection all
			for (File file : mods.listFiles()) {
				String name = file.getName().toLowerCase();
				if (file.isFile() && name.endsWith(".zip") || name.endsWith(".jar")) {
					checkCompatiable(file);
				}
			}
			//noinspection all
			File file1 = new File(mods, "1.12.2");
			if (file1.isDirectory()) {
				for (File file : file1.listFiles()) {
					String name = file.getName().toLowerCase();
					if (file.isFile() && name.endsWith(".zip") || name.endsWith(".jar")) {
						checkCompatiable(file);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		Loader.logger.info("完成检测存在文件注入冲突的模组");

		super.init();
	}

	private void checkCompatiable(File file) {
		try (ZipFile zf = new ZipFile(file)) {
			if (zf.getEntry("meldexun/entityculling/EntityCulling.class") != null) {
				incompatibleMods |= 3;
			} else if (zf.getEntry("bl4ckscor3/mod/particleculling/ParticleCulling.class") != null) {
				incompatibleMods |= 2;
			} else if (zf.getEntry("wanion/unidict/UniDict.class") != null) {
				incompatibleMods |= 4;
			} else if (zf.getEntry("com/lucunji/superportalcache/PortalSuperCache.class") != null) {
				incompatibleMods |= 8;
			} else if (zf.getEntry("de/cas_ual_ty/uuidfix/UUIDFix.class") != null) {
				incompatibleMods |= 16;
			} else if (zf.getEntry("fionathemortal/betterbiomeblend/BetterBiomeBlend.class") != null) {
				incompatibleMods |= 32;
			} else if (zf.getEntry("mixins.aquaacrobatics.refmap.json") != null) {
				incompatibleMods |= 64;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static boolean hasIncompatibleCoremod(int mod) {
		return (incompatibleMods & mod) != 0;
	}

	// endregion

	private Config() {
		super(new File("config/ImprovementLibrary.json"));
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void load(CMapping map) {
		map.dot(true);
		map.clearComments(true);

		// region 优化

		// region 默认开启
		map.putCommentDotted("优化.清理一些垃圾", "清除LaunchClassLoader和mixin(如果安装)的包缓存, 锁定AccessTransformer的项");
		isTrashEnable = map.putIfAbsent("优化.清理一些垃圾", true);
		miscPickaxeOptimize = map.putIfAbsent("优化.稿子优化", true);
		map.putCommentDotted("优化.下界传送门缓存", "差不多就是那个玩意");
		portalCache = map.putIfAbsent("优化.下界传送门缓存", !hasIncompatibleCoremod(SUPERPORTALCACHE));
		eventInvoker = map.putIfAbsent("优化.事件调用优化", true);
		map.putCommentDotted("优化.修复多线程问题", "不修复可能会使进入世界卡死, 也不止这一个原因会导致卡死");
		fixThreadIssues = map.putIfAbsent("优化.修复多线程问题", true);
		fixNaNHealth = map.putIfAbsent("优化.修复实体假死(NaN/Inf)的BUG", true);
		map.putCommentDotted("优化.替换MC的穷举法提高熔炉/工作台合成判断速度", "与两个Fast系均兼容,安装FastFurnace可以进一步加速,FastWorkbench可以删除");
		fastRecipe = map.putIfAbsent("优化.替换MC的穷举法提高熔炉/工作台合成判断速度", true);
		fastCollision = map.putIfAbsent("优化.优化碰撞体积获取", true);
		map.putCommentDotted("优化.方块更新优化2", "优化方块更新有关代码，使之使用PooledMutableBlocKPos\n" +
			"其他5个优化");
		noSoManyBlockPos2 = map.putIfAbsent("优化.方块更新优化2", true);
		nxOre = map.putIfAbsent("优化.矿物词典优化", !hasIncompatibleCoremod(UNIDICT));
		map.putCommentDotted("优化.优化随机刻", "其实没啥用，除非你randomTickSpeed开到几千");
		fastRandomTick = map.putIfAbsent("优化.优化随机刻", true);
		leavesMem = map.putIfAbsent("优化.优化树叶的内存占用", true);
		map.putCommentDotted("优化.替换EnumFacing\\.values()", "降低大量区块更新时的CPU占用");
		replaceEnumFacing = map.putIfAbsent("优化.替换EnumFacing\\.values()", true);
		worldGenOpt = map.putIfAbsent("优化.世界生成优化", true);
		fastExplosion = map.putIfAbsent("优化.爆炸优化", true);
		map.putCommentDotted("优化.激进爆炸优化", "优化后的方法无法触发ExplosionEvent.Detonate事件");
		fastExplosion2 = map.putIfAbsent("优化.激进爆炸优化", false);

		fastHopper = map.putIfAbsent("优化.漏斗优化", true);
		trimEvent = map.putIfAbsent("优化.压缩实体事件", true);
		fastPacketProc = map.putIfAbsent("优化.数据包压缩优化", true);

		noSoManyAABB = map.putIfAbsent("优化.实体优化2", true);
		chunkOpt = map.putIfAbsent("优化.区块迭代器和实体获取优化", true);
		itemOpt = map.putIfAbsent("优化.EntityItem合并优化", true);
		fastNodeWalk = map.putIfAbsent("优化.实体寻路优化", true);

		map.putCommentDotted("优化.区块.异步地形生成", "可能存在bug");
		asyncChunkGen = map.putIfAbsent("优化.区块.异步地形生成", false);
		maxChunkTick = map.putIfAbsent("优化.区块.每t最大生成", 32);
		map.putCommentDotted("优化.区块.每t最大耗时", "单位为微秒, 10000为10ms");
		maxChunkTimeTick = map.putIfAbsent("优化.区块.每t最大耗时", 10000) * 1000L;

		fastMobSpawn = map.putIfAbsent("优化.测试.生物生成", false);
		fastEntityList = map.putIfAbsent("优化.测试.区块实体集合(TypeFilterableList)", true);
		inheritEntityType = !map.putIfAbsent("优化.测试.区块实体集合-不在新实例中继承实体类型", true);

		// endregion
		// region 默认关闭
		noCollision = map.putIfAbsent("优化.我不需要碰撞！", false);
		map.putCommentDotted("优化.实体AABB缓存", "Surge已有同类功能");
		entityAabbCache = map.putIfAbsent("优化.实体AABB缓存", false);
		map.putCommentDotted("优化.高效的能力兼容检测", "以内存为代价提高TPS,至高可达50%\n" +
			"来自1.16.5的祝福(FastCaps,尽管这个模组只有空壳)\n" +
			"但是,有的时候,创意才是最重要的");
		fastCaps = map.putIfAbsent("优化.高效的能力兼容检测", true);
		map.putCommentDotted("优化.快速的脱离", "可能造成一些基于矿车/船的特性构造的设备失效");
		fastDismount = map.putIfAbsent("优化.快速的脱离", false);
		map.putCommentDotted("优化.方块更新优化", "优化方块更新有关代码，使之使用PooledMutableBlockPos\n" +
			"专为红石设计，大幅降低红石运算开销\n" +
			"和修改TPS更配哦\n" +
			"对依赖同一gt内更新顺序的设备会有影响\n" +
			"同一gt内将按照BlockPos的哈希顺序排序，这意味着即使只是平移，执行顺序也会不同");
		noSoManyBlockPos = map.putIfAbsent("优化.方块更新优化", false);
		map.putCommentDotted("优化.禁止过度嵌套的方块更新", "需求:打开方块更新优化\n" +
			"禁止在栈深超过1000的情况下继续方块更新\n" +
			"能够修复一部分CascadingWorldGeneration问题\n" +
			"微量降低性能(实际上把提升的性能都降回来了)\n" +
			"能够预防此类崩溃");
		checkStackDepth = map.putIfAbsent("优化.禁止过度嵌套的方块更新", false);
		//map.putCommentDotted("优化.MyNextTickList", "防止出现NextTickList not synch异常\n" +
		//        "并带有一些优化");
		//myNextTickList = map.putIfAbsent("优化.MyNextTickList", false);

		map.putCommentDotted("优化.删减默认NBT", "可以减小实体在存档中占用的空间。");
		trimNBT = map.putIfAbsent("优化.删减默认NBT", false);
		// endregion

		map.putCommentDotted("优化.测试", "这些优化可能存在bug");

		asyncTexLoad = map.putIfAbsent("优化.测试.材质加载优化", false);
		asyncTexLoadEx = map.putIfAbsent("优化.测试.异步材质加载", false);
		modelCache = map.putIfAbsent("优化.测试.模型缓存", false);
		fastLightCheck = map.putIfAbsent("优化.测试.替换区块中的光照计算，提高效率", false);

		compressionLevel = map.putIfAbsent("优化.测试.网络优化.zip压缩等级", -1);

		map.putCommentDotted("优化.弃用.替换ObjectIdentityMap", "替换为IntMap\n" +
			"何时开启这玩意能优化，而不是反过来，怕是作者才能做出正确的决定（");
		replaceOIM = map.putIfAbsent("优化.弃用.替换ObjectIdentityMap", false);

		boolean side = Loader.testClientSide();
		if (side) {
			fastBiomeBlend = map.putIfAbsent("优化.客户端.快速生物群系混合.启用", !hasIncompatibleCoremod(BETTERBIOMEBLEND));
			biomeBlendCache = map.putIfAbsent("优化.客户端.快速生物群系混合.缓存容量", 100);
			map.putCommentDotted("优化.客户端.快速生物群系混合.范围", "0=黑色,1=1x1,2=3x3,3=5x5,以此类推");
			biomeBlendRegion = map.putIfAbsent("优化.客户端.快速生物群系混合.范围", 3);
			biomeBlendFabulously = map.putIfAbsent("优化.客户端.快速生物群系混合.我眼瞎了", false);

			map.putCommentDotted("优化.客户端.关闭动态材质",
				"当该列表非空时,只允许列表内的材质动画(上传),这可以在较老的电脑上提高FPS\n" +
					"您还可以安装类似Optifine或LessLag模组，它们可以提供类似的效果\n" +
					"并且，明显的，专注的优化模组也许比我做的更好，比如【只更新能看到的动态材质】\n" +
					"minecraft:blocks/<流体 water/lava>_flow\n" +
					"minecraft:items/<id>\n" +
					"minecraft:blocks/fire_layer_<0/1>");
			noAnim = map.getOrCreateList("优化.客户端.关闭动态材质").asStringSet();

			// region 客户端Only
			fastFont = map.putIfAbsent("优化.客户端.快速字体渲染", true);
			map.putCommentDotted("优化.客户端.自动修复无声音问题", "如果还是没用, 请在任意世界内输入//reloadSoundMgr指令");
			reloadSound = map.putIfAbsent("优化.客户端.自动修复无声音问题", true);
			map.putCommentDotted("优化.客户端.最大粒子效果数量", "实际上这是每个粒子层的数量,而一共有4-8个层");
			maxParticleCountPerLayer = map.putIfAbsent("优化.客户端.最大粒子效果数量", 4096);

			map.putCommentDotted("优化.客户端.自动降低FPS.时间", "单位秒, 0关闭");
			fpsLowTime = map.putIfAbsent("优化.客户端.自动降低FPS.时间", 30);
			fpsLowFPS = map.putIfAbsent("优化.客户端.自动降低FPS.目标帧率", 10);
			map.putCommentDotted("优化.客户端.自动降低FPS.目标音量", "范围0-1");
			fpsLowVol = (float) map.putIfAbsent("优化.客户端.自动降低FPS.目标音量", 1.0f);

			map.putCommentDotted("优化.客户端.最低目标帧率", "有时候，我们要直视性能瓶颈\n" +
				"如果实在无法避免的话\n" +
				"那就不渲染了\n" +
				"在你的帧率低于目标帧率时终止剩余【非方块】的渲染\n" +
				"0关闭");
			minimumFPS = map.putIfAbsent("优化.客户端.最低目标帧率", 30);

			map.putCommentDotted("优化.客户端.提高切换世界速度", "1: 不显示[下载地形中]\n" +
				"2: 禁用切换世界时检测碰撞\n" +
				"3: 禁止切换世界时GC");
			changeWorldSpeed = (byte) map.putIfAbsent("优化.客户端.提高切换世界速度", 3);

			map.putCommentDotted("优化.客户端.优化区块渲染缓冲", "将缓冲区余量由十倍降为<n>倍\n" +
				"越低的值CPU占用越低，世界加载越慢\n" +
				"Optifine安装时无效");
			lowerChunkBuf = map.putIfAbsent("优化.客户端.优化区块渲染缓冲", 3);
			map.putCommentDotted("优化.客户端.最大区块渲染线程数", "设置为0使用CPU虚拟核心数\n" +
				"虚拟:考虑Intel(R) Hyper Thread技术后的可用核心数\n" +
				"Optifine安装时无效");
			maxChunkBufCnt = map.putIfAbsent("优化.客户端.最大区块渲染线程数", 6);

			soundRecycle = map.putIfAbsent("优化.客户端.重复使用音频缓冲", true);

			map.putCommentDotted("优化.客户端.优化方块渲染", "也许可以提高10-20FPS");
			fixCRD = map.putIfAbsent("优化.客户端.优化方块渲染", true);

			map.putCommentDotted("优化.客户端.优化透明排序", "换用缓存的基本类型数组\n" +
				"由于作者算法水平过低\n" +
				"部分流体显示异常");
			otherSort = map.putIfAbsent("优化.客户端.优化透明排序", false);

			map.putCommentDotted("优化.客户端.复杂模型阈值", "平均每面四边形数大于该值时才会使用高级光照\n" +
				"而不是一直使用\n" +
				"在不影响显示的情况下能够降低50%的区块更新CPU\n" +
				"如果部分模型出现黑边,请降低它\n" +
				"需求: 优化方块渲染开启\n" +
				"Optifine安装时无效");
			complexModelThreshold = (float) map.putIfAbsent("优化.客户端.复杂模型阈值", 4f);

			map.putCommentDotted("优化.客户端.降低渲染缓冲区大小", "根据统计数据,可以节约90%的方块渲染常驻内存\n" +
				"如果是2G内存分配,则可以减少370MB\n" +
				"在使用带有大量3D模型的材质包时不建议开启(高分辨率无关系)");
			smallBuf = map.putIfAbsent("优化.客户端.降低渲染缓冲区大小", true);

			map.putCommentDotted("优化.客户端.替换实体列表", "使用WeakSet替换原先的ArrayList, 有效降低长时间游玩的内存占用");
			replaceEntityList = map.putIfAbsent("优化.客户端.替换实体列表", true);
			particleCulling = map.putIfAbsent("优化.客户端.粒子切除", !hasIncompatibleCoremod(PARTICLECULLING));
			entityCulling = map.putIfAbsent("优化.客户端.实体切除", !hasIncompatibleCoremod(ENTITYCULLING | PARTICLECULLING));
			// endregion
		} else {
			// region 服务端Only
			map.putCommentDotted("优化.服务端.根据TPS自动调节视距", "这表示最小值, 0关闭");
			dynamicViewDistance = (byte) map.putIfAbsent("优化.服务端.根据TPS自动调节视距", 0);
			// endregion
		}

		// endregion
		// region 功能
		map.putCommentDotted("功能.多少tick捡起一个经验球", "默认为2, 改为1有少量优化");
		xpCooldown = map.putIfAbsent("功能.多少tick捡起一个经验球", 1);
		doorMod = map.putIfAbsent("功能.打开门时同时打开附近的门", false);
		enableTPSChange = map.putIfAbsent("功能.改变Tick速度", false);
		enableMissingItemCreation = map.putIfAbsent("功能.为没有物品的方块创建物品", false);
		map.putCommentDotted("功能.更多的生成蛋", "为一些原版没有生成蛋的实体生成蛋");
		moreEggs = map.putIfAbsent("功能.更多的生成蛋", true);
		mobSpawnFullBlock = map.putIfAbsent("功能.怪物只在完整方块上生成", false);
		fixMinecart = map.putIfAbsent("功能.只有玩家可以坐矿车", false);
		jumpAttack = map.putIfAbsent("功能.高处跳下提高攻击伤害", false);
		map.putCommentDotted("功能.注册ImpLib的物品", "例如'位置选择工具'");
		registerItem = map.putIfAbsent("功能.注册ImpLib的物品", true);
		map.putCommentDotted("功能.禁用方块实体", "输入一些方块实体的id以禁止它们从存档中被读取");
		disableTileEntities = map.getOrCreateList("功能.禁用方块实体").asStringSet();
		noAdvancement = map.putIfAbsent("功能.禁用进度", false);
		map.putCommentDotted("功能.禁用合成书", "还可以节省带宽和内存!");
		noRecipeBook = map.putIfAbsent("功能.禁用合成书", true);
		map.putCommentDotted("功能.攻击CD百分比", "0-1: 在CD百分比恢复到此值前不能造成伤害\n" +
			"0: 完全删除攻击CD (不修改物品NBT)" + "-1: 不使用此功能");
		attackCD = (float) map.putIfAbsent("功能.攻击CD百分比", -1f);
		noEnchantTax = map.putIfAbsent("功能.附魔不掉等级，而是经验", false);
		noAnvilTax = map.putIfAbsent("功能.铁X.不掉等级，而是经验", false);
		map.putCommentDotted("功能.铁X.删除RepairCost", "允许无限的修复一件物品");
		noRepairCost = map.putIfAbsent("功能.铁X.删除RepairCost", false);
		enchantOverload = map.putIfAbsent("功能.铁X.允许合成超过最大等级的附魔书", false);

		map.putCommentDotted("功能.NBT最大长度", "单位KB, 防止因为过长的NBT无法进入服务器");
		nbtMaxLength = (long) map.putIfAbsent("功能.NBT最大长度", 2048) << 10;
		map.putCommentDotted("功能.删除数据包的数据长度限制", "不影响NBT,而是数组和字符串");
		packetBufferInfinity = map.putIfAbsent("功能.删除数据包的数据长度限制", false);

		noPrefixWarning = map.putIfAbsent("功能.删除讨人厌的危险prefix警告", true);

		noPistonGhost = map.putIfAbsent("功能.修复活塞导致的幽灵方块", false);
		boatFloat = map.putIfAbsent("功能.船不会沉", false);
		colorCode = map.putIfAbsent("功能.允许输入彩色字", false);

		map.putCommentDotted("功能.修改无敌帧", "长度的单位是tick, -1不修改");
		siFrameTime = map.putIfAbsent("功能.修改无敌帧.长度", -1);
		siTimeExcludeAttackers = map.putIfAbsent("功能.修改无敌帧.攻击者黑名单", CList.of("minecraft:slime", "tconstruct:blueslime", "thaumcraft:thaumslime")).asList().asStringSet();
		siTimeExcludeDmgs = map.putIfAbsent("功能.修改无敌帧.伤害源黑名单", CList.of("inFire", "lava", "cactus", "lightningBolt", "inWall", "hotFloor")).asList().asStringSet();
		siTimeExcludeTargets = map.getOrCreateList("功能.修改无敌帧.攻击目标黑名单").asStringSet();

		setAttributeRange = map.getOrCreateMap("功能.调节属性范围");

		map.putCommentDotted("功能.安全管理", "替换安全管理器以防止mod启动进程，监听端口，或者加载native\n" +
			"当然，如果它像ImpLib一样不讲武德那实际上有多少用就不好说了\n" +
			"不兼容CatServer(服务器开这个干啥)");
		safer = map.putIfAbsent("功能.安全管理.启用", false);
		if (map.putIfAbsent("功能.安全管理.禁止联网", false)) {
			noUpdate = map.getOrCreateList("功能.安全管理.禁止联网白名单").asStringSet();
		}

		autoNewUUID = map.putIfAbsent("功能.自动修复UUID重复", !hasIncompatibleCoremod(DEUF));
		noGhostBlock = map.putIfAbsent("功能.防止幽灵方块出现", true);
		passLeaves = map.putIfAbsent("功能.可以穿过树叶", false);
		creativeNoClimb = map.putIfAbsent("功能.创造飞行不被可攀爬方块减速", true);
		attachFurther = map.putIfAbsent("功能.修复末影龙等大型实体攻击距离的bug", true);

		map.putCommentDotted("功能.允许热重载配置", "不是所有配置都支持重载\n" +
			"部分Nixim项仅根据布尔值开启或关闭而没有注入后的检测\n" +
			"部分数值/列表类配置在特定时机后会进行运算使得重载无效");
		if (instance == null && map.putIfAbsent("功能.允许热重载配置", false)) {
			Thread t = new Thread(() -> {
				WatchService ws;
				try {
					ws = FileSystems.getDefault().newWatchService();
					getFile().getParentFile().toPath().register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				while (true) {
					try {
						if (ws.take().watchable().toString().contains("ImprovementLibrary")) {
							reload();
						}
					} catch (InterruptedException ignored) {}
				}
			});
			t.setName("ImpLib配置重载");
			t.setDaemon(true);
			t.start();
		}


		if (side) {
			showPlayerName = map.putIfAbsent("功能.客户端.一直显示玩家昵称", false);

			guiButton = map.putIfAbsent("功能.客户端.开启主菜单的按钮", true);
			noDeathAnim = map.putIfAbsent("功能.客户端.关闭死亡动画", false);

			noSignLimit = map.putIfAbsent("功能.客户端.删除告示牌长度限制", true);
			overlayShadow = map.putIfAbsent("功能.客户端.提高actionbar消息的可读性", true);
			commandEverywhere = map.putIfAbsent("功能.客户端.删除反胃效果", true);
			noShitSound = map.putIfAbsent("功能.客户端.删除洞穴音效", true);
			fixSBOverF3 = map.putIfAbsent("功能.客户端.计分版不会覆盖F3", true);

			map.putCommentDotted("功能.客户端.在日志中记录聊天", "是的, 你可以关闭它\n玩单人游戏时依然会显示");
			logChat = map.putIfAbsent("功能.客户端.在日志中记录聊天", true);
			map.putCommentDotted("功能.客户端.聊天记录最大长度", "这是指客户端UI中的历史记录总条数");
			chatLength = map.putIfAbsent("功能.客户端.聊天记录最大长度", 100);
			map.putCommentDotted("功能.客户端.聊天记录最大显示长度", "这是指客户端UI的显示高度(像素)");
			chatHeight = map.putIfAbsent("功能.客户端.聊天记录最大显示长度", 160);

			map.putCommentDotted("功能.客户端.中文模式开启英文字宽", "选择英文, 你会发现这时的英文字符都会更宽\n" +
				"开启以把它带入任何语言");
			fixFont = map.putIfAbsent("功能.客户端.中文模式开启英文字宽", false);

			map.putCommentDotted("功能.客户端.重复键盘事件", "难道你不觉得，打字时长按无效很烦么\n" +
				"可能造成以下问题: 长按E打开背包，它又没了（你会长按么）");
			betterKeyboard = map.putIfAbsent("功能.客户端.重复键盘事件", true);

			map.putCommentDotted("功能.客户端.独立的脱离按键", "分开潜行与脱离");
			separateDismount = map.putIfAbsent("功能.客户端.独立的脱离按键", false);

			betterF3 = map.putIfAbsent("功能.客户端.更好的F3.启用", true);
			F3_ins = map.putIfAbsent("功能.客户端.更好的F3.本地服务端信息", true);
			F3_render = map.putIfAbsent("功能.客户端.更好的F3.渲染信息", true);
			F3_hard = map.putIfAbsent("功能.客户端.更好的F3.硬件信息", false);

			map.putCommentDotted("功能.客户端.更好的F3.不显示CPU类型", "减少0.4s启动时间\nMojang NB!");
			oshi886 = map.putIfAbsent("功能.客户端.更好的F3.不显示CPU类型", true);

			map.putCommentDotted("功能.客户端.清澈的岩浆", "如果要实现'I See Lava' mod的全部功能，你需要用材质包把岩浆的材质替换成半透明的");
			clearLava = map.putIfAbsent("功能.客户端.清澈的岩浆", false);
			map.putCommentDotted("功能.客户端.自定义字体", "效果一般...感觉做的挺失败的\n" +
				"比如在下面填个宋体，黑体");
			customFont = map.putIfAbsent("功能.客户端.自定义字体", "");

			int tooltipFlags = map.putIfAbsent("功能.客户端.高级提示框.注册名", true) ? 1 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.高级提示框.未本地化名", false) ? 1 << 1 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.高级提示框.矿物词典", false) ? 1 << 2 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.高级提示框.NBT", false) ? 1 << 3 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.高级提示框.食物", false) ? 1 << 4 : 0;

			tooltipFlags |= map.putIfAbsent("功能.客户端.流体提示框.粘度", true) ? 1 << 5 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.流体提示框.亮度", false) ? 1 << 6 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.流体提示框.温度", false) ? 1 << 7 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.流体提示框.颜色", false) ? 1 << 8 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.流体提示框.密度", false) ? 1 << 9 : 0;
			tooltipFlags |= map.putIfAbsent("功能.客户端.流体提示框.气体", false) ? 1 << 10 : 0;
			tooltipFlag = tooltipFlags | (map.putIfAbsent("功能.客户端.流体提示框.可放置", false) ? 1 << 11 : 0);

			map.putCommentDotted("功能.客户端.Tooltip分页", "长度改-1关闭");
			pagedTooltipLen = map.putIfAbsent("功能.客户端.Tooltip分页.长度", 16);
			pagedTooltipTime = map.putIfAbsent("功能.客户端.Tooltip分页.切换时间(帧)", 300);
			noticeItemChange = map.putIfAbsent("功能.客户端.提示物品改变", false);

			title = map.putIfAbsent("功能.客户端.窗口标题", "Minecraft 1.12.2");
			clientNetworkTimeout = map.putIfAbsent("功能.客户端.连接服务器的超时", 30);
			autoClimb = map.putIfAbsent("功能.客户端.自动爬楼梯", true);
			showDPS = map.putIfAbsent("功能.客户端.显示DPS", true);
			map.putCommentDotted("功能.客户端.关闭Potion Shift", "药水效果不再使背包界面右移");
			disablePotionShift = map.putIfAbsent("功能.客户端.关闭Potion Shift", true);
			map.putCommentDotted("功能.客户端.修复卡死", "删除mojang提供的patchy(服务器黑名单)\n" +
				"它是主线程读网络,程序员都知道这有多么操蛋\n" +
				"然而不少mod也这么干\n" +
				"比如JECH, 以及AbyssalCraft");
			removePatchy = map.putIfAbsent("功能.客户端.修复卡死", false);
			map.putCommentDotted("功能.客户端.更显眼的选择框", "我都不知道我做这玩意是为了啥");
			showySelectBox = map.putIfAbsent("功能.客户端.更显眼的选择框", false);
		} else {
			map.putCommentDotted("优化.服务端.禁止二次登陆", "新登录的人不能挤掉之前登录的人\n" +
				"正版服严禁开启!");
			noDuplicateLogin = map.putIfAbsent("优化.服务端.禁止二次登陆", false);
		}


		threadPriority = (byte) map.putIfAbsent("调试.主进程优先级(1-9)", -1);

		replaceTransformer = !map.putIfAbsent("调试.禁用上下文ASM", false);

		WTFIsIt = map.putIfAbsent("调试.深 渊 之 子", false);

		map.putCommentDotted("调试.关闭调试日志", "关闭的是forge的调试日志与stdout");
		shrinkLog = map.putIfAbsent("调试.关闭调试日志", true);
		map.putCommentDotted("调试.调试标志位", "位  1: 保存生成的代码\n" +
			"位  2: 记录代码替换\n" +
			"位  4: 保存Nixim结果\n" +
			"位  8: 保存生成的材质\n" +
			"位 16: -\n" +
			"位 32: -\n" +
			"位 64: 游戏内调试功能\n" +
			"位128: 版本兼容(测试版)\n");
		debug = map.putIfAbsent("调试.调试标志位", 0);
		ClassDefiner.debug = (debug & 1) != 0;
		NiximSystem.debug = (debug & 4) != 0;
		clientBrand = map.putIfAbsent("调试.客户端标识", "vanilla");

		tractDirectMem = map.putIfAbsent("调试.追踪直接内存分配", false);
		traceAABB = map.putIfAbsent("调试.追踪碰撞箱创建", false);
		traceBP = map.putIfAbsent("调试.追踪BlockPos创建", false);

		injectLWP = map.putIfAbsent("调试.注入LaunchWrapper", false);

		map.putCommentDotted("调试.细分开启粒度到每个文件", "允许你调整单独一个patch的启用与否\n" +
			"用途：解决兼容性问题");
		controlViaFile = map.putIfAbsent("调试.细分开启粒度到每个文件", false);
		if (controlViaFile) {
			Runtime.getRuntime().addShutdownHook(new Thread(this::save));
		}
		disableAllNixim = map.putIfAbsent("调试.禁用所有Nixim优化", false);


		if (side) {
			map.putCommentDotted("吃掉mod.喜报", "芜湖！这是位掩码！！设置为0关闭");
			xiBao = map.putIfAbsent("吃掉mod.喜报", 0);
			map.putCommentDotted("吃掉mod.FarSight", "忽略服务器的视距限制");
			farSight = map.putIfAbsent("吃掉mod.FarSight", true);
			infMods = map.putIfAbsent("吃掉mod.Infinity MODs", false);
			fastUri = map.putIfAbsent("吃掉mod.FastOpenLinksAndFolders", true);
			betterTooltip = map.putIfAbsent("吃掉mod.手持物品信息", true);
			noRenderUpdateWhenUnload = map.putIfAbsent("吃掉mod.ForgetMeChunk", true);
		}

		map.putCommentDotted("吃掉mod.No Unused Chunks", "设置为0-100代表保存未修改区块的概率");
		chunkSaveChance = map.putIfAbsent("吃掉mod.No Unused Chunks", 100);

		lootR = map.putIfAbsent("吃掉mod.Lootr", false);
		fastLeaveDecay = map.putIfAbsent("吃掉mod.树叶快速凋零", false);
		slabHelper = map.putIfAbsent("吃掉mod.SlabHelper", true);
		CMapping powerShot = map.getOrCreateMap("吃掉mod.PowerShot");
		if (powerShot.putIfAbsent("启用", false)) {
			for (Map.Entry<String, CEntry> entry : powerShot.getOrCreateMap("附魔id到力量").entrySet()) {
				PowershotEvent.enchantmentMultipliers.put(entry.getKey(), entry.getValue().asDouble());
			}
			powerShotRequirement = powerShot.getOrCreateMap("方块状态到力量").entrySet();
		}
		map.putCommentDotted("吃掉mod.FlashFreeze", "删除和添加模组, 而无需担心丢弃方块实体或物品\n" +
			"您可选择启用哪些功能: 将 item, block, entity, tile 中的一些放入这个列表\n" +
			"block和tile选项暂不可用");
		freezeUnknownEntries = map.getOrCreateList("吃掉mod.FlashFreeze").asStringSet();

		map.putCommentDotted("吃掉mod.BlockSwap", "载入世界时，通过id重映射替换方块\n" +
			"格式: 旧资源id: 新资源id\n" +
			"暂不可用");
		idReplacement = (Map<String, String>) map.getOrCreateMap("吃掉mod.BlockSwap").unwrap();
	}
}
