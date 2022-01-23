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
package ilib.event;

import com.google.common.collect.BiMap;
import com.google.common.collect.Multimap;
import ilib.ClientProxy;
import ilib.Config;
import ilib.ImpLib;
import ilib.api.item.ICustomTooltip;
import ilib.capabilities.Capabilities;
import ilib.capabilities.EntitySize;
import ilib.client.AutoFPS;
import ilib.client.api.CustomRTResult;
import ilib.client.util.KeyHelper;
import ilib.client.util.MyDebugOverlay;
import ilib.util.NBTType;
import ilib.util.PlayerUtil;
import ilib.util.Reflection;
import ilib.util.TextHelper;
import ilib.world.saver.WorldSaver;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.reflect.ReflectionUtils;
import roj.text.Placeholder;
import roj.text.SimpleLineReader;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.WorldProvider;

import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Text;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static ilib.ClientProxy.mc;

/**
 * @author Roj234
 * @since 2021/5/30 0:25
 */
public final class ClientEvent {
    private static final MyHashSet<ResourceLocation> ADDITIONAL_TEXTURES = new MyHashSet<>();
    private static List<String> splashes;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ClientEvent.class);

        if (Config.reduceFPSWhenNotActive > 0) {
            AutoFPS.init(Config.reduceFPSWhenNotActive);
        }
        try {
            splashes = SimpleLineReader.slrParserV2(IOUtil.readUTF("META-INF/splashes.txt"), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static SoundManager cachedManager = null;

    public static boolean reloadSoundMgr(boolean force) {
        if (cachedManager == null) {
            SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
            try {
                Field fi = SoundHandler.class.getDeclaredField("field_147694_f");
                fi.setAccessible(true);
                cachedManager = (SoundManager) fi.get(handler);
            } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        try {
            Field fi = SoundManager.class.getDeclaredField("field_148617_f");
            fi.setAccessible(true);
            if (!fi.getBoolean(cachedManager) || force) {
                ImpLib.logger().info("Reloading sound system...");
                cachedManager.reloadSoundSystem();
                return true;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    static boolean lastPlaying;
    private static void doAutoClick() {
        final Minecraft mc = ClientProxy.mc;

        boolean playing = mc.currentScreen == null && mc.inGameHasFocus;

        if(playing != lastPlaying) {
            lastPlaying = playing;

            if(!playing) {
                mc.playerController.resetBlockRemoving();
                return;
            }
        }

        final RayTraceResult over = mc.objectMouseOver;
        if (over != null && !mc.player.isRowingBoat()) {
            final int cl = ClientEvent.autoClick;
            switch (over.typeOfHit) {
                case ENTITY:
                    if((cl & 1) != 0)
                        mc.playerController.attackEntity(mc.player, over.entityHit);
                break;
                case BLOCK: {
                    final BlockPos pos = over.getBlockPos();
                    if (!mc.world.isAirBlock(pos)) {
                        if((cl & 2) != 0)
                            mc.playerController.clickBlock(pos, over.sideHit);
                        else if((cl & 4) != 0 && mc.playerController.onPlayerDamageBlock(pos, over.sideHit)) {
                                mc.effectRenderer.addBlockHitEffects(pos, over);
                        }
                    }
                }
                break;
                case MISS:
                    mc.player.resetCooldown();
                    if((cl & 8) != 0)
                        ForgeHooks.onEmptyLeftClick(mc.player);
                break;
            }
            mc.player.swingArm(EnumHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void onRenderF3(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == ElementType.DEBUG && Config.betterF3)
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderDebugOverlay(Text event) {
        if (Config.betterF3) {
            MyDebugOverlay.process(event.getLeft(), event.getRight());
        }
    }

    @SubscribeEvent
    public static void onOpenGui(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (gui instanceof GuiDownloadTerrain) {
            if (Config.changeWorldSpeed > 0) {
                event.setCanceled(true);
            }
        } else if (gui instanceof GuiMultiplayer) {
            WorldSaver.plusSid();
        } else if (gui instanceof GuiMainMenu) {
            if (splashes != null) {
                String splash = splashes.get(Math.abs((int) System.nanoTime()) % splashes.size());
                splash = Placeholder.assign('{', '}', splash)
                                    .replace(Collections.singletonMap("$USERNAME", mc.getSession().getUsername()));
                Reflection.HELPER.setMainMenuSplash((GuiMainMenu) event.getGui(), splash);
            }
        }
    }

    @SubscribeEvent
    public static void onPotionShift(GuiScreenEvent.PotionShiftEvent event) {
        if (Config.disablePotionShift) {
            event.setCanceled(true);
        }
    }

    static int autoClick;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        EntityPlayer player = ClientProxy.mc.player;
        if (player == null) return;
        if (event.phase == TickEvent.Phase.START) {
            if (Config.autoClimb) {
                if (player.isOnLadder() && !player.isSneaking() && player.rotationPitch < -45) {
                    player.motionY = 0.1f;
                    player.velocityChanged = true;
                }
            }
            if (Config.noAutoJump) {
                player.stepHeight = 0.6f;
            }

            if (autoClick != 0) {
                doAutoClick();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerSpirit(TextureStitchEvent.Pre event) {
        TextureMap map = event.getMap();
        try {
            BiMap<String, Fluid> masterFluidReference = ReflectionUtils.getValue(null, FluidRegistry.class, "masterFluidReference");
            for (Fluid fluid : masterFluidReference.values()) {
                map.registerSprite(fluid.getStill());
                map.registerSprite(fluid.getFlowing());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        for (ResourceLocation loc : ADDITIONAL_TEXTURES)
            map.registerSprite(loc);
    }

    @SubscribeEvent
    public static void onItemDrop(ItemTossEvent event) {
        if (Config.noticeItemChange && event.getEntityItem().world.isRemote) {
            ItemStack stack = event.getEntityItem().getItem();
            PlayerUtil.sendTo(event.getPlayer(), "[\u00a7c-\u00a7r] " + stack.getDisplayName() + " \u00a7c*\u00a7r " + stack.getCount());
        }
    }

    @SubscribeEvent
    public static void onItemDrop(EntityItemPickupEvent event) {
        if (Config.noticeItemChange && event.getEntity().world.isRemote) {
            ItemStack stack = event.getItem().getItem();
            PlayerUtil.sendTo(event.getEntityPlayer(), "[\u00a7a+\u00a7r] " + stack.getDisplayName() + " \u00a7c*\u00a7r " + stack.getCount());
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        List<String> list = event.getToolTip();

        final EntityPlayer player = event.getEntityPlayer();

        if (item instanceof ICustomTooltip) {
            ((ICustomTooltip) item).handleTooltipEvent(list, stack, player);
        }

        final int f = Config.tooltipFlag;
        if (f != 0 && !KeyHelper.isShiftPressed()) {
            if ((f & 1) != 0) {
                list.add("\u00a7a" + I18n.format("tooltip.ilib.debug.registry"));
                list.add("\u00a77 - " + item.getRegistryName());
            }
            if ((f & 2) != 0) {
                list.add("\u00a7a" + I18n.format("tooltip.ilib.debug.unlocalized"));
                list.add("\u00a77 - " + item.getTranslationKey(stack) + ".name");
            }
            if ((f & 4) != 0) {
                int[] oreIds = OreDictionary.getOreIDs(stack);
                if (oreIds.length > 0) {
                    list.add("\u00a7a" + I18n.format("tooltip.ilib.debug.oredict"));
                    for (int i : oreIds) {
                        list.add("\u00a77 - " + OreDictionary.getOreName(i));
                    }
                }
            }
            if ((f & 8) != 0 && stack.hasTagCompound()) {
                if (!KeyHelper.isCtrlPressed()) {
                    list.add("\u00a7aNBT (Ctrl)");
                } else {
                    list.add("\u00a7aNBT");
                    list.add("\u00a77 - " + NBTType.betterRender(stack.getTagCompound()));
                }
            }
            if ((f & 16) != 0 && item instanceof ItemFood) {
                list.add("\u00a7a" + I18n.format("tooltip.ilib.debug.food"));
                ItemFood food = (ItemFood) item;
                list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.food.meat") + TextHelper.translate(food.isWolfsFavoriteMeat()));
                list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.food.sat") + food.getSaturationModifier(stack));
                list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.food.heal") + food.getHealAmount(stack));
            }
            if ((f & 4064) != 0 && item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).getBlock();
                if (block instanceof IFluidBlock) {
                    Fluid fluid = ((IFluidBlock) block).getFluid();
                    list.add("\u00a7a" + I18n.format("tooltip.ilib.debug.fluid"));
                    if ((f & 32) != 0) {
                        list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.fluid.viscosity") + fluid.getViscosity());
                    }
                    if ((f & 64) != 0) {
                        list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.fluid.luminosity") + fluid.getLuminosity());
                    }
                    if ((f & 128) != 0) {
                        list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.fluid.temperature") + fluid.getTemperature());
                    }
                    if ((f & 256) != 0) {
                        list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.fluid.color") + Integer.toHexString(fluid.getColor()));
                    }
                    if ((f & 512) != 0) {
                        list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.fluid.density") + fluid.getDensity());
                    }
                    if ((f & 1024) != 0) {
                        list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.fluid.gas") + fluid.isGaseous());
                    }
                    if ((f & 2048) != 0) {
                        list.add("\u00a77 - " + I18n.format("tooltip.ilib.debug.fluid.place") + fluid.canBePlacedInWorld());
                    }
                }
            }
        }

        if (Config.showDPS && player != null) {
            Multimap<String, AttributeModifier> map = event.getItemStack().getAttributeModifiers(EntityEquipmentSlot.MAINHAND);
            Collection<AttributeModifier> damage = map.get(SharedMonsterAttributes.ATTACK_DAMAGE.getName());
            if (damage.size() == 1) {
                Collection<AttributeModifier> speed = map.get(SharedMonsterAttributes.ATTACK_SPEED.getName());
                if (speed.size() == 1) {
                    int i = list.indexOf(TextHelper.translate("item.modifiers." + EntityEquipmentSlot.MAINHAND.getName()));
                    if (i == -1) {
                        return;
                    }

                    do {
                        i++;
                    } while (i < list.size() && list.get(i).startsWith(" "));

                    list.add(i, " DPS: \u00a7c" + ItemStack.DECIMALFORMAT.format((player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue() + (damage
                            .iterator().next()).getAmount()) * (player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED)
                            .getBaseValue() + speed.iterator().next().getAmount())));
                }
            }
        }
    }

    static WeakReference<ItemStack> EMPTY = new WeakReference<>(null);
    static WeakReference<ItemStack> lastRender = EMPTY;
    static String[] backup;
    static int timer;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void lastTooltip(ItemTooltipEvent event) {
        int rows = Config.autoFlipTooltip;
        if (rows <= 0) {
            return;
        }

        List<String> list = event.getToolTip();

        if (list.size() <= rows) {
            timer = 0;
            lastRender = EMPTY;
            return;
        }

        ItemStack stack = event.getItemStack();
        if (lastRender.get() == null || !stack.isItemEqual(lastRender.get())) {
            lastRender = new WeakReference<>(stack);
            timer = 0;
        }

        int pages = (int) Math.ceil(((double) list.size()) / rows);

        int v = timer++ / 100;
        if (v >= pages)
            v = timer = 0;

        int end = Math.min(list.size(), (v + 1) * rows);

        String[] backup = ClientEvent.backup;
        if(backup == null)
            ClientEvent.backup = backup = new String[rows];
        int j = 0;

        for (int i = v * rows; i < end; i++) {
            backup[j++] = list.get(i);
        }
        list.clear();

        for (int i = 0; i < j; i++) {
            list.add(backup[i]);
        }
        list.add(String.valueOf(v + 1) + '/' + pages + " 页 (" + (100 - timer % 100) / 20 + ")");
    }

    static boolean notice;
    private static void notice() {
        if(!notice) {
            PlayerUtil.sendTo(null, "//auto系列指令: [autoClick/autoDig 互斥] autoClickAir autoKill autoClear 自动点击方块 挖方块 点击空气 攻击 停止\n指令用两次切换, autoClick/Dig除外");
            notice = true;
        }
    }

    @SubscribeEvent
    public static void reloadSndManager(ClientChatEvent event) {
        final String msg = event.getOriginalMessage();
        if(!msg.startsWith("//"))
            return;
        switch (msg) {
            case "//reloadSoundMgr":
                PlayerUtil.sendTo(null, reloadSoundMgr(true) ? "重载完毕" : "没有重载, 可能出错了或已经加载...");
            break;
            case "//worldSaver":
                PlayerUtil.sendTo(null, (WorldSaver.toggleEnable()) ? "下次进入服务器时生效" : "已关闭");
            break;
            case "//autoDig":
                autoClick |= 4;
                autoClick &= ~2;
                notice();
                break;
            case "//autoClick":
                autoClick |= 2;
                autoClick &= ~4;
                notice();
            break;
            case "//autoClickAir":
                autoClick ^= 8;
                notice();
            break;
            case "//autoKill":
                autoClick ^= 1;
                notice();
            break;
            case "//autoClear":
                autoClick = 0;
            break;
            default:
                return;
        }
        event.setCanceled(true);
    }

    // 替换其他世界天空渲染
    public static final IntMap<IRenderHandler> REPLACE_SKY_RENDERS = new IntMap<>(4);
    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load e) {
        WorldProvider pv = e.getWorld().provider;
        if (pv != null && e.getWorld().isRemote/* && pv.getDimension() == theDimensionId*/) {
            IRenderHandler theRenderer = REPLACE_SKY_RENDERS.get(pv.getDimension());
            if (theRenderer != null)
                pv.setSkyRenderer(theRenderer);
        }
    }

    @SubscribeEvent
    public static void onEntityRenderPre(RenderLivingEvent.Pre<?> event) {
        EntitySize cap = event.getEntity().getCapability(Capabilities.RENDERING_SIZE, null);
        if (cap != null) {
            float scale = cap.getScale();

            GlStateManager.pushMatrix();

            GlStateManager.scale(scale, scale, scale);
            GlStateManager.translate(event.getX() / scale - event.getX(), event.getY() / scale - event.getY(), event.getZ() / scale - event.getZ());
        }
    }

    @SubscribeEvent
    public static void onEntityRenderPost(RenderLivingEvent.Post<?> event) {
        if (event.getEntity().hasCapability(Capabilities.RENDERING_SIZE, null)) {
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public static void drawSelectionBox(DrawBlockHighlightEvent event) {
        if (event.getSubID() == 0 && event.getTarget().typeOfHit == RayTraceResult.Type.BLOCK) {
            if (!(event.getTarget() instanceof CustomRTResult)) return;
            event.setCanceled(true);
            CustomRTResult hit = (CustomRTResult) event.getTarget();

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.glLineWidth(2);
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);

            EntityPlayer player = event.getPlayer();
            double x = MathUtils.interpolate(player.lastTickPosX, player.posX, event.getPartialTicks());
            double y = MathUtils.interpolate(player.lastTickPosY, player.posY, event.getPartialTicks());
            double z = MathUtils.interpolate(player.lastTickPosZ, player.posZ, event.getPartialTicks());
            RenderGlobal.drawSelectionBoundingBox(hit.box.offset(-x, -y, -z).grow(0.002),
                    0, 0, 0, 0.4F
            );

            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        }
    }

    public static void registerTexture(ResourceLocation resourceLocation) {
        ADDITIONAL_TEXTURES.add(resourceLocation);
    }
}
