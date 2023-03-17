package ilib.minestom_instance;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IJumpingMount;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecartCommandBlock;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayer.EnumChatVisibility;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemWritableBook;
import net.minecraft.item.ItemWrittenBook;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.CPacketClientStatus.State;
import net.minecraft.network.play.client.CPacketSeenAdvancements.Action;
import net.minecraft.network.play.server.*;
import net.minecraft.network.play.server.SPacketPlayerPosLook.EnumFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.*;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.ForgeHooks;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class InstanceNetHandler extends net.minecraft.network.NetHandlerPlayServer {
	private static final Logger LOGGER = LogManager.getLogger();
	public final NetworkManager netManager;
	private final MinecraftServer server;
	private final IntHashMap<Short> pendingTransactions = new IntHashMap();
	public EntityPlayerMP player;
	private NetHandlerPlayServer delegate;
	private int networkTickCount;
	private long field_194402_f;
	private boolean field_194403_g;
	private int chatSpamThresholdCount;
	private int itemDropThreshold;
	private double firstGoodX;
	private double firstGoodY;
	private double firstGoodZ;
	private double lastGoodX;
	private double lastGoodY;
	private double lastGoodZ;
	private Entity lowestRiddenEnt;
	private double lowestRiddenX;
	private double lowestRiddenY;
	private double lowestRiddenZ;
	private double lowestRiddenX1;
	private double lowestRiddenY1;
	private double lowestRiddenZ1;
	private Vec3d targetPos;
	private int teleportId;
	private int lastPositionUpdate;
	private boolean floating;
	private int floatingTickCount;
	private boolean vehicleFloating;
	private int vehicleFloatingTickCount;
	private int movePacketCounter;
	private int lastMovePacketCounter;
	//private              Instance             instance;

	public InstanceNetHandler(MinecraftServer server, NetworkManager networkManagerIn, EntityPlayerMP playerIn) {
		super(server, networkManagerIn, playerIn);
		this.server = server;
		this.netManager = networkManagerIn;
		networkManagerIn.setNetHandler(this);
		this.player = playerIn;
		playerIn.connection = this;
	}

	private static boolean isMovePlayerPacketInvalid(CPacketPlayer packetIn) {
		if (Doubles.isFinite(packetIn.getX(0.0D)) && Doubles.isFinite(packetIn.getY(0.0D)) && Doubles.isFinite(packetIn.getZ(0.0D)) && Floats.isFinite(packetIn.getPitch(0.0F)) && Floats.isFinite(
			packetIn.getYaw(0.0F))) {
			return Math.abs(packetIn.getX(0.0D)) > 3.0E7D || Math.abs(packetIn.getY(0.0D)) > 3.0E7D || Math.abs(packetIn.getZ(0.0D)) > 3.0E7D;
		} else {
			return true;
		}
	}

	private static boolean isMoveVehiclePacketInvalid(CPacketVehicleMove packetIn) {
		return !Doubles.isFinite(packetIn.getX()) || !Doubles.isFinite(packetIn.getY()) || !Doubles.isFinite(packetIn.getZ()) || !Floats.isFinite(packetIn.getPitch()) || !Floats.isFinite(
			packetIn.getYaw());
	}

	public void update() {
		delegate.update();
	}

	public NetworkManager getNetworkManager() {
		return delegate.getNetworkManager();
	}

	public void disconnect(ITextComponent tx) {
		delegate.disconnect(tx);
	}

	public void processInput(CPacketInput packetIn) {
		delegate.processInput(packetIn);
	}

	public void processVehicleMove(CPacketVehicleMove packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		if (isMoveVehiclePacketInvalid(packetIn)) {
			this.disconnect(new TextComponentTranslation("multiplayer.disconnect.invalid_vehicle_movement"));
		} else {
			Entity entity = this.player.getLowestRidingEntity();
			if (entity != this.player && entity.getControllingPassenger() == this.player && entity == this.lowestRiddenEnt) {
				WorldServer worldserver = this.player.getServerWorld();
				double d0 = entity.posX;
				double d1 = entity.posY;
				double d2 = entity.posZ;
				double d3 = packetIn.getX();
				double d4 = packetIn.getY();
				double d5 = packetIn.getZ();
				float f = packetIn.getYaw();
				float f1 = packetIn.getPitch();
				double d6 = d3 - this.lowestRiddenX;
				double d7 = d4 - this.lowestRiddenY;
				double d8 = d5 - this.lowestRiddenZ;
				double d9 = entity.motionX * entity.motionX + entity.motionY * entity.motionY + entity.motionZ * entity.motionZ;
				double d10 = d6 * d6 + d7 * d7 + d8 * d8;
				if (d10 - d9 > 100.0D && (!this.server.isSinglePlayer() || !this.server.getServerOwner().equals(entity.getName()))) {
					LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", entity.getName(), this.player.getName(), d6, d7, d8);
					this.netManager.sendPacket(new SPacketMoveVehicle(entity));
					return;
				}

				boolean flag = worldserver.getCollisionBoxes(entity, entity.getEntityBoundingBox().shrink(0.0625D)).isEmpty();
				d6 = d3 - this.lowestRiddenX1;
				d7 = d4 - this.lowestRiddenY1 - 1.0E-6D;
				d8 = d5 - this.lowestRiddenZ1;
				entity.move(MoverType.PLAYER, d6, d7, d8);
				double d11 = d7;
				d6 = d3 - entity.posX;
				d7 = d4 - entity.posY;
				if (d7 > -0.5D || d7 < 0.5D) {
					d7 = 0.0D;
				}

				d8 = d5 - entity.posZ;
				d10 = d6 * d6 + d7 * d7 + d8 * d8;
				boolean flag1 = false;
				if (d10 > 0.0625D) {
					flag1 = true;
					LOGGER.warn("{} moved wrongly!", entity.getName());
				}

				entity.setPositionAndRotation(d3, d4, d5, f, f1);
				this.player.setPositionAndRotation(d3, d4, d5, this.player.rotationYaw, this.player.rotationPitch);
				boolean flag2 = worldserver.getCollisionBoxes(entity, entity.getEntityBoundingBox().shrink(0.0625D)).isEmpty();
				if (flag && (flag1 || !flag2)) {
					entity.setPositionAndRotation(d0, d1, d2, f, f1);
					this.player.setPositionAndRotation(d0, d1, d2, this.player.rotationYaw, this.player.rotationPitch);
					this.netManager.sendPacket(new SPacketMoveVehicle(entity));
					return;
				}

				this.server.getPlayerList().serverUpdateMovingPlayer(this.player);
				this.player.addMovementStat(this.player.posX - d0, this.player.posY - d1, this.player.posZ - d2);
				this.vehicleFloating = d11 >= -0.03125D && !this.server.isFlightAllowed() && !worldserver.checkBlockCollision(entity.getEntityBoundingBox().grow(0.0625D).expand(0.0D, -0.55D, 0.0D));
				this.lowestRiddenX1 = entity.posX;
				this.lowestRiddenY1 = entity.posY;
				this.lowestRiddenZ1 = entity.posZ;
			}
		}

	}

	public void processConfirmTeleport(CPacketConfirmTeleport packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		if (packetIn.getTeleportId() == this.teleportId) {
			this.player.setPositionAndRotation(this.targetPos.x, this.targetPos.y, this.targetPos.z, this.player.rotationYaw, this.player.rotationPitch);
			if (this.player.isInvulnerableDimensionChange()) {
				this.lastGoodX = this.targetPos.x;
				this.lastGoodY = this.targetPos.y;
				this.lastGoodZ = this.targetPos.z;
				this.player.clearInvulnerableDimensionChange();
			}

			this.targetPos = null;
		}

	}

	public void handleRecipeBookUpdate(CPacketRecipeInfo p_191984_1_) {}

	public void handleSeenAdvancements(CPacketSeenAdvancements p_194027_1_) {
		PacketThreadUtil.checkThreadAndEnqueue(p_194027_1_, this, this.player.getServerWorld());
		if (p_194027_1_.getAction() == Action.OPENED_TAB) {
			ResourceLocation resourcelocation = p_194027_1_.getTab();
			Advancement advancement = this.server.getAdvancementManager().getAdvancement(resourcelocation);
			if (advancement != null) {
				this.player.getAdvancements().setSelectedTab(advancement);
			}
		}

	}

	public void processPlayer(CPacketPlayer packetIn) {
		delegate.processPlayer(packetIn);
	}

	public void setPlayerLocation(double x, double y, double z, float yaw, float pitch) {
		this.setPlayerLocation(x, y, z, yaw, pitch, Collections.emptySet());
	}

	public void setPlayerLocation(double x, double y, double z, float yaw, float pitch, Set<EnumFlags> relativeSet) {
		double d0 = relativeSet.contains(EnumFlags.X) ? this.player.posX : 0.0D;
		double d1 = relativeSet.contains(EnumFlags.Y) ? this.player.posY : 0.0D;
		double d2 = relativeSet.contains(EnumFlags.Z) ? this.player.posZ : 0.0D;
		this.targetPos = new Vec3d(x + d0, y + d1, z + d2);
		float f = yaw;
		float f1 = pitch;
		if (relativeSet.contains(EnumFlags.Y_ROT)) {
			f = yaw + this.player.rotationYaw;
		}

		if (relativeSet.contains(EnumFlags.X_ROT)) {
			f1 = pitch + this.player.rotationPitch;
		}

		if (++this.teleportId == 2147483647) {
			this.teleportId = 0;
		}

		this.lastPositionUpdate = this.networkTickCount;
		this.player.setPositionAndRotation(this.targetPos.x, this.targetPos.y, this.targetPos.z, f, f1);
		this.player.connection.sendPacket(new SPacketPlayerPosLook(x, y, z, yaw, pitch, relativeSet, this.teleportId));
	}

	public void processPlayerDigging(CPacketPlayerDigging packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		WorldServer worldserver = this.server.getWorld(this.player.dimension);
		BlockPos blockpos = packetIn.getPosition();
		this.player.markPlayerActive();
		switch (packetIn.getAction()) {
			case SWAP_HELD_ITEMS:
				if (!this.player.isSpectator()) {
					ItemStack itemstack = this.player.getHeldItem(EnumHand.OFF_HAND);
					this.player.setHeldItem(EnumHand.OFF_HAND, this.player.getHeldItem(EnumHand.MAIN_HAND));
					this.player.setHeldItem(EnumHand.MAIN_HAND, itemstack);
				}

				return;
			case DROP_ITEM:
				if (!this.player.isSpectator()) {
					this.player.dropItem(false);
				}

				return;
			case DROP_ALL_ITEMS:
				if (!this.player.isSpectator()) {
					this.player.dropItem(true);
				}

				return;
			case RELEASE_USE_ITEM:
				this.player.stopActiveHand();
				return;
			case START_DESTROY_BLOCK:
			case ABORT_DESTROY_BLOCK:
			case STOP_DESTROY_BLOCK:
				double d0 = this.player.posX - ((double) blockpos.getX() + 0.5D);
				double d1 = this.player.posY - ((double) blockpos.getY() + 0.5D) + 1.5D;
				double d2 = this.player.posZ - ((double) blockpos.getZ() + 0.5D);
				double d3 = d0 * d0 + d1 * d1 + d2 * d2;
				double dist = this.player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 1.0D;
				dist *= dist;
				if (d3 > dist) {
					return;
				} else if (blockpos.getY() >= this.server.getBuildLimit()) {
					return;
				} else {
					if (packetIn.getAction() == net.minecraft.network.play.client.CPacketPlayerDigging.Action.START_DESTROY_BLOCK) {
						if (!this.server.isBlockProtected(worldserver, blockpos, this.player) && worldserver.getWorldBorder().contains(blockpos)) {
							this.player.interactionManager.onBlockClicked(blockpos, packetIn.getFacing());
						} else {
							this.player.connection.sendPacket(new SPacketBlockChange(worldserver, blockpos));
						}
					} else {
						if (packetIn.getAction() == net.minecraft.network.play.client.CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
							this.player.interactionManager.blockRemoving(blockpos);
						} else if (packetIn.getAction() == net.minecraft.network.play.client.CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
							this.player.interactionManager.cancelDestroyingBlock();
						}

						if (worldserver.getBlockState(blockpos).getMaterial() != Material.AIR) {
							this.player.connection.sendPacket(new SPacketBlockChange(worldserver, blockpos));
						}
					}

					return;
				}
			default:
				throw new IllegalArgumentException("Invalid player action");
		}
	}

	public void processTryUseItemOnBlock(CPacketPlayerTryUseItemOnBlock packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		WorldServer worldserver = this.server.getWorld(this.player.dimension);
		EnumHand enumhand = packetIn.getHand();
		ItemStack itemstack = this.player.getHeldItem(enumhand);
		BlockPos blockpos = packetIn.getPos();
		EnumFacing enumfacing = packetIn.getDirection();
		this.player.markPlayerActive();
		if (blockpos.getY() >= this.server.getBuildLimit() - 1 && (enumfacing == EnumFacing.UP || blockpos.getY() >= this.server.getBuildLimit())) {
			TextComponentTranslation textcomponenttranslation = new TextComponentTranslation("build.tooHigh", this.server.getBuildLimit());
			textcomponenttranslation.getStyle().setColor(TextFormatting.RED);
			this.player.connection.sendPacket(new SPacketChat(textcomponenttranslation, ChatType.GAME_INFO));
		} else {
			double dist = this.player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 3.0D;
			dist *= dist;
			if (this.targetPos == null && this.player.getDistanceSq((double) blockpos.getX() + 0.5D, (double) blockpos.getY() + 0.5D,
																	(double) blockpos.getZ() + 0.5D) < dist && !this.server.isBlockProtected(worldserver, blockpos,
																																			 this.player) && worldserver.getWorldBorder()
																																										.contains(blockpos)) {
				this.player.interactionManager.processRightClickBlock(this.player, worldserver, itemstack, enumhand, blockpos, enumfacing, packetIn.getFacingX(), packetIn.getFacingY(),
																	  packetIn.getFacingZ());
			}
		}

		this.player.connection.sendPacket(new SPacketBlockChange(worldserver, blockpos));
		this.player.connection.sendPacket(new SPacketBlockChange(worldserver, blockpos.offset(enumfacing)));
	}

	public void processTryUseItem(CPacketPlayerTryUseItem packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		WorldServer worldserver = this.server.getWorld(this.player.dimension);
		EnumHand enumhand = packetIn.getHand();
		ItemStack itemstack = this.player.getHeldItem(enumhand);
		this.player.markPlayerActive();
		if (!itemstack.isEmpty()) {
			this.player.interactionManager.processRightClick(this.player, worldserver, itemstack, enumhand);
		}

	}

	public void handleSpectate(CPacketSpectate packetIn) {
		delegate.handleSpectate(packetIn);

	}

	public void handleResourcePackStatus(CPacketResourcePackStatus packetIn) {
	}

	public void processSteerBoat(CPacketSteerBoat packetIn) {
		delegate.processSteerBoat(packetIn);
	}

	public void onDisconnect(ITextComponent reason) {
		LOGGER.info("{} lost connection: {}", this.player.getName(), reason.getUnformattedText());
		this.server.refreshStatusNextTick();
		TextComponentTranslation textcomponenttranslation = new TextComponentTranslation("multiplayer.player.left", this.player.getDisplayName());
		textcomponenttranslation.getStyle().setColor(TextFormatting.YELLOW);
		this.server.getPlayerList().sendMessage(textcomponenttranslation);
		this.player.mountEntityAndWakeUp();
		this.server.getPlayerList().playerLoggedOut(this.player);
		if (this.server.isSinglePlayer() && this.player.getName().equals(this.server.getServerOwner())) {
			LOGGER.info("Stopping singleplayer server as player logged out");
			this.server.initiateShutdown();
		}

	}

	public void sendPacket(final Packet<?> packetIn) {
		if (packetIn instanceof SPacketChat) {
			SPacketChat spacketchat = (SPacketChat) packetIn;
			EnumChatVisibility entityplayer$enumchatvisibility = this.player.getChatVisibility();
			if (entityplayer$enumchatvisibility == EnumChatVisibility.HIDDEN && spacketchat.getType() != ChatType.GAME_INFO) {
				return;
			}

			if (entityplayer$enumchatvisibility == EnumChatVisibility.SYSTEM && !spacketchat.isSystem()) {
				return;
			}
		}

		delegate.sendPacket(packetIn);
	}

	public void processHeldItemChange(CPacketHeldItemChange packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		if (packetIn.getSlotId() >= 0 && packetIn.getSlotId() < InventoryPlayer.getHotbarSize()) {
			this.player.inventory.currentItem = packetIn.getSlotId();
			this.player.markPlayerActive();
		} else {
			LOGGER.warn("{} tried to set an invalid carried item", this.player.getName());
		}

	}

	public void processChatMessage(CPacketChatMessage packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		if (this.player.getChatVisibility() == EnumChatVisibility.HIDDEN) {
			TextComponentTranslation textcomponenttranslation = new TextComponentTranslation("chat.cannotSend");
			textcomponenttranslation.getStyle().setColor(TextFormatting.RED);
			this.sendPacket(new SPacketChat(textcomponenttranslation));
		} else {
			this.player.markPlayerActive();
			String s = packetIn.getMessage();
			s = StringUtils.normalizeSpace(s);

			for (int i = 0; i < s.length(); ++i) {
				if (!ChatAllowedCharacters.isAllowedCharacter(s.charAt(i))) {
					this.disconnect(new TextComponentTranslation("multiplayer.disconnect.illegal_characters"));
					return;
				}
			}

			if (s.startsWith("/")) {
				this.handleSlashCommand(s);
			} else {
				ITextComponent itextcomponent = new TextComponentTranslation("chat.type.text", this.player.getDisplayName(), ForgeHooks.newChatWithLinks(s));
				ITextComponent tex2 = ForgeHooks.onServerChatEvent(this, s, itextcomponent);
				if (tex2 == null) {
					return;
				}

				this.server.getPlayerList().sendMessage(tex2, false);
			}

			this.chatSpamThresholdCount += 20;
			if (this.chatSpamThresholdCount > 200 && !this.server.getPlayerList().canSendCommands(this.player.getGameProfile())) {
				this.disconnect(new TextComponentTranslation("disconnect.spam"));
			}
		}

	}

	private void handleSlashCommand(String command) {
		this.server.getCommandManager().executeCommand(this.player, command);
	}

	public void handleAnimation(CPacketAnimation packetIn) {
		delegate.handleAnimation(packetIn);
	}

	public void processEntityAction(CPacketEntityAction packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		this.player.markPlayerActive();
		IJumpingMount ijumpingmount1;
		switch (packetIn.getAction()) {
			case START_SNEAKING:
				this.player.setSneaking(true);
				break;
			case STOP_SNEAKING:
				this.player.setSneaking(false);
				break;
			case START_SPRINTING:
				this.player.setSprinting(true);
				break;
			case STOP_SPRINTING:
				this.player.setSprinting(false);
				break;
			case STOP_SLEEPING:
				if (this.player.isPlayerSleeping()) {
					this.player.wakeUpPlayer(false, true, true);
					this.targetPos = new Vec3d(this.player.posX, this.player.posY, this.player.posZ);
				}
				break;
			case START_RIDING_JUMP:
				if (this.player.getRidingEntity() instanceof IJumpingMount) {
					ijumpingmount1 = (IJumpingMount) this.player.getRidingEntity();
					int i = packetIn.getAuxData();
					if (ijumpingmount1.canJump() && i > 0) {
						ijumpingmount1.handleStartJump(i);
					}
				}
				break;
			case STOP_RIDING_JUMP:
				if (this.player.getRidingEntity() instanceof IJumpingMount) {
					ijumpingmount1 = (IJumpingMount) this.player.getRidingEntity();
					ijumpingmount1.handleStopJump();
				}
				break;
			case OPEN_INVENTORY:
				if (this.player.getRidingEntity() instanceof AbstractHorse) {
					((AbstractHorse) this.player.getRidingEntity()).openGUI(this.player);
				}
				break;
			case START_FALL_FLYING:
				if (!this.player.onGround && this.player.motionY < 0.0D && !this.player.isElytraFlying() && !this.player.isInWater()) {
					ItemStack itemstack = this.player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
					if (itemstack.getItem() == Items.ELYTRA && ItemElytra.isUsable(itemstack)) {
						this.player.setElytraFlying();
					}
				} else {
					this.player.clearElytraFlying();
				}
				break;
			default:
				throw new IllegalArgumentException("Invalid client command!");
		}

	}

	public void processUseEntity(CPacketUseEntity packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		WorldServer worldserver = this.server.getWorld(this.player.dimension);
		Entity entity = packetIn.getEntityFromWorld(worldserver);
		this.player.markPlayerActive();
		if (entity != null) {
			boolean flag = this.player.canEntityBeSeen(entity);
			double d0 = 36.0D;
			if (!flag) {
				d0 = 9.0D;
			}

			if (this.player.getDistanceSq(entity) < d0) {
				EnumHand enumhand1;
				if (packetIn.getAction() == net.minecraft.network.play.client.CPacketUseEntity.Action.INTERACT) {
					enumhand1 = packetIn.getHand();
					this.player.interactOn(entity, enumhand1);
				} else if (packetIn.getAction() == net.minecraft.network.play.client.CPacketUseEntity.Action.INTERACT_AT) {
					enumhand1 = packetIn.getHand();
					if (ForgeHooks.onInteractEntityAt(this.player, entity, packetIn.getHitVec(), enumhand1) != null) {
						return;
					}

					entity.applyPlayerInteraction(this.player, packetIn.getHitVec(), enumhand1);
				} else if (packetIn.getAction() == net.minecraft.network.play.client.CPacketUseEntity.Action.ATTACK) {
					if (entity instanceof EntityItem || entity instanceof EntityXPOrb || entity instanceof EntityArrow || entity == this.player) {
						this.disconnect(new TextComponentTranslation("multiplayer.disconnect.invalid_entity_attacked"));
						this.server.logWarning("Player " + this.player.getName() + " tried to attack an invalid entity");
						return;
					}

					this.player.attackTargetEntityWithCurrentItem(entity);
				}
			}
		}

	}

	public void processClientStatus(CPacketClientStatus packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		this.player.markPlayerActive();
		State cpacketclientstatus$state = packetIn.getStatus();
		switch (cpacketclientstatus$state) {
			case PERFORM_RESPAWN:
				if (this.player.queuedEndExit) {
					this.player.queuedEndExit = false;
					this.player = this.server.getPlayerList().recreatePlayerEntity(this.player, 0, true);
					CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, DimensionType.THE_END, DimensionType.OVERWORLD);
				} else {
					if (this.player.getHealth() > 0.0F) {
						return;
					}

					this.player = this.server.getPlayerList().recreatePlayerEntity(this.player, this.player.dimension, false);
					if (this.server.isHardcore()) {
						this.player.setGameType(GameType.SPECTATOR);
						this.player.getServerWorld().getGameRules().setOrCreateGameRule("spectatorsGenerateChunks", "false");
					}
				}
				break;
			case REQUEST_STATS:
				this.player.getStatFile().sendStats(this.player);
		}

	}

	public void processCloseWindow(CPacketCloseWindow packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		this.player.closeContainer();
	}

	public void processClickWindow(CPacketClickWindow packetIn) {
		delegate.processClickWindow(packetIn);
	}

	public void func_194308_a(CPacketPlaceRecipe p_194308_1_) {}

	public void processEnchantItem(CPacketEnchantItem packetIn) {
		delegate.processEnchantItem(packetIn);
	}

	public void processCreativeInventoryAction(CPacketCreativeInventoryAction packetIn) {
		delegate.processCreativeInventoryAction(packetIn);
	}

	public void processConfirmTransaction(CPacketConfirmTransaction packetIn) {

	}

	public void processUpdateSign(CPacketUpdateSign packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		this.player.markPlayerActive();
		WorldServer worldserver = this.server.getWorld(this.player.dimension);
		BlockPos blockpos = packetIn.getPosition();
		if (worldserver.isBlockLoaded(blockpos)) {
			IBlockState iblockstate = worldserver.getBlockState(blockpos);
			TileEntity tileentity = worldserver.getTileEntity(blockpos);
			if (!(tileentity instanceof TileEntitySign)) {
				return;
			}

			TileEntitySign tileentitysign = (TileEntitySign) tileentity;
			if (!tileentitysign.getIsEditable() || tileentitysign.getPlayer() != this.player) {
				this.server.logWarning("Player " + this.player.getName() + " just tried to change non-editable sign");
				return;
			}

			String[] astring = packetIn.getLines();

			for (int i = 0; i < astring.length; ++i) {
				tileentitysign.signText[i] = new TextComponentString(TextFormatting.getTextWithoutFormattingCodes(astring[i]));
			}

			tileentitysign.markDirty();
			worldserver.notifyBlockUpdate(blockpos, iblockstate, iblockstate, 3);
		}
	}

	public void processKeepAlive(CPacketKeepAlive packetIn) {
		delegate.processKeepAlive(packetIn);
	}

	private long currentTimeMillis() {
		return System.nanoTime() / 1000000L;
	}

	public void processPlayerAbilities(CPacketPlayerAbilities packetIn) {
		delegate.processPlayerAbilities(packetIn);
	}

	public void processTabComplete(CPacketTabComplete packetIn) {
		delegate.processTabComplete(packetIn);
	}

	public void processClientSettings(CPacketClientSettings packetIn) {
		delegate.processClientSettings(packetIn);
	}

	public void processCustomPayload(CPacketCustomPayload packetIn) {
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.player.getServerWorld());
		String s = packetIn.getChannelName();
		PacketBuffer packetbuffer5;
		ItemStack itemstack3;
		ItemStack itemstack4;
		if ("MC|BEdit".equals(s)) {
			packetbuffer5 = packetIn.getBufferData();

			try {
				itemstack3 = packetbuffer5.readItemStack();
				if (itemstack3.isEmpty()) {
					return;
				}

				if (!ItemWritableBook.isNBTValid(itemstack3.getTagCompound())) {
					throw new IOException("Invalid book tag!");
				}

				itemstack4 = this.player.getHeldItemMainhand();
				if (itemstack4.isEmpty()) {
					return;
				}

				if (itemstack3.getItem() == Items.WRITABLE_BOOK && itemstack3.getItem() == itemstack4.getItem()) {
					itemstack4.setTagInfo("pages", itemstack3.getTagCompound().getTagList("pages", 8));
				}
			} catch (Exception var25) {
				LOGGER.error("Couldn't handle book info", var25);
			}
		} else {
			String s8;
			if ("MC|BSign".equals(s)) {
				packetbuffer5 = packetIn.getBufferData();

				try {
					itemstack3 = packetbuffer5.readItemStack();
					if (itemstack3.isEmpty()) {
						return;
					}

					if (!ItemWrittenBook.validBookTagContents(itemstack3.getTagCompound())) {
						throw new IOException("Invalid book tag!");
					}

					itemstack4 = this.player.getHeldItemMainhand();
					if (itemstack4.isEmpty()) {
						return;
					}

					if (itemstack3.getItem() == Items.WRITABLE_BOOK && itemstack4.getItem() == Items.WRITABLE_BOOK) {
						ItemStack itemstack2 = new ItemStack(Items.WRITTEN_BOOK);
						itemstack2.setTagInfo("author", new NBTTagString(this.player.getName()));
						itemstack2.setTagInfo("title", new NBTTagString(itemstack3.getTagCompound().getString("title")));
						NBTTagList nbttaglist = itemstack3.getTagCompound().getTagList("pages", 8);

						for (int i = 0; i < nbttaglist.tagCount(); ++i) {
							s8 = nbttaglist.getStringTagAt(i);
							ITextComponent itextcomponent = new TextComponentString(s8);
							s8 = ITextComponent.Serializer.componentToJson(itextcomponent);
							nbttaglist.set(i, new NBTTagString(s8));
						}

						itemstack2.setTagInfo("pages", nbttaglist);
						this.player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, itemstack2);
					}
				} catch (Exception var26) {
					LOGGER.error("Couldn't sign book", var26);
				}
			} else if ("MC|TrSel".equals(s)) {
				try {
					int k = packetIn.getBufferData().readInt();
					Container container = this.player.openContainer;
					if (container instanceof ContainerMerchant) {
						((ContainerMerchant) container).setCurrentRecipeIndex(k);
					}
				} catch (Exception var24) {
					LOGGER.error("Couldn't select trade", var24);
				}
			} else {
				TileEntity tileentity1;
				if ("MC|AdvCmd".equals(s)) {
					if (!this.server.isCommandBlockEnabled()) {
						this.player.sendMessage(new TextComponentTranslation("advMode.notEnabled"));
						return;
					}

					if (!this.player.canUseCommandBlock()) {
						this.player.sendMessage(new TextComponentTranslation("advMode.notAllowed"));
						return;
					}

					packetbuffer5 = packetIn.getBufferData();

					try {
						int l = packetbuffer5.readByte();
						CommandBlockBaseLogic commandblockbaselogic1 = null;
						if (l == 0) {
							tileentity1 = this.player.world.getTileEntity(new BlockPos(packetbuffer5.readInt(), packetbuffer5.readInt(), packetbuffer5.readInt()));
							if (tileentity1 instanceof TileEntityCommandBlock) {
								commandblockbaselogic1 = ((TileEntityCommandBlock) tileentity1).getCommandBlockLogic();
							}
						} else if (l == 1) {
							Entity entity = this.player.world.getEntityByID(packetbuffer5.readInt());
							if (entity instanceof EntityMinecartCommandBlock) {
								commandblockbaselogic1 = ((EntityMinecartCommandBlock) entity).getCommandBlockLogic();
							}
						}

						String s6 = packetbuffer5.readString(packetbuffer5.readableBytes());
						boolean flag2 = packetbuffer5.readBoolean();
						if (commandblockbaselogic1 != null) {
							commandblockbaselogic1.setCommand(s6);
							commandblockbaselogic1.setTrackOutput(flag2);
							if (!flag2) {
								commandblockbaselogic1.setLastOutput(null);
							}

							commandblockbaselogic1.updateCommand();
							this.player.sendMessage(new TextComponentTranslation("advMode.setCommand.success", s6));
						}
					} catch (Exception var23) {
						LOGGER.error("Couldn't set command block", var23);
					}
				} else if ("MC|AutoCmd".equals(s)) {
					if (!this.server.isCommandBlockEnabled()) {
						this.player.sendMessage(new TextComponentTranslation("advMode.notEnabled"));
						return;
					}

					if (!this.player.canUseCommandBlock()) {
						this.player.sendMessage(new TextComponentTranslation("advMode.notAllowed"));
						return;
					}

					packetbuffer5 = packetIn.getBufferData();

					try {
						CommandBlockBaseLogic commandblockbaselogic = null;
						TileEntityCommandBlock tileentitycommandblock = null;
						BlockPos blockpos1 = new BlockPos(packetbuffer5.readInt(), packetbuffer5.readInt(), packetbuffer5.readInt());
						TileEntity tileentity2 = this.player.world.getTileEntity(blockpos1);
						if (tileentity2 instanceof TileEntityCommandBlock) {
							tileentitycommandblock = (TileEntityCommandBlock) tileentity2;
							commandblockbaselogic = tileentitycommandblock.getCommandBlockLogic();
						}

						String s7 = packetbuffer5.readString(packetbuffer5.readableBytes());
						boolean flag3 = packetbuffer5.readBoolean();
						TileEntityCommandBlock.Mode tileentitycommandblock$mode = TileEntityCommandBlock.Mode.valueOf(packetbuffer5.readString(16));
						boolean flag = packetbuffer5.readBoolean();
						boolean flag1 = packetbuffer5.readBoolean();
						if (commandblockbaselogic != null) {
							EnumFacing enumfacing = this.player.world.getBlockState(blockpos1).getValue(BlockCommandBlock.FACING);
							switch (tileentitycommandblock$mode) {
								case SEQUENCE:
									IBlockState iblockstate3 = Blocks.CHAIN_COMMAND_BLOCK.getDefaultState();
									this.player.world.setBlockState(blockpos1, iblockstate3.withProperty(BlockCommandBlock.FACING, enumfacing).withProperty(BlockCommandBlock.CONDITIONAL, flag), 2);
									break;
								case AUTO:
									IBlockState iblockstate2 = Blocks.REPEATING_COMMAND_BLOCK.getDefaultState();
									this.player.world.setBlockState(blockpos1, iblockstate2.withProperty(BlockCommandBlock.FACING, enumfacing).withProperty(BlockCommandBlock.CONDITIONAL, flag), 2);
									break;
								case REDSTONE:
									IBlockState iblockstate = Blocks.COMMAND_BLOCK.getDefaultState();
									this.player.world.setBlockState(blockpos1, iblockstate.withProperty(BlockCommandBlock.FACING, enumfacing).withProperty(BlockCommandBlock.CONDITIONAL, flag), 2);
							}

							tileentity2.validate();
							this.player.world.setTileEntity(blockpos1, tileentity2);
							commandblockbaselogic.setCommand(s7);
							commandblockbaselogic.setTrackOutput(flag3);
							if (!flag3) {
								commandblockbaselogic.setLastOutput(null);
							}

							tileentitycommandblock.setAuto(flag1);
							commandblockbaselogic.updateCommand();
							if (!net.minecraft.util.StringUtils.isNullOrEmpty(s7)) {
								this.player.sendMessage(new TextComponentTranslation("advMode.setCommand.success", s7));
							}
						}
					} catch (Exception var22) {
						LOGGER.error("Couldn't set command block", var22);
					}
				} else {
					int j1;
					if ("MC|Beacon".equals(s)) {
						if (this.player.openContainer instanceof ContainerBeacon) {
							try {
								packetbuffer5 = packetIn.getBufferData();
								j1 = packetbuffer5.readInt();
								int k1 = packetbuffer5.readInt();
								ContainerBeacon containerbeacon = (ContainerBeacon) this.player.openContainer;
								Slot slot = containerbeacon.getSlot(0);
								if (slot.getHasStack()) {
									slot.decrStackSize(1);
									IInventory iinventory = containerbeacon.getTileEntity();
									iinventory.setField(1, j1);
									iinventory.setField(2, k1);
									iinventory.markDirty();
								}
							} catch (Exception var21) {
								LOGGER.error("Couldn't set beacon", var21);
							}
						}
					} else if ("MC|ItemName".equals(s)) {
						if (this.player.openContainer instanceof ContainerRepair) {
							ContainerRepair containerrepair = (ContainerRepair) this.player.openContainer;
							if (packetIn.getBufferData() != null && packetIn.getBufferData().readableBytes() >= 1) {
								String s5 = ChatAllowedCharacters.filterAllowedCharacters(packetIn.getBufferData().readString(32767));
								if (s5.length() <= 35) {
									containerrepair.updateItemName(s5);
								}
							} else {
								containerrepair.updateItemName("");
							}
						}
					} else if ("MC|Struct".equals(s)) {
						if (!this.player.canUseCommandBlock()) {
							return;
						}

						packetbuffer5 = packetIn.getBufferData();

						try {
							BlockPos blockpos = new BlockPos(packetbuffer5.readInt(), packetbuffer5.readInt(), packetbuffer5.readInt());
							IBlockState iblockstate1 = this.player.world.getBlockState(blockpos);
							tileentity1 = this.player.world.getTileEntity(blockpos);
							if (tileentity1 instanceof TileEntityStructure) {
								TileEntityStructure tileentitystructure = (TileEntityStructure) tileentity1;
								int l1 = packetbuffer5.readByte();
								s8 = packetbuffer5.readString(32);
								tileentitystructure.setMode(net.minecraft.tileentity.TileEntityStructure.Mode.valueOf(s8));
								tileentitystructure.setName(packetbuffer5.readString(64));
								int i2 = MathHelper.clamp(packetbuffer5.readInt(), -32, 32);
								int j2 = MathHelper.clamp(packetbuffer5.readInt(), -32, 32);
								int k2 = MathHelper.clamp(packetbuffer5.readInt(), -32, 32);
								tileentitystructure.setPosition(new BlockPos(i2, j2, k2));
								int l2 = MathHelper.clamp(packetbuffer5.readInt(), 0, 32);
								int i3 = MathHelper.clamp(packetbuffer5.readInt(), 0, 32);
								int j = MathHelper.clamp(packetbuffer5.readInt(), 0, 32);
								tileentitystructure.setSize(new BlockPos(l2, i3, j));
								String s2 = packetbuffer5.readString(32);
								tileentitystructure.setMirror(Mirror.valueOf(s2));
								String s3 = packetbuffer5.readString(32);
								tileentitystructure.setRotation(Rotation.valueOf(s3));
								tileentitystructure.setMetadata(packetbuffer5.readString(128));
								tileentitystructure.setIgnoresEntities(packetbuffer5.readBoolean());
								tileentitystructure.setShowAir(packetbuffer5.readBoolean());
								tileentitystructure.setShowBoundingBox(packetbuffer5.readBoolean());
								tileentitystructure.setIntegrity(MathHelper.clamp(packetbuffer5.readFloat(), 0.0F, 1.0F));
								tileentitystructure.setSeed(packetbuffer5.readVarLong());
								String s4 = tileentitystructure.getName();
								if (l1 == 2) {
									if (tileentitystructure.save()) {
										this.player.sendStatusMessage(new TextComponentTranslation("structure_block.save_success", s4), false);
									} else {
										this.player.sendStatusMessage(new TextComponentTranslation("structure_block.save_failure", s4), false);
									}
								} else if (l1 == 3) {
									if (!tileentitystructure.isStructureLoadable()) {
										this.player.sendStatusMessage(new TextComponentTranslation("structure_block.load_not_found", s4), false);
									} else if (tileentitystructure.load()) {
										this.player.sendStatusMessage(new TextComponentTranslation("structure_block.load_success", s4), false);
									} else {
										this.player.sendStatusMessage(new TextComponentTranslation("structure_block.load_prepare", s4), false);
									}
								} else if (l1 == 4) {
									if (tileentitystructure.detectSize()) {
										this.player.sendStatusMessage(new TextComponentTranslation("structure_block.size_success", s4), false);
									} else {
										this.player.sendStatusMessage(new TextComponentTranslation("structure_block.size_failure"), false);
									}
								}

								tileentitystructure.markDirty();
								this.player.world.notifyBlockUpdate(blockpos, iblockstate1, iblockstate1, 3);
							}
						} catch (Exception var20) {
							LOGGER.error("Couldn't set structure block", var20);
						}
					} else if ("MC|PickItem".equals(s)) {
						packetbuffer5 = packetIn.getBufferData();

						try {
							j1 = packetbuffer5.readVarInt();
							this.player.inventory.pickItem(j1);
							this.player.connection.sendPacket(new SPacketSetSlot(-2, this.player.inventory.currentItem, this.player.inventory.getStackInSlot1(this.player.inventory.currentItem)));
							this.player.connection.sendPacket(new SPacketSetSlot(-2, j1, this.player.inventory.getStackInSlot1(j1)));
							this.player.connection.sendPacket(new SPacketHeldItemChange(this.player.inventory.currentItem));
						} catch (Exception var19) {
							LOGGER.error("Couldn't pick item", var19);
						}
					}
				}
			}
		}
	}
}
