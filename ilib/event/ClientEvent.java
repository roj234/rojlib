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
import ilib.client.KeyRegister;
import ilib.client.misc.MyDebugOverlay;
import ilib.client.music.MusicPlayer;
import ilib.client.renderer.DebugRenderer;
import ilib.client.renderer.WaypointRenderer;
import ilib.gui.GuiHelper;
import ilib.item.ItemSelectTool;
import ilib.math.Arena;
import ilib.math.SelectionCache;
import ilib.util.*;
import ilib.world.saver.WorldSaver;
import org.lwjgl.input.Keyboard;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.math.MathUtils;
import roj.opengl.render.ArenaRenderer;
import roj.reflect.ReflectionUtils;
import roj.sound.mp3.Header;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.registry.RegistrySimple;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

import static ilib.ClientProxy.mc;

/**
 * @author Roj234
 * @since 2021/5/30 0:25
 */
public final class ClientEvent {
    public static void init() {
        MinecraftForge.EVENT_BUS.register(ClientEvent.class);

        if (Config.fpsLowTime > 0) {
            AutoFPS.init(Config.fpsLowTime, Config.fpsLowFPS, Config.fpsLowVol);
        }
    }

    public static boolean reloadSoundMgr(boolean force) {
        SoundManager sm = ReflectionClient.HELPER.getSoundManager(mc.getSoundHandler());
        boolean ok = ReflectionClient.HELPER.getInited(sm);
        if (!ok || force) {
            ImpLib.logger().info("??????SoundSystem");
            sm.reloadSoundSystem();
            return true;
        }
        return false;
    }

    private static void renderMusicHUD(ScaledResolution r) {
        int width = r.getScaledWidth();
        int height = r.getScaledHeight();

        MusicPlayer mp = MusicPlayer.instance;
        if(!mp.player.closed()) {
            boolean rightSide = true;

            String note = mp.player.paused() ? "\u258E\u258E" : "\u266C";
            int noteWidth = mc.fontRenderer.getStringWidth(note) * 2;
            int noteSpace = 4;

            Header hdr = mp.player.getHeader();
            int time = (int) (hdr.getFrameDuration() * hdr.getFrames() * 1000);
            String lyric = mp.getLyric(time);
            time /= 1000;
            String title = ((File) mp.playList.get(mp.playIndex)).getName() + " (" + (time / 60) + ":" + ((time %= 60) < 10 ? "0" : "") + time + ")";

            int padding = 4;

            int titleWidth = mc.fontRenderer.getStringWidth(title);
            int artistWidth = mc.fontRenderer.getStringWidth(lyric);

            int textWidth = Math.max(titleWidth, artistWidth);
            int hudWidth = textWidth + noteWidth + noteSpace + padding * 2;
            int hudHeight = 20 + padding * 2;

            int x = width - 4;
            int y = 4;

            if(rightSide)
                x -= hudWidth;

            if(x < 0) x = padding;
            if(y < 0) y = 0;

            int xf = x + hudWidth;
            int yf = y + hudHeight;

            if(xf > width)
                x -= (xf - width);
            if(yf > height)
                y -= (yf - height);

            int noteX = x + padding + (rightSide ? textWidth + noteSpace : 0);
            int noteY = y + padding;

            GlStateManager.pushMatrix();

            GlStateManager.scale(2,2,2);
            GlStateManager.translate(0.5F + (float) noteX / 2, 0.5F + (float) noteY / 2, 0);

            Color color = Color.getHSBColor((float) (System.currentTimeMillis() % 5000) / 5000F, 1F, 1F);
            mc.fontRenderer.drawString(note, 0, 0, color.darker().darker().getRGB());
            GlStateManager.translate(-0.5F, -0.5F, 0F);
            mc.fontRenderer.drawString(note, 0, 0, color.getRGB());

            GlStateManager.popMatrix();

            int diffTitle = 0;
            int diffArtist = 0;

            int textLeft = x + padding + (rightSide ? 0 : noteWidth + noteSpace);

            if(rightSide) {
                diffTitle = textWidth - titleWidth;
                diffArtist = textWidth - artistWidth;
            }

            mc.fontRenderer.drawStringWithShadow(title, textLeft + diffTitle, y + padding, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow(lyric, textLeft + diffArtist, y + 10 + padding, 0xDDDDDD);
        }
    }

    @SubscribeEvent
    public static void onOpenGui(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (gui instanceof GuiDownloadTerrain) {
            if (Config.changeWorldSpeed > 0) event.setGui(null);
        } else if (gui instanceof GuiMultiplayer) {
            WorldSaver.plusSid();
        }

        if (Config.betterKeyboard) {
            Keyboard.enableRepeatEvents(gui != null);
        }

        if (model11 != null) {
            //ATHandler.clearRegistry(model11);
            model11 = null;
        }
    }

    private static RegistrySimple<ModelResourceLocation, IBakedModel> model11;

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        model11 = (RegistrySimple<ModelResourceLocation, IBakedModel>) event.getModelRegistry();
    }

    // region ????????????

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onTakePicture(ScreenshotEvent event) {
        event.setCanceled(false);
    }

    // endregion
    // region ??????????????????

    static int lastPV;
    static float distance;

    @SubscribeEvent
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        float d = distance;

        int TPV = mc.gameSettings.thirdPersonView;
        if (mc.player != null && TPV == lastPV && TPV != 0) {
            if (KeyRegister.keyZoomOut.isKeyDown()) {
                if (KeyRegister.keyZoomIn.isKeyDown()) d = 0;
                else d += 0.1f;
            } else if (KeyRegister.keyZoomIn.isKeyDown()) {
                d -= 0.1f;
            }

            if (d != 0) GlStateManager.translate(0, 0, d);
        } else {
            d = 0;
            lastPV = TPV;
        }

        distance = (float) MathUtils.clamp(d, -20, 20);
    }

    // endregion
    // region ?????????????????????????????????, ??????????????????

    @SubscribeEvent
    public static void onRenderWordLast(RenderWorldLastEvent event) {
        Entity p = mc.getRenderManager().renderViewEntity;
        if (p == null) return;

        WaypointRenderer.render();

        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * (double) event.getPartialTicks();
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * (double) event.getPartialTicks();
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * (double) event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-x, -y, -z);

        DebugRenderer.drawPending();
        if (KeyRegister.shouldDrawLight) DebugRenderer.drawLight(false, false);

        Arena arena = SelectionCache.get(mc.player.getUniqueID().getMostSignificantBits());
        if (arena != null && arena.isOK()) {
            if (EntityHelper.canPlayerSee(new AxisAlignedBB(arena.getP1(), arena.getP2()))) {
                boolean noDepth = mc.player.getHeldItemMainhand().getItem() == ItemSelectTool.INSTANCE;
                if (noDepth) GlStateManager.disableDepth();
                // noinspection all
                int rgb = 0xFF000000 | MathHelper.hsvToRGB((System.currentTimeMillis() / 50) % 100 / 100f, 0.7F, 0.6F);
                ArenaRenderer.INSTANCE.setColor(rgb);
                // noinspection all
                ArenaRenderer.INSTANCE.render(EntityHelper.vec(arena.getP1()), EntityHelper.vec(arena.getP2()),
                                              (System.currentTimeMillis() / 20) % 36000);
                if (noDepth) GlStateManager.enableDepth();
            }
        }

        GlStateManager.popMatrix();
    }

    // endregion
    // region ?????????F3, HUD

    @SubscribeEvent
    public static void onRenderF3(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == ElementType.DEBUG && Config.betterF3)
            event.setCanceled(true);

        if(event.getType() == ElementType.CROSSHAIRS) {
            renderMusicHUD(event.getResolution());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderDebugOverlay(Text event) {
        if (mc.gameSettings.showDebugInfo && Config.betterF3) {
            MyDebugOverlay.process(event.getLeft(), event.getRight());
        }
    }

    // endregion
    // region ???????????????

    @SubscribeEvent
    public static void onRenderLava(EntityViewRenderEvent.FogDensity event) {
        if (event.getState().getBlock() == Blocks.LAVA && Config.clearLava) {
            event.setCanceled(true);
            GlStateManager.setFog(GlStateManager.FogMode.EXP);
            Entity entity = event.getEntity();
            if (entity instanceof EntityLivingBase) {
                if (mc.player.isCreative() && entity == mc.player) {
                    event.setDensity(0);
                    return;
                }

                if (((EntityLivingBase)entity).isPotionActive(MobEffects.FIRE_RESISTANCE)) {
                    event.setDensity(0.13F);
                    return;
                }
            }
            event.setDensity(entity.isImmuneToFire() ? 0.13f : 0.7f);
        }
    }

    @SubscribeEvent
    public static void onRenderFire(RenderBlockOverlayEvent event) {
        if (event.getBlockForOverlay().getBlock() == Blocks.FIRE && Config.clearLava) {
            if (mc.player.isCreative()) {
                event.setCanceled(true);
                return;
            }
            if (mc.player.isImmuneToFire() || mc.player.isPotionActive(MobEffects.FIRE_RESISTANCE)) {
                GlStateManager.translate(0, -0.25, 0);
            }
        }
    }

    // endregion
    // region ??????????????????

    @SubscribeEvent
    public static void onPotionShift(GuiScreenEvent.PotionShiftEvent event) {
        if (Config.disablePotionShift) event.setCanceled(true);
    }

    // endregion
    // region ???????????????, ?????????, ????????????
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        EntityPlayer p = ClientProxy.mc.player;
        if (p == null) return;

        if (event.phase == TickEvent.Phase.START) {
            // ??????????????????, ??????????????????MC??????????????????
            float yaw = p.rotationYaw;
            if (yaw < -360 || yaw > 360) {
                yaw %= 360f;
                p.rotationYaw = p.prevRotationYaw = yaw;
            }

            if (Config.autoClimb) {
                if (p.isOnLadder() && !p.isSneaking() && p.rotationPitch < -45) {
                    p.motionY = 0.1f;
                    p.velocityChanged = true;
                }
            }

            RayTraceResult hover = mc.objectMouseOver;
            if (hover != null && hover.typeOfHit == RayTraceResult.Type.BLOCK) {
                int timer = mc.rightClickDelayTimer;
                if (timer > KeyRegister.rightClickDelay) {
                    mc.rightClickDelayTimer = KeyRegister.rightClickDelay;
                }
            }

            if (autoClick != 0) {
                doAutoClick();
            }
        }
    }

    // endregion
    // region ???????????????????????????, ??????????????????????????????

    private static final List<ResourceLocation> moreSprites = new SimpleList<>();

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

        for (int i = 0; i < moreSprites.size(); i++) {
            map.registerSprite(moreSprites.get(i));
        }
    }

    public static void registerTexture(ResourceLocation loc) {
        moreSprites.add(loc);
    }

    // endregion
    // region ??????????????????

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

    // endregion
    // region ??????tooltip

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (mc.world == null) return;

        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        List<String> list = event.getToolTip();

        final EntityPlayer player = event.getEntityPlayer();

        if (item instanceof ICustomTooltip) {
            ((ICustomTooltip) item).handleTooltipEvent(list, stack, player);
        }

        final int f = Config.tooltipFlag;
        if (f != 0 && !GuiHelper.isShiftPressed()) {
            if ((f & 1) != 0) {
                list.add("\u00a7a" + I18n.format("tooltip.ilib.registry"));
                list.add("\u00a77 - " + item.getRegistryName());
            }
            if ((f & 2) != 0) {
                list.add("\u00a7a" + I18n.format("tooltip.ilib.unlocalized"));
                list.add("\u00a77 - " + item.getTranslationKey(stack) + ".name");
            }
            if ((f & 4) != 0) {
                int[] oreIds = OreDictionary.getOreIDs(stack);
                if (oreIds.length > 0) {
                    list.add("\u00a7a" + I18n.format("tooltip.ilib.oredict"));
                    for (int i : oreIds) {
                        list.add("\u00a77 - " + OreDictionary.getOreName(i));
                    }
                }
            }
            if ((f & 8) != 0 && stack.hasTagCompound()) {
                if (!GuiHelper.isCtrlPressed()) {
                    list.add("\u00a7aNBT (Ctrl)");
                } else {
                    list.add("\u00a7aNBT");
                    list.add("\u00a77 - " + NBTType.betterRender(stack.getTagCompound()));
                }
            }
            if ((f & 16) != 0 && item instanceof ItemFood) {
                list.add("\u00a7a" + I18n.format("tooltip.ilib.food"));
                ItemFood food = (ItemFood) item;
                list.add("\u00a77 - " + I18n.format("tooltip.ilib.food.meat") + MCTexts.format(food.isWolfsFavoriteMeat()));
                list.add("\u00a77 - " + I18n.format("tooltip.ilib.food.sat") + food.getSaturationModifier(stack));
                list.add("\u00a77 - " + I18n.format("tooltip.ilib.food.heal") + food.getHealAmount(stack));
            }

            checkFluid:
            if ((f & 4064) != 0) {
                if (item instanceof ItemBlock) {
                    Block block = ((ItemBlock) item).getBlock();
                    if (block instanceof IFluidBlock) {
                        Fluid fluid = ((IFluidBlock) block).getFluid();
                        fluidTooltip(list, f, fluid);
                    }
                } else if (stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                    IFluidHandlerItem cap = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                    // noinspection all
                    IFluidTankProperties[] props = cap.getTankProperties();
                    if (props.length == 0) break checkFluid;

                    int i = 0;
                    while (true) {
                        FluidStack stack1 = props[i++].getContents();
                        if (stack1 != null) fluidTooltip(list, f, stack1.getFluid());
                        if (i == props.length) break;
                        list.add("\u00a7a\u00a7l - - - ");
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
                    int i = list.indexOf(MCTexts.format("item.modifiers." + EntityEquipmentSlot.MAINHAND.getName()));
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

    private static void fluidTooltip(List<String> list, int flag, Fluid fluid) {
        list.add("\u00a7a" + I18n.format("tooltip.ilib.fluid"));
        if ((flag & 32) != 0) {
            list.add("\u00a77 - " + I18n.format("tooltip.ilib.f.viscosity") + fluid.getViscosity());
        }
        if ((flag & 64) != 0) {
            list.add("\u00a77 - " + I18n.format("tooltip.ilib.f.luminosity") + fluid.getLuminosity());
        }
        if ((flag & 128) != 0) {
            list.add("\u00a77 - " + I18n.format("tooltip.ilib.f.temperature") + fluid.getTemperature());
        }
        if ((flag & 256) != 0) {
            list.add("\u00a77 - " + I18n.format("tooltip.ilib.f.color") + Integer.toHexString(
                fluid.getColor()));
        }
        if ((flag & 512) != 0) {
            list.add("\u00a77 - " + I18n.format("tooltip.ilib.f.density") + fluid.getDensity());
        }
        if ((flag & 1024) != 0) {
            list.add("\u00a77 - " + I18n.format("tooltip.ilib.f.gas") + fluid.isGaseous());
        }
        if ((flag & 2048) != 0) {
            list.add("\u00a77 - " + I18n.format("tooltip.ilib.f.place") + fluid.canBePlacedInWorld());
        }
    }

    static ItemStack lastRender;
    static String[] backup;
    static int timer, prevLen;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void lastTooltip(RenderTooltipEvent.Pre event) {
        int rows = Config.pagedTooltipLen;
        if (rows <= 0) return;

        List<String> tooltip = Reflection.HELPER.getModifiableList(event.getLines());

        if (lastRender == null || !InventoryUtil.areItemStacksEqual(event.getStack(), lastRender) ||
                tooltip.size() != prevLen) {
            timer = 0;
            prevLen = tooltip.size();

            for (int i = 0; i < tooltip.size(); i++) {
                String s = tooltip.get(i);
                if (s.length() > 128) {
                    tooltip.set(i, s.substring(0, 128));
                    tooltip.add(i+1, s.substring(128));
                }
            }

            lastRender = event.getStack().copy();

            if (tooltip.size() <= rows) {
                timer = -1;
                return;
            }

            backup = tooltip.toArray(new String[tooltip.size()]);
        }

        if (timer < 0) return;
        int pages = (int) Math.ceil(((double) backup.length) / rows);

        int v = timer++ / Config.pagedTooltipTime;
        if (v >= pages) v = timer = 0;

        int end = Math.min(backup.length, (v + 1) * rows);

        tooltip.clear();
        for (int i = v * rows; i < end; i++) {
            tooltip.add(backup[i]);
        }

        tooltip.add(String.valueOf(v + 1) + '/' + pages + " ??? (" + (Config.pagedTooltipTime - timer % Config.pagedTooltipTime) + ")");
    }

    // endregion
    // region ????????????//???????????????

    static int autoClick;
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

    static boolean notice;
    private static void notice() {
        if(!notice) {
            PlayerUtil.sendTo(null, "//auto????????????: [autoClick/autoDig ??????] autoClickAir autoKill autoClear ?????????????????? ????????? ???????????? ?????? ??????\n?????????????????????, autoClick/Dig??????");
            notice = true;
        }
    }

    @SubscribeEvent
    public static void reloadSndManager(ClientChatEvent event) {
        final String msg = event.getOriginalMessage();
        if(!msg.startsWith("//")) return;
        switch (msg) {
            case "//reloadSoundMgr":
                PlayerUtil.sendTo(null, reloadSoundMgr(true) ? "????????????" : "????????????, ??????????????????????????????...");
            break;
            case "//worldSaver":
                PlayerUtil.sendTo(null, (WorldSaver.toggleEnable()) ? "??????????????????????????????" : "?????????");
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

    // endregion
    // region ??????????????????????????????
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

    // endregion
    // region ??????????????????

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

    // endregion
}
