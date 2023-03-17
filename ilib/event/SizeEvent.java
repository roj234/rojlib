package ilib.event;

import ilib.ClientProxy;
import ilib.capabilities.Capabilities;
import ilib.capabilities.EntitySize;
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
import net.minecraft.util.math.AxisAlignedBB;
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
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since 2021/2/3 13:01
 */
public final class SizeEvent {
	public static void register(boolean registerSlowMethod) {
		MinecraftForge.EVENT_BUS.register(SizeEvent.class);
		if (registerSlowMethod) MinecraftForge.EVENT_BUS.register(new SizeEvent());
	}

	@SubscribeEvent
	public static void onPlayerFall(LivingFallEvent event) {
		if (event.getEntityLiving() instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) event.getEntityLiving();
			if (player.height < 0.45F) {event.setDistance(0);} else event.setDistance(event.getDistance() / player.height * 0.6F);
		}
	}

	//    @SubscribeEvent
	//    public void onLivingTick(LivingEvent.LivingUpdateEvent event) {
	//        EntityLivingBase entity = event.getEntityLiving();
	//        List<EntityLivingBase> es = entity.world.getEntitiesWithinAABB(EntityLivingBase.class,
	//                                                                       entity.getEntityBoundingBox());
	//        for (int i = 0; i < es.size(); i++) {
	//            EntityLivingBase e = es.get(i);
	//            if (!entity.isSneaking()) {
	//                if (entity.height / e.height >= 4 && e.getRidingEntity() != entity)
	//                    e.attackEntityFrom(causeCrushingDamage(entity), entity.height - e.height);
	//            }
	//        }
	//    }

	private static void updateTick(EntityLivingBase entity) {
		EntitySize cap = entity.getCapability(Capabilities.RENDERING_SIZE, null);
		if (cap != null) {
			double rh = cap.relHeight, rw = cap.relWidth;
			float height = (float) (cap.defHeight * rh), width = (float) (cap.defWidth * rw);
			if (rw != 1 || rh != 1) {
				cap.transformed = true;

				if (entity instanceof EntityPlayer) {
					EntityPlayer player = (EntityPlayer) entity;
					float eyeHeight = (float) (player.getDefaultEyeHeight() * rh);
					if (player.isSneaking()) {
						height *= 0.9166667F;
						eyeHeight *= 0.9382716F;
					}
					if (player.isElytraFlying()) height *= 0.33F;
					if (player.isPlayerSleeping()) {
						width = 0.2F;
						height = 0.2F;
					}
					//if (player.isRiding()) {
					//
					//}
					width = Math.max(width, 0.15F);
					height = Math.max(height, 0.25F);
					if (height >= 1.6F) {
						player.eyeHeight = eyeHeight;
					} else {
						player.eyeHeight = eyeHeight * 0.9876542F;
					}
				} else {
					width = Math.max(width, 0.04F);
					height = Math.max(height, 0.08F);
				}
				entity.height = height;
				entity.width = width;
				double d0 = width / 2.0D;
				AxisAlignedBB aabb = entity.getEntityBoundingBox();
				entity.setEntityBoundingBox(new AxisAlignedBB(entity.posX - d0, aabb.minY, entity.posZ - d0, entity.posX + d0, aabb.minY + entity.height, entity.posZ + d0));
			} else {
				if (cap.transformed) {
					entity.height = height;
					entity.width = width;
					double d0 = width / 2.0D;
					AxisAlignedBB aabb = entity.getEntityBoundingBox();
					entity.setEntityBoundingBox(new AxisAlignedBB(entity.posX - d0, aabb.minY, entity.posZ - d0, entity.posX + d0, aabb.minY + height, entity.posZ + d0));

					if (entity instanceof EntityPlayer) {
						EntityPlayer player = (EntityPlayer) entity;
						player.eyeHeight = player.getDefaultEyeHeight();
					}
					cap.transformed = false;
				}
			}
		}
	}


	public static DamageSource causeCrushingDamage(EntityLivingBase entity) {return new EntityDamageSource("crushing", entity);}

	@SubscribeEvent
	public static void onTargetEntity(LivingSetAttackTargetEvent event) {
		if (event.getTarget() instanceof EntityPlayer && event.getEntityLiving() instanceof EntityLiving) {
			EntityPlayer player = (EntityPlayer) event.getTarget();
			EntityLiving entity = (EntityLiving) event.getEntityLiving();
			if (player.height <= 0.45F && !(entity instanceof net.minecraft.entity.monster.EntitySpider) && !(entity instanceof net.minecraft.entity.passive.EntityOcelot)) {
				entity.setAttackTarget(null);
			}
		}
	}

	MyHashSet<Block> slowDown = new MyHashSet<>(Blocks.DEADBUSH, Blocks.CARPET, Blocks.RED_FLOWER, Blocks.YELLOW_FLOWER, Blocks.REEDS, Blocks.SNOW, Blocks.SNOW_LAYER, Blocks.WEB, Blocks.SOUL_SAND);
	MyHashSet<Block> climb = new MyHashSet<>(Blocks.DIRT, Blocks.GRASS, Blocks.MYCELIUM, Blocks.LEAVES, Blocks.LEAVES2, Blocks.SAND, Blocks.SOUL_SAND, Blocks.CONCRETE_POWDER, Blocks.FARMLAND,
											 Blocks.GRASS_PATH, Blocks.GRAVEL, Blocks.CLAY);
	MyHashSet<Block> hot = new MyHashSet<>(Blocks.AIR, Blocks.LAVA, Blocks.FLOWING_LAVA, Blocks.FIRE, Blocks.LIT_FURNACE, Blocks.LIT_PUMPKIN, Blocks.MAGMA);

	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == Phase.START) return;
		updateTick(event.player);

		EntityPlayer player = event.player;
		World world = event.player.world;
		player.stepHeight = player.height / 3;
		player.jumpMovementFactor = 0.02f * player.height / 1.8F;

		if (player.height < 0.9F) {
			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(player.posX, player.posY, player.posZ);
			IBlockState state = world.getBlockState(pos);
			Block block = state.getBlock();
			float ratio = player.height / 1.8F / 2;
			if (slowDown.contains(block)) {
				if (block instanceof net.minecraft.block.BlockRedFlower || (state == Blocks.DOUBLE_PLANT.getDefaultState()
																										.withProperty(BlockDoublePlant.VARIANT, BlockDoublePlant.EnumPlantType.ROSE))) {
					player.attackEntityFrom(DamageSource.CACTUS, 1);
				}
				if (!player.capabilities.isFlying) {
					player.motionX *= ratio;
					if (block instanceof net.minecraft.block.BlockWeb) player.motionY *= ratio;
					player.motionZ *= ratio;
				}
			}

			if (player.height <= 0.45F) {
				EnumFacing facing = player.getHorizontalFacing();
				block = world.getBlockState(pos.move(facing)).getBlock();

				final boolean b = player.collidedHorizontally && canClimb(player, facing);
				if (b && climb.contains(block)) {
					player.motionY = !player.isSneaking() ? 0.1D : 0;
				} else {
					check(player, world, pos, player.getHeldItemMainhand(), b);
					check(player, world, pos, player.getHeldItemOffhand(), b);
				}
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
			if (player.motionY < 0) {
				double vec = 1 - (player.motionX * player.motionX + player.motionZ * player.motionZ) * 10;
				player.motionY *= 0.99D * Math.max(vec, 0.95);
			}
			if (player.isSneaking()) {
				player.jumpMovementFactor *= 3.5F;
			} else {
				pos1.setPos(player.posX, player.posY, player.posZ);

				int blockY = (int) player.posY;

				Block block;
				do {
					block = world.getBlockState(pos1).getBlock();
					if (!hot.contains(block) || blockY - pos1.getY() > 25) break;
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
			return (baseBlk instanceof BlockPane && !(topBlk instanceof BlockPane)) || (baseBlk instanceof BlockStairs && base.getValue(BlockStairs.FACING) == facing && base.getValue(
				BlockStairs.HALF) != BlockStairs.EnumHalf.TOP) || (baseBlk instanceof BlockSlab && !top.isNormalCube() && base.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.BOTTOM);
		}

		if (!frontTop.getBlock().isPassable(world, pos.setPos(player).move(facing).move(EnumFacing.UP)) && !topBlk.isPassable(world, pos.move(facing.getOpposite()))) {
			//if (tb instanceof BlockPane) {
			//
			//}
			return (topBlk instanceof BlockStairs && top.getValue(BlockStairs.FACING) == facing.getOpposite()) || (topBlk instanceof BlockSlab && !top.isNormalCube() && top.getValue(
				BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP);
		}
		return true;
	}

	@SubscribeEvent
	public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getTarget() instanceof EntityLivingBase) {
			EntityLivingBase target = (EntityLivingBase) event.getTarget();
			EntityPlayer player = event.getEntityPlayer();
			if (target.height / 2 >= player.height) if (player.getHeldItemMainhand().getItem() == Items.STRING || player.getHeldItemOffhand().getItem() == Items.STRING) player.startRiding(target);
			if (target.height * 2 <= player.height) target.startRiding(player);
			if (player.getHeldItemMainhand().isEmpty() && player.isBeingRidden() && player.isSneaking()) {
				for (Entity entities : player.getPassengers()) {
					if (entities instanceof EntityLivingBase) entities.dismountRidingEntity();
				}
			}
		}
	}

	@SubscribeEvent
	public static void onEntityJump(LivingEvent.LivingJumpEvent event) {
		if (event.getEntityLiving() instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) event.getEntityLiving();
			player.motionY *= Math.sqrt(player.height / 1.8F);
		}
	}

	@SubscribeEvent
	public static void onHarvest(PlayerEvent.BreakSpeed event) {
		EntityPlayer player = event.getEntityPlayer();
		event.setNewSpeed(event.getOriginalSpeed() * player.height / 1.8F);
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
		EntityPlayerSP player = ClientProxy.mc.player;
		float scale = player.height / 1.8F;
		if (scale < 0) scale = -scale;
		final int view = ClientProxy.mc.gameSettings.thirdPersonView;
		if (view == 1) {GL11.glTranslatef(0, 0, -scale * 2);} else if (view == 2) GL11.glTranslatef(0, 0, scale * 2);
	}
}
