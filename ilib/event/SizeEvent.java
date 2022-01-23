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

import ilib.ClientProxy;
import org.lwjgl.opengl.GL11;
import roj.collect.MyHashSet;

import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since  2021/2/3 13:01
 */
public final class SizeEvent {
    public static void register(boolean registerSlowMethod) {
        MinecraftForge.EVENT_BUS.register(SizeEvent.class);
        if(registerSlowMethod)
            MinecraftForge.EVENT_BUS.register(new SizeEvent());
    }

    @SubscribeEvent
    public static void onPlayerFall(LivingFallEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)event.getEntityLiving();
            if (player.height < 0.45F)
                event.setDistance(0);
            event.setDistance(event.getDistance() / player.height * 0.6F);
        }
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        World world = (event.getEntityLiving()).world;
        for (EntityLivingBase entities : world.getEntitiesWithinAABB(EntityLivingBase.class, entity.getEntityBoundingBox())) {
            if (!entity.isSneaking())
                if (entity.height / entities.height >= 4 && entities.getRidingEntity() != entity)
                    entities.attackEntityFrom(causeCrushingDamage(entity), entity.height - entities.height);
        }
    }

    public static DamageSource causeCrushingDamage(EntityLivingBase entity) { return new EntityDamageSource("crushing", entity); }

    @SubscribeEvent
    public static void onTargetEntity(LivingSetAttackTargetEvent event) {
        if (event.getTarget() instanceof EntityPlayer && event.getEntityLiving() instanceof EntityLiving) {
            EntityPlayer player = (EntityPlayer)event.getTarget();
            EntityLiving entity = (EntityLiving)event.getEntityLiving();
            if (player.height <= 0.45F && !(entity instanceof net.minecraft.entity.monster.EntitySpider) && !(entity instanceof net.minecraft.entity.passive.EntityOcelot))
                entity.setAttackTarget(null);
        }
    }

    MyHashSet<Block> slowDown = new MyHashSet<>(Blocks.DEADBUSH, Blocks.CARPET, Blocks.RED_FLOWER, Blocks.YELLOW_FLOWER, Blocks.REEDS, Blocks.SNOW, Blocks.SNOW_LAYER, Blocks.WEB, Blocks.SOUL_SAND);
    MyHashSet<Block> climb = new MyHashSet<>(Blocks.DIRT, Blocks.GRASS, Blocks.MYCELIUM, Blocks.LEAVES, Blocks.LEAVES2, Blocks.SAND, Blocks.SOUL_SAND, Blocks.CONCRETE_POWDER, Blocks.FARMLAND, Blocks.GRASS_PATH, Blocks.GRAVEL, Blocks.CLAY);
    MyHashSet<Block> hot = new MyHashSet<>(Blocks.AIR, Blocks.LAVA, Blocks.FLOWING_LAVA, Blocks.FIRE, Blocks.LIT_FURNACE, Blocks.LIT_PUMPKIN, Blocks.MAGMA);

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer player = event.player;
        World world = event.player.world;
        player.stepHeight = player.height / 3;
        player.jumpMovementFactor *= player.height / 1.8F;

        if (player.height < 0.9F) {
            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(player.posX, player.posY, player.posZ);
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            float ratio = player.height / 1.8F / 2;
            if(slowDown.contains(block)) {
                if (block instanceof net.minecraft.block.BlockRedFlower || (state == Blocks.DOUBLE_PLANT.getDefaultState().withProperty(BlockDoublePlant.VARIANT, BlockDoublePlant.EnumPlantType.ROSE)))
                    player.attackEntityFrom(DamageSource.CACTUS, 1);
                if (!player.capabilities.isFlying) {
                    player.motionX *= ratio;
                    if (block instanceof net.minecraft.block.BlockWeb)
                        player.motionY *= ratio;
                    player.motionZ *= ratio;
                }
            }

            if (player.height <= 0.45F) {
                EnumFacing facing = player.getHorizontalFacing();
                block = world.getBlockState(pos.move(facing)).getBlock();

                final boolean b = player.collidedHorizontally && canClimb(player, facing);
                if (b && climb.contains(block)) {
                    player.motionY = !player.isSneaking() ? 0.1D : 0;
                }

                check(player, world, pos, player.getHeldItemMainhand(), b);
                check(player, world, pos, player.getHeldItemOffhand(), b);
            }

            pos.release();
        }
    }

    private void check(EntityPlayer player, World world, BlockPos.PooledMutableBlockPos pos1, ItemStack stack, boolean b) {
        if (b && (stack.getItem() == Items.SLIME_BALL || (stack.getItem() == Item.getItemFromBlock(Blocks.SLIME_BLOCK)))) {
            player.motionY = !player.isSneaking() ? 0.1D : 0;
        } else if (stack.getItem() == Items.PAPER && !player.onGround) {
            player.jumpMovementFactor = 0.035F;
            player.fallDistance = 0;
            if (player.motionY < 0)
                player.motionY *= 0.6D;
            if (player.isSneaking()) {
                player.jumpMovementFactor *= 3.5F;
            } else {
                pos1.setPos(player.posX, player.posY, player.posZ);

                int blockY = (int) player.posY;

                Block block;
                do {
                    block = world.getBlockState(pos1).getBlock();
                    if(!hot.contains(block) || blockY - pos1.getY() > 25)
                        break;
                    pos1.setY(pos1.getY() - 1);

                    if (block != Blocks.AIR) {
                        player.motionY += 0.07D;
                    }
                } while (true);
            }
        }
    }

    public static boolean canClimb(final EntityPlayer player, final EnumFacing facing) {
        final World world = player.getEntityWorld();

        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(player.posX, player.posY, player.posZ);

        IBlockState base = world.getBlockState(pos);
        IBlockState front = world.getBlockState(pos.move(facing));
        IBlockState frontTop = world.getBlockState(pos.move(EnumFacing.UP));
        IBlockState top = world.getBlockState(pos.move(facing.getOpposite()));

        Block topBlk = top.getBlock();
        Block baseBlk = base.getBlock();

        if (!baseBlk.isPassable(world, pos.move(EnumFacing.DOWN)) || front.getBlock().isPassable(world, pos.move(facing))) {
            return (baseBlk instanceof BlockPane && !(topBlk instanceof BlockPane)) ||
                    (baseBlk instanceof BlockStairs && base.getValue(BlockStairs.FACING) == facing && base.getValue(BlockStairs.HALF) != BlockStairs.EnumHalf.TOP) ||
                    (baseBlk instanceof BlockSlab && !top.isNormalCube() && base.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.BOTTOM);
        }

        if (!frontTop.getBlock().isPassable(world, pos.setPos(player).move(facing).move(EnumFacing.UP)) && !topBlk.isPassable(world, pos.move(facing.getOpposite()))) {
            //if (tb instanceof BlockPane) {
            //
            //}
            return (topBlk instanceof BlockStairs && top.getValue(BlockStairs.FACING) == facing.getOpposite()) ||
                    (topBlk instanceof BlockSlab && !top.isNormalCube() && top.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP);
        }
        return true;
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof EntityLivingBase) {
            EntityLivingBase target = (EntityLivingBase)event.getTarget();
            EntityPlayer player = event.getEntityPlayer();
            if (target.height / 2 >= player.height)
                if (player.getHeldItemMainhand().getItem() == Items.STRING || player.getHeldItemOffhand().getItem() == Items.STRING)
                    player.startRiding(target);
            if (target.height * 2 <= player.height)
                target.startRiding(player);
            if (player.getHeldItemMainhand().isEmpty() && player.isBeingRidden() && player.isSneaking())
                for (Entity entities : player.getPassengers()) {
                    if (entities instanceof EntityLivingBase)
                        entities.dismountRidingEntity();
                }
        }
    }

    @SubscribeEvent
    public static void onEntityJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)event.getEntityLiving();
            float height = player.height / 1.8F;
            if(height < 0.65F)
                height = 0.65F;
            player.motionY *= height;
        }
    }

    @SubscribeEvent
    public static void onHarvest(PlayerEvent.BreakSpeed event) {
        EntityPlayer player = event.getEntityPlayer();
        event.setNewSpeed(event.getOriginalSpeed() * player.height / 1.8F);
    }

    /*@SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onFOVChange(FOVUpdateEvent event) {
        if (event.getEntity() != null) {
            EntityPlayer player = event.getEntity();
            GameSettings settings = (Minecraft.getMinecraft()).gameSettings;
            PotionEffect speed = player.getActivePotionEffect(MobEffects.SPEED);
            float fov = settings.fovSetting / settings.fovSetting;
            if (player.isSprinting()) {
                event.setNewfov((speed != null) ? (fov + 0.1F * (speed.getAmplifier() + 1) + 0.15F) : (fov + 0.1F));
            } else {
                event.setNewfov((speed != null) ? (fov + 0.1F * (speed.getAmplifier() + 1)) : fov);
            }
        }
    }*/

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        EntityPlayerSP player = ClientProxy.mc.player;
        float scale = player.height / 1.8F;
        if(scale > 1) {
            final int view = ClientProxy.mc.gameSettings.thirdPersonView;
            if (view == 1)
                GL11.glTranslatef(0, 0, -scale * 2);
            else if (view == 2)
                GL11.glTranslatef(0, 0, scale * 2);
        }
    }
}
