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

import ilib.event.PowershotEvent;
import roj.asm.nixim.NiximSystem;
import roj.collect.MyHashSet;
import roj.config.JSONConfiguration;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.reflect.ClassDefiner;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/5/31 21:20
 */
public final class Config extends JSONConfiguration {
    public static boolean reloadSound, replaceOIM, replaceEntityList, isTrashEnable, fastLeaveDecay, mobSpawnFullBlock,
        noticeItemChange, cacheAnnotation, noRepairCost, enchantOverload, fastDismount,
        jumpAttack, enableMissingItemCreation, fixFont, noShitSound, enableTPSChange, fixMinecart, fastRecipe, shrinkLog,
        noDuplicateLogin, commandEverywhere, logChat, registerItem, autoClimb, disablePotionShift, showySelectBox,
        noSoManyBlockPos, fastLightCheck, fastMethod, lootR, otherWorldChange, miscPickaxeOptimize, modelCache,
        showDPS, moreEggs, portalCache, betterRenderGlobal, disableGlobalTESR, enablePinyinSearch, searchNameOnly,
        entityAabbCache, noAdvancement, eventInvoker, removePatchy, slabHelper, eventInvokerMost, packetBufferInfinity,
        IwantLight, noRecipeBook, fixNaNHealth, fixThreadIssues, noCollision, noEnchantTax, noAnvilTax, betterF3,
        injectLWP, clearLava, F3_ins, F3_render, F3_hard, safer, fastFont, noGhostBlock, checkStackDepth,
            betterKeyboard;

    public static int fpsLowTime, fpsLowFPS, clientNetworkTimeout, chatLength, debug, maxParticleCountPerLayer,
        maxChunkTick, entityUpdateFreq, tileUpdateFreq, siFrameTime, pagedTooltipLen, tooltipFlag, pagedTooltipTime;

    public static byte threadPriority, dynamicViewDistance, changeWorldSpeed;

    public static long maxChunkTimeTick, nbtMaxLength;

    public static String title, clientBrand;

    public static Set<String> disableTileEntities, siTimeExcludeTargets, siTimeExcludeDmgs, siTimeExcludeAttackers, noUpdate;

    public static CMapping setAttributeRange;

    public static float attackCD, fpsLowVol;

    public static Set<Map.Entry<String, CEntry>> powerShotRequirement;

    public static MyHashSet<String> freezeUnknownEntries;

    public static boolean networkOpt1, networkOpt2, resetCompressor = true;
    public static int compressionLevel = -1, maxCompressionPacketSize = 2097152;

    public static final Config instance;

    public static int sizeEvent;
    @Deprecated
    public static byte packetDelay;

    static {
        instance = new Config();
        if(threadPriority != -1) {
            Thread.currentThread().setPriority(threadPriority);
        }
    }


    private Config() {
        super(new File("config/ImprovementLibrary.json"));
    }

    @Override
    protected void readConfig(CMapping map) {
        map.dot(true);

        // region 优化

        // region 默认开启
        map.putCommentDotted("优化.清理一些垃圾", "清除LaunchClassLoader和mixin(如果安装)的包缓存, 锁定AccessTransformer的项");
        isTrashEnable = map.putIfAbsent("优化.清理一些垃圾", true);
        miscPickaxeOptimize = map.putIfAbsent("优化.稿子优化", true);
        map.putCommentDotted("优化.下界传送门缓存", "差不多就是那个玩意");
        portalCache = map.putIfAbsent("优化.下界传送门缓存", true);
        eventInvoker = map.putIfAbsent("优化.事件调用优化", true);
        noCollision = map.putIfAbsent("优化.我不需要碰撞！", false);
        map.putCommentDotted("优化.修复多线程问题", "可能会使得进入世界卡死");
        fixThreadIssues = map.putIfAbsent("优化.修复多线程问题", true);
        fixNaNHealth = map.putIfAbsent("优化.修复实体假死(NaN/Inf)的BUG", true);
        fastRecipe = map.putIfAbsent("优化.替换MC的穷举法提高熔炉/工作台合成判断速度", true);
        fastMethod = map.putIfAbsent("优化.优化碰撞体积获取", true);

        maxChunkTick = map.putIfAbsent("优化.区块.每t最大生成", 10);
        maxChunkTimeTick = map.putIfAbsent("优化.区块.每t最大耗时(ms)", 25) * 1000000L;
        // endregion
        // region 默认关闭
        map.putCommentDotted("优化.实体AABB缓存", "Surge已有同类功能");
        entityAabbCache = map.putIfAbsent("优化.实体AABB缓存", false);
        map.putCommentDotted("优化.缓存mod注解", "forge会在启动时扫描每个mod的jar文件并读取注解\n" +
            "IL已经做了优化, 不只是这里\n" +
            "不超过50个mod没必要打开");
        cacheAnnotation = map.putIfAbsent("优化.缓存mod注解", false);
        map.putCommentDotted("优化.快速的脱离", "可能造成一些基于矿车/船的特性构造的设备失效");
        fastDismount = map.putIfAbsent("优化.快速的脱离", false);
        map.putCommentDotted("优化.方块更新优化", "优化方块更新有关代码，使之使用PooledMutableBlocKPos\n" +
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
        map.putCommentDotted("优化.优化随机刻", "其实没啥用，除非你randomTickSpeed开到几千");
        otherWorldChange = map.putIfAbsent("优化.优化随机刻", false);
        // endregion

        map.putCommentDotted("优化.测试", "这些优化可能存在较严重的bug");

        modelCache = map.putIfAbsent("优化.测试.模型缓存", false);
        eventInvokerMost = map.putIfAbsent("优化.测试.激进事件调用优化", false);
        fastLightCheck = map.putIfAbsent("优化.测试.替换区块中的光照计算，提高效率", false);


        networkOpt1 = map.putIfAbsent("优化.测试.网络优化.A部分", false);
        networkOpt2 = map.putIfAbsent("优化.测试.网络优化.B部分", false);
        compressionLevel = map.putIfAbsent("优化.测试.网络优化.zip压缩等级", -1);
        maxCompressionPacketSize = map.putIfAbsent("优化.测试.网络优化.最大压缩数据包大小", 2097152);

        betterRenderGlobal = map.putIfAbsent("优化.测试.优化全局渲染.启用", false);
        tileUpdateFreq = map.putIfAbsent("优化.测试.优化全局渲染.TESR更新间隔(每x帧)", 10);
        disableGlobalTESR = map.putIfAbsent("优化.测试.优化全局渲染.关闭全局TESR", false);
        entityUpdateFreq = map.putIfAbsent("优化.测试.优化全局渲染.实体更新间隔(每x帧)", 10);



        map.putCommentDotted("优化.弃用", "这些优化可能没有太大的效果,计划在未来删除");

        map.putCommentDotted("优化.弃用.替换ObjectIdentityMap", "替换为IntMap, 如果数组中有大量空位这不失为一个好选择(如果你有很多带meta的方块就关闭)\n" +
            "在MoreId的更新计划中, 我们计划引入类似1.13的机制,至此meta与方块id再无关联, 不过这可能会导致严重的兼容性问题");
        replaceOIM = map.putIfAbsent("优化.弃用.替换ObjectIdentityMap", false);

        // region 客户端Only
        map.putCommentDotted("优化.客户端.快速字体渲染", "可能存在兼容性问题");
        fastFont = map.putIfAbsent("优化.客户端.快速字体渲染", true);
        map.putCommentDotted("优化.客户端.自动修复无声音问题", "如果还是没用, 请在任意世界内输入//reloadSoundMgr指令");
        reloadSound = map.putIfAbsent("优化.客户端.自动修复无声音问题", true);
        map.putCommentDotted("优化.客户端.最大粒子效果数量", "实际上这是每个粒子层的数量,而一共有4-8个层");
        maxParticleCountPerLayer = map.putIfAbsent("优化.客户端.最大粒子效果数量", 4096);

        map.putCommentDotted("优化.客户端.自动降低FPS.时间", "单位秒, 0关闭");
        fpsLowTime = map.putIfAbsent("优化.客户端.自动降低FPS.时间 ", 30);
        fpsLowFPS = map.putIfAbsent("优化.客户端.自动降低FPS.目标帧率", 10);
        map.putCommentDotted("优化.客户端.自动降低FPS.目标音量", "范围0-1");
        fpsLowVol = (float) map.putIfAbsent("优化.客户端.自动降低FPS.目标音量", 0.2f);

        map.putCommentDotted("优化.客户端.提高切换世界速度", "1: 不显示[下载地形中]\n" +
            "2: 禁用切换世界时检测碰撞\n" +
            "3: 禁止切换世界时GC");
        changeWorldSpeed = (byte) map.putIfAbsent("优化.客户端.提高切换世界速度", 3);

        map.putCommentDotted("优化.客户端.替换实体列表", "使用WeakSet替换原先的ArrayList, 有效降低长时间游玩的内存占用");
        replaceEntityList = map.putIfAbsent("优化.客户端.替换实体列表", true);
        // endregion
        // region 服务端Only
        map.putCommentDotted("优化.服务端.根据TPS自动调节视距", "这表示最小值, 0关闭");
        dynamicViewDistance = (byte) map.putIfAbsent("优化.服务端.根据TPS自动调节视距", 0);
        // endregion

        // endregion
        // region 功能
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
            "0: 完全删除攻击CD (不修改物品NBT)" +
            "-1: 不使用此功能");
        attackCD = (float) map.putIfAbsent("功能.攻击CD百分比", -1f);
        noEnchantTax = map.putIfAbsent("功能.附魔不掉等级，而是经验", false);
        noAnvilTax = map.putIfAbsent("功能.铁X.不掉等级，而是经验", false);
        map.putCommentDotted("功能.铁X.删除RepairCost", "允许无限的修复一件物品");
        noRepairCost = map.putIfAbsent("功能.铁X.删除RepairCost", false);
        enchantOverload = map.putIfAbsent("功能.铁X.允许合成超过最大等级的附魔书", false);

        packetBufferInfinity = map.putIfAbsent("功能.删除数据包的数据长度限制", false);

        map.putCommentDotted("功能.修改无敌帧", "长度的单位是tick, -1不修改");
        siFrameTime = map.putIfAbsent("功能.修改无敌帧.长度", -1);
        siTimeExcludeAttackers = map.putIfAbsent("功能.修改无敌帧.攻击者黑名单", CList.of("minecraft:slime", "tconstruct:blueslime", "thaumcraft:thaumslime")).asList().asStringSet();
        siTimeExcludeDmgs = map.putIfAbsent("功能.修改无敌帧.伤害源黑名单", CList.of("inFire", "lava", "cactus", "lightningBolt", "inWall", "hotFloor")).asList().asStringSet();
        siTimeExcludeTargets = map.getOrCreateList("功能.修改无敌帧.攻击目标黑名单").asStringSet();

        setAttributeRange = map.getOrCreateMap("功能.调节属性范围");

        map.putCommentDotted("功能.安全管理", "替换安全管理器以防止mod启动进程，监听端口，或者加载native\n" +
            "当然，如果它像ImpLib一样不讲武德那实际上有多少用就不好说了");
        safer = map.putIfAbsent("功能.安全管理.启用", true);
        if (map.putIfAbsent("功能.安全管理.禁止联网", false)) {
            noUpdate = map.getOrCreateList("功能.安全管理.禁止联网白名单").asStringSet();
        }

        noGhostBlock = map.putIfAbsent("功能.防止幽灵方块出现", true);


        commandEverywhere = map.putIfAbsent("功能.客户端.删除反胃效果", true);
        noShitSound = map.putIfAbsent("功能.客户端.删除洞穴音效", true);

        map.putCommentDotted("功能.客户端.在日志中记录聊天", "是的, 你可以关闭它");
        logChat = map.putIfAbsent("功能.客户端.在日志中记录聊天", true);
        map.putCommentDotted("功能.客户端.聊天记录最大长度", "这是指客户端UI中的历史记录");
        chatLength = map.putIfAbsent("功能.客户端.聊天记录最大长度", 100);

        map.putCommentDotted("功能.客户端.中文模式开启英文字宽", "选择英文, 你会发现这时的英文字符都会更宽\n" +
            "开启以把它带入任何语言");
        fixFont = map.putIfAbsent("功能.客户端.中文模式开启英文字宽", false);

        map.putCommentDotted("功能.客户端.重复键盘事件", "难道你不觉得，打字时长按无效很烦么\n" +
                "可能造成以下问题: 长按E打开背包，它又没了（你会长按么）");
        betterKeyboard = map.putIfAbsent("功能.客户端.重复键盘事件", true);

        betterF3 = map.putIfAbsent("功能.客户端.更好的F3.启用", true);
        F3_ins = map.putIfAbsent("功能.客户端.更好的F3.本地服务端信息", false);
        F3_render = map.putIfAbsent("功能.客户端.更好的F3.渲染信息", true);
        F3_hard = map.putIfAbsent("功能.客户端.更好的F3.硬件信息", true);

        map.putCommentDotted("功能.客户端.清澈的岩浆", "如果要实现'I See Lava' mod的全部功能，你需要用材质包把岩浆的材质替换成半透明的");
        clearLava = map.putIfAbsent("功能.客户端.清澈的岩浆", false);

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

        pagedTooltipLen = map.putIfAbsent("功能.客户端.Tooltip分页.长度", 16);
        pagedTooltipTime = map.putIfAbsent("功能.客户端.Tooltip分页.切换时间(帧)", 60);
        noticeItemChange = map.putIfAbsent("功能.客户端.提示物品改变", false);

        map.putCommentDotted("功能.客户端.NBT最大长度", "单位KB, 防止因为过长的NBT无法进入服务器");
        nbtMaxLength = (long)map.putIfAbsent("功能.客户端.NBT最大长度", 2048) << 10;
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


        map.putCommentDotted("优化.服务端.禁止二次登陆", "新登录的人不能挤掉之前登录的人\n" +
            "正版服严禁开启!");
        noDuplicateLogin = map.putIfAbsent("优化.服务端.禁止二次登陆", false);


        threadPriority = (byte) map.putIfAbsent("调试.主进程优先级(1-9)", -1);

        map.putCommentDotted("调试.关闭调试日志", "关闭的是forge的调试日志与stdout");
        shrinkLog = map.putIfAbsent("调试.关闭调试日志", true);
        map.putCommentDotted("调试.调试标志位", "可选生成更详细的数据, 出现bug时带好logs/debug\\.log发送给作者\n" +
            "位  1: 输出通过ClassDefiner定义的类\n" +
            "位  2: 记录ClassReplacer的替换\n" +
            "位  4: 将Nixim处理器的输入与输出写入zip文件\n" +
            "位  8: 记录GeneratedModelRepo的材质注册\n" +
            "位 16: 调试事件优化\n" +
            "位 32: 记录AABB缓存\n" +
            "位 64: 保留\n" +
            "位128: 保留\n");
        debug = map.putIfAbsent("调试.调试标志位", 0);
        ClassDefiner.debug = (debug & 1) != 0;
        NiximSystem.debug = (debug & 4) != 0;
        clientBrand = map.putIfAbsent("调试.客户端标识", "vanilla");




        map.putCommentDotted("内部使用", "它们没啥用，并且可能是为以后的新优化收集素材");
        injectLWP = map.putIfAbsent("内部使用.注入LaunchWrapper.开启", false);
        map.getOrCreateList("内部使用.注入LaunchWrapper.附加不转换前缀");

        enablePinyinSearch = map.putIfAbsent("内部使用.拼音搜索!!! 没错，JECH可以删了", true);
        searchNameOnly = map.putIfAbsent("内部使用.只搜索物品名字", false);





        lootR = map.putIfAbsent("吃掉mod.Lootr", false);
        fastLeaveDecay = map.putIfAbsent("吃掉mod.树叶快速凋零", false);
        slabHelper = map.putIfAbsent("吃掉mod.SlabHelper", true);
        CMapping powerShot = map.getOrCreateMap("吃掉mod.PowerShot");
        if(powerShot.putIfAbsent("启用", false)) {
            for (Map.Entry<String, CEntry> entry : powerShot.getOrCreateMap("附魔id到力量").entrySet()) {
                PowershotEvent.enchantmentMultipliers.put(entry.getKey(), entry.getValue().asDouble());
            }
            powerShotRequirement = powerShot.getOrCreateMap("方块状态到力量").entrySet();
        }
        map.putCommentDotted("吃掉mod.FlashFreeze", "删除和添加模组, 而无需担心丢弃方块实体或物品\n" +
            "您可选择启用哪些功能: 将 item, block, entity, tile 中的一些放入这个列表");
        freezeUnknownEntries = map.getOrCreateList("吃掉mod.FlashFreeze").asStringSet();
    }
}
