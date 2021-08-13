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

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:20
 */

import ilib.asm.Loader;
import ilib.event.PowershotEvent;
import roj.asm.nixim.NiximTransformer;
import roj.collect.MyHashSet;
import roj.config.JSONConfiguration;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.reflect.*;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class Config extends JSONConfiguration {
    public static boolean reloadSound, replaceOIM, replaceEntityList, isTrashEnable, fastLeaveDecay, mobSpawnFullBlock, noticeItemChange, dumpAnnotationInfo, cacheAnnotation, noRepairCost, enchantOverload,
            jumpAttack, enableMissingItemCreation, fixFont, noShitSound, enableTPSChange, betterSlider, fixMinecart, fastRecipe, shrinkLog,
            noDuplicateLogin, commandEverywhere, logChat, registerItem, autoClimb, noAutoJump, disablePotionShift,
             betterDCA, noSoManyBlockPos, fastLightCheck, fastMethod, lootR, otherWorldChange, cacheBox2, miscPickaxeOptimize,
            showDPS, moreEggs, portalCache, betterRenderGlobal, disableGlobalTESR, enablePinyinSearch, searchNameOnly, entityAabbCache,
            noAdvancement, eventInvoker, removePatchy, slabHelper, eventInvokerMost, packetBufferInfinity, IwantLight, noRecipeBook,
            fixNaNHealth, fixThreadIssues, IwantConnect, noCollision;
    public static int aabbCache, reduceFPSWhenNotActive, clientNetworkTimeout, chatLength, debug, maxParticleCountPerLayer,
            maxChunkTick, entityUpdateFreq, tileUpdateFreq, siFrameTime, autoFlipTooltip;
    public static byte advancedTooltipFlag, threadPriority, subThreadPriority, packetDelay, dynamicViewDistance, changeWorldSpeed;
    public static long maxChunkTimeTick, nbtMaxLength;
    public static String title, clientBrand;
    public static Set<String> disableTileEntities, siTimeExcludeTargets, siTimeExcludeDmgs, siTimeExcludeAttackers;
    public static CMapping setAttributeRange;
    public static float attackCancelThreshold;
    public static Set<Map.Entry<String, CEntry>> powerShotRequirement;

    public static boolean thallium;

    public static final Config instance;

    static {
        instance = new Config();
        if(threadPriority != -1) {
            Thread.currentThread().setPriority(threadPriority);
        }
        if(subThreadPriority != -1) {
            hackSubThreadPriority();
        }
        /**
         * 内置化:
         *  BUG Fix:
         *   鞘翅渲染修复
         *   实体脱离
         *   局域网离线模式
         *   松开时应用修改
         *   NPE: group
         *  优化:
         *   高效Entity/Tile创建
         *  其它:
         *   自定义tab支持
         *   FasterForge
         *
         */
    }

    private static void hackSubThreadPriority() {
        try {
            IFieldAccessor accessor = ReflectionUtils.accessField(Thread.class.getDeclaredField("group"));
            accessor.setInstance(Thread.currentThread());
            ThreadGroup tg = (ThreadGroup) accessor.getObject();
            accessor = ReflectionUtils.accessField(ThreadGroup.class.getDeclaredField("threads"));
            accessor.setInstance(tg);


            IFieldAccessor finalAccessor = accessor;
            final Thread thread1 = new Thread(() -> {
                Thread self = Thread.currentThread();
                Set<Thread> done = new MyHashSet<>();
                while (!self.isInterrupted()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Thread[] arr = (Thread[]) finalAccessor.getObject();
                    for (Thread thread : arr) {
                        if (thread != null)
                        if (done.add(thread)) {
                            thread.setPriority(subThreadPriority);
                        }
                    }
                    for (Iterator<Thread> iterator = done.iterator(); iterator.hasNext(); ) {
                        Thread thread = iterator.next();
                        if (!thread.isAlive())
                            iterator.remove();
                    }
                }
            }, "SubThreadPriorityHack");
            thread1.setPriority(2);
            thread1.setDaemon(true);
            thread1.start();

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }


    private Config() {
        super(new File("config/ImprovementLibrary.json"));
    }

    @Override
    protected void readConfig(CMapping map) {
        map.dotMode(true);

        Loader.logger().debug(
                "    // DEBUG FLAGS\n" +
                        "    // 1 MOD API INFO\n" +
                        "    // 2 CLASS REPLACER\n" +
                        "    // 4 NIXIM\n" +
                        "    // 8 GENERATED MODEL REPOSITORY\n" +
                        "    // 16 JSON PARSER DEBUG\n" +
                        //    "    // 32 DISABLE PRELOAD\n" +
                        //    "    // 64 DUMP ALL ANNOTATIONS\n" +
                        "    // 128 CLASS DEFINE INFO\n" +
                        "    // 256 AABB HIT INFO\n" +
                        "    // 512 INTERESTING COMMENT\n" +
                        "    // 1024 DFA / DCA / DMA DEBUG");

        isTrashEnable = map.putIfAbsent("Optimize.Memory.启用清理垃圾功能", true);
        replaceOIM = map.putIfAbsent("Optimize.Memory.替换ObjectIdentityMap为IntMap(如果你有很多带meta的方块就关闭)", false);
        betterDCA = map.putIfAbsent("Optimize.Memory.替换DCA/DMA/DFA的反射为native方法, 降低内存消耗", true);

        aabbCache = map.putIfAbsent("Optimize.Efficiency.getEntitiesInAABB()的缓存时长(ms), 0关闭", 0);
        cacheAnnotation = map.putIfAbsent("Optimize.Efficiency.缓存mod注解", false);
        noSoManyBlockPos = map.putIfAbsent("Optimize.Efficiency.让World使用MutableBlockPos", false);
        fastMethod = map.putIfAbsent("Optimize.Efficiency.让World再快一点", false);
        fastLightCheck = map.putIfAbsent("Optimize.Efficiency.替换区块中的光照计算，提高效率", false);
        miscPickaxeOptimize = map.putIfAbsent("Optimize.Efficiency.稿子优化", true);
        maxChunkTick = map.putIfAbsent("Optimize.Efficiency.每t最大生成区块数量", 10);
        maxChunkTimeTick = map.putIfAbsent("Optimize.Efficiency.生成区块最大延时(ms)", 25) * 1000000L;
        portalCache = map.putIfAbsent("Optimize.Efficiency.下界传送门缓存", true);
        entityAabbCache = map.putIfAbsent("Optimize.Efficiency.实体AABB缓存", false);
        eventInvoker = map.putIfAbsent("Optimize.Efficiency.事件调用优化", true);
        eventInvokerMost = map.putIfAbsent("Optimize.Efficiency.更激进的事件调用优化", false);
        noCollision = map.putIfAbsent("Optimize.Efficiency.我不需要碰撞！", false);

        otherWorldChange = map.putIfAbsent("Optimize.Misc.对'World'的其他微小优化", false);
        shrinkLog = map.putIfAbsent("Optimize.Misc.减小log文件体积", true);
        fixThreadIssues = map.putIfAbsent("Optimize.Misc.修复多线程问题.启用", true);
        map.put("Optimize.Misc.修复多线程问题.详细", "见https://forum.mechaenetia.com/t/java-lang-nullpoin" +
                "terexception-ticking-memory-connection-at-cpw-mods-fml-common-network-internal-fmlproxypacket/415/9");

        cacheBox2 = map.putIfAbsent("Optimize.Game.修复村庄和世界优化同时开启时爆炸的问题", false);
        fixNaNHealth = map.putIfAbsent("Optimize.Game.修复实体假死(NaN/Inf)的BUG", true);

        // todo make it
        fastRecipe = map.putIfAbsent("Optimize.Game.WIP.替换MC的穷举法提高熔炉/工作台合成判断速度", false);


        commandEverywhere = map.putIfAbsent("Optimize.Client.Game.删除反胃药水效果并使得地狱门里可以输入指令", true);
        betterSlider = map.putIfAbsent("Optimize.Client.Game.滑块只有鼠标松开时才会应用更改", true);
        reloadSound = map.putIfAbsent("Optimize.Client.Game.自动重载声音", true);
        maxParticleCountPerLayer = map.putIfAbsent("Optimize.Client.Game.最大粒子效果数量(还要乘以4-8)", 4096);
        noShitSound = map.putIfAbsent("Optimize.Client.Game.删除洞穴音效", false);

        // 1 - 9
        threadPriority = (byte) map.putIfAbsent("Optimize.Client.Efficiency.Process.主进程优先级(1-9)", -1);
        subThreadPriority = (byte) map.putIfAbsent("Optimize.Client.Efficiency.Process.子进程优先级(1-9)", -1);

        betterRenderGlobal = map.putIfAbsent("Optimize.Client.Efficiency.优化全局渲染器.启用", true);
        tileUpdateFreq = map.putIfAbsent("Optimize.Client.Efficiency.优化全局渲染器.TESR更新间隔(每x帧)", 10);
        disableGlobalTESR = map.putIfAbsent("Optimize.Client.Efficiency.优化全局渲染器.关闭全局TESR", false);
        entityUpdateFreq = map.putIfAbsent("Optimize.Client.Efficiency.优化全局渲染器.实体更新间隔(每x帧)", 10);

        reduceFPSWhenNotActive = map.putIfAbsent("Optimize.Client.Efficiency.不活动n秒时自动降低FPS (0关闭)", 30);
        changeWorldSpeed = (byte) map.putIfAbsent("Optimize.Client.Efficiency.切换世界的速度(0-3)越大越快", 3);

        replaceEntityList = map.putIfAbsent("Optimize.Client.Memory.替换客户端世界的实体列表", true);

        logChat = map.putIfAbsent("Optimize.Client.Misc.在日志中记录聊天", true);
        fixFont = map.putIfAbsent("Optimize.Client.Misc.中文模式的英文字体不好看怎么办", false);

        dynamicViewDistance = (byte) map.putIfAbsent("Optimize.Server.Efficiency.根据TPS自动调节视距(最小值), 0关闭", 0);
        noDuplicateLogin = map.putIfAbsent("Optimize.Server.Game.不允许二次登陆", false);



        debug = map.putIfAbsent("Debug.出现BUG时设为2047, 生成更详细的数据，请带好logs/debug\\.log发送给作者", 0);
        NiximTransformer.debug = (debug & 4) != 0;
        ClassDefiner.debug = (debug & 128) != 0;
        if (debug != 0)
            shrinkLog = false;
        dumpAnnotationInfo = (debug & 64) != 0;
        DirectAccessor.DEBUG = (debug & 1024) != 0;



        boolean advancedTooltip_Reg = map.putIfAbsent("Util.Client.高级提示框.注册名", true);
        boolean advancedTooltip_Loc = map.putIfAbsent("Util.Client.高级提示框.未本地化名", false);
        boolean advancedTooltip_Food = map.putIfAbsent("Util.Client.高级提示框.食物", false);
        advancedTooltipFlag = (byte) ((advancedTooltip_Food ? 1 : 0) << 2 | (advancedTooltip_Loc ? 1 : 0) << 1 | (advancedTooltip_Reg ? 1 : 0));

        autoFlipTooltip = map.putIfAbsent("Util.Client.Tooltip分页长度", 16);

        noticeItemChange = map.putIfAbsent("Util.Client.提示背包物品改变", false);

        enableTPSChange = map.putIfAbsent("Util.改变Tick速度", false);
        enableMissingItemCreation = map.putIfAbsent("Util.为没有物品的方块创建物品", false);
        packetDelay = (byte) map.putIfAbsent("Util.网络数据包系统Packet计时器", 3);
        enablePinyinSearch = map.putIfAbsent("Util.拼音搜索!!! 没错，JECH可以删了", true);
        searchNameOnly = map.putIfAbsent("Util.只搜索物品名字", false);


        CList l = new CList();

        moreEggs = map.putIfAbsent("Tweak.来点蛋吧", true);
        mobSpawnFullBlock = map.putIfAbsent("Tweak.怪物只能在完整方块上生成", false);
        fixMinecart = map.putIfAbsent("Tweak.只有玩家可以开着矿车到处跑", false);
        jumpAttack = map.putIfAbsent("Tweak.从高处落下会具有更多的攻击伤害", false);
        registerItem = map.putIfAbsent("Tweak.注册ImpLib自身的物品", true);
        disableTileEntities = map.putIfAbsent("Tweak.禁用的方块实体列表", l).asList().asStringSet();
        noAdvancement = map.putIfAbsent("Tweak.禁用进度系统", false);
        setAttributeRange = map.getOrCreateMap("Tweak.属性调节");
        noRecipeBook = map.putIfAbsent("Tweak.不再保存合成书的NBT", false);

        siFrameTime = map.putIfAbsent("Tweak.无敌帧.新值(-1关闭)", -1);
        siTimeExcludeAttackers = map.putIfAbsent("Tweak.无敌帧.攻击者黑名单", CList.of("minecraft:slime", "tconstruct:blueslime", "thaumcraft:thaumslime")).asList().asStringSet();
        siTimeExcludeDmgs = map.putIfAbsent("Tweak.无敌帧.伤害源黑名单", CList.of("inFire", "lava", "cactus", "lightningBolt", "inWall", "hotFloor")).asList().asStringSet();
        siTimeExcludeTargets = map.putIfAbsent("Tweak.无敌帧.攻击目标黑名单", l).asList().asStringSet();

        attackCancelThreshold = (float) map.putIfAbsent("Tweak.攻击最低CD(百分比0-1)", -1d);

        noRepairCost = map.putIfAbsent("Tweak.Anvil.无限修复", false);
        enchantOverload = map.putIfAbsent("Tweak.Anvil.允许合成超过最大等级的附魔书", false);

        nbtMaxLength = (long)map.putIfAbsent("Tweak,Client.NBT数据包最大长度(KB)", 2048) << 10;
        title = map.putIfAbsent("Tweak.Client.窗口标题", "Minecraft 1.12.2");
        clientNetworkTimeout = map.putIfAbsent("Tweak.Client.客户端连接服务器的网络超时", 30);
        autoClimb = map.putIfAbsent("Tweak.Client.自动爬楼梯", false);
        chatLength = map.putIfAbsent("Tweak.Client.聊天记录最大长度", 100);
        noAutoJump = map.putIfAbsent("Tweak.Client.禁用自动跳跃", false);
        showDPS = map.putIfAbsent("Tweak.Client.显示武器DPS", true);
        disablePotionShift = map.putIfAbsent("Tweak.Client.显示背包时不会被药水效果带偏", true);
        clientBrand = map.putIfAbsent("Tweak.Client.客户端标识(原版：vanilla)", "disable");
        removePatchy = map.putIfAbsent("Tweak.Client.修复进存档或服务器卡死(只需打开一次)", false);
        IwantConnect = map.putIfAbsent("Tweak.Client.我要联机!", false);



        thallium = map.putIfAbsent("Mod-Porting.WIP.Thallium(FPS Improve)", false);
        lootR = map.putIfAbsent("Mod-Porting.Lootr(独立战利品箱)", false);
        fastLeaveDecay = map.putIfAbsent("Mod-Porting.树叶快速凋零", false);
        slabHelper = map.putIfAbsent("Mod-Porting.SlabHelper", true);
        CMapping powerShot = map.getOrCreateMap("Mod-Porting.PowerShot");
        if(powerShot.putIfAbsent("启用", false)) {
            for (Map.Entry<String, CEntry> entry : powerShot.getOrCreateMap("附魔namespace到power等级").entrySet()) {
                PowershotEvent.enchantmentMultipliers.put(entry.getKey(), entry.getValue().asDouble());
            }
            powerShotRequirement = powerShot.getOrCreateMap("方块IBlockState Id到power等级").entrySet();
        }

        packetBufferInfinity = map.putIfAbsent("Misc.PacketBuffer解除所有限制!", false);
        //IwantLight = map.computeIfAbsent("Misc.替换原版光照系统", false);
        //IwantLight = map.computeIfAbsent("Misc.重写WIP区块", false);

        if (betterDCA) {
            try {
                SunReflection.doSunReflectCache();
            } catch (Throwable e) {
                Loader.logger().warn("无法替换DMA的native!", e);
            }
        }
    }
}
