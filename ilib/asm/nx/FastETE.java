package ilib.asm.nx;

import roj.asm.nixim.*;
import roj.collect.AbstractIterator;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.event.ForgeEventFactory;

import java.util.*;

/**
 * @author Roj233
 * @since 2022/5/25 1:09
 */
@Nixim("/")
class FastETE extends EntityTrackerEntry {
	@Shadow
	private Entity trackedEntity;
	@Shadow
	private double lastTrackedEntityMotionX;
	@Shadow
	private double lastTrackedEntityMotionY;
	@Shadow
	private double motionZ;
	@Shadow
	private boolean sendVelocityUpdates;
	@Shadow
	private List<Entity> passengers;
	@Shadow
	private final Set<EntityPlayerMP> trackingPlayers;
	@Copy(unique = true)
	private final AbstractIterator<EntityPlayerMP> itr;

	//@Inject(value = "<init>", at = Inject.At.INVOKE, occurrence = {"newHashSet", "redirect__newHashSet"})
	void repl(Entity entityIn, int rangeIn, int maxRangeIn, int updateFrequencyIn, boolean sendVelocityUpdatesIn) {}

	@Inject(value = "/", at = Inject.At.TAIL)
	FastETE(Entity entityIn, int rangeIn, int maxRangeIn, int updateFrequencyIn, boolean sendVelocityUpdatesIn) {
		super(null, 0, 0, 0, false);
		MyHashSet<EntityPlayerMP> s = new MyHashSet<>();
		trackingPlayers = s;
		itr = s.setItr();
	}

	@Copy(unique = true)
	private static HashSet<?> redirect__newHashSet() {
		return null;
	}

	public void match1() {
		List<Entity> list = trackedEntity.getPassengers();
		if (!list.equals(passengers)) {
			passengers = list;
			sendPacketToTrackedPlayers(new SPacketSetPassengers(trackedEntity));
		}
		NiximSystem.SpecMethods.$$$MATCH_END();
	}

	@Inject(value = "func_73122_a", at = Inject.At.MIDDLE, param = "match1")
	public void replace1(List<?> xxx) {
		List<Entity> list = trackedEntity.getPassengers();
		if (!list.equals(passengers)) {
			if (passengers == Collections.EMPTY_LIST) {passengers = new SimpleList<>();} else passengers.clear();
			passengers.addAll(list);
			sendPacketToTrackedPlayers(new SPacketSetPassengers(trackedEntity));
		}
		NiximSystem.SpecMethods.$$$MATCH_END();
	}

	@Inject("/")
	public void sendPacketToTrackedPlayers(Packet<?> packetIn) {
		AbstractIterator<EntityPlayerMP> itr = this.itr;
		itr.reset();

		while (itr.hasNext()) {
			itr.next().connection.sendPacket(packetIn);
		}
	}

	@Inject("/")
	public void sendDestroyEntityPacketToTrackedPlayers() {
		AbstractIterator<EntityPlayerMP> itr = this.itr;
		itr.reset();

		while (itr.hasNext()) {
			EntityPlayerMP player = itr.next();
			trackedEntity.removeTrackingPlayer(player);
			player.removeEntity(trackedEntity);
		}
	}

	@Inject("/")
	public void updatePlayerEntity(EntityPlayerMP p) {
		Entity E = trackedEntity;

		if (p == E) return;
		if (!isVisibleTo(p)) {
			if (trackingPlayers.remove(p)) {
				E.removeTrackingPlayer(p);
				p.removeEntity(E);
				ForgeEventFactory.onStopEntityTracking(E, p);
			}
		}

		if ((!isPlayerWatchingThisChunk(p) && !E.forceSpawn) || !trackingPlayers.add(p)) return;

		Packet<?> packet = createSpawnPacket();
		p.connection.sendPacket(packet);
		sendMetadata();
		if (!E.getDataManager().isEmpty()) {
			p.connection.sendPacket(new SPacketEntityMetadata(E.getEntityId(), E.getDataManager(), true));
		}

		boolean vup = sendVelocityUpdates;
		if (E instanceof EntityLivingBase) {
			EntityLivingBase elb = (EntityLivingBase) E;

			Collection<IAttributeInstance> attr = ((AttributeMap) elb.getAttributeMap()).getWatchedAttributes();
			if (!attr.isEmpty()) {
				p.connection.sendPacket(new SPacketEntityProperties(E.getEntityId(), attr));
			}

			if (elb.isElytraFlying()) {
				vup = true;
			}

			EntityEquipmentSlot[] var9 = EntityEquipmentSlot.values();
			for (EntityEquipmentSlot slot : var9) {
				ItemStack stack = elb.getItemStackFromSlot(slot);
				if (!stack.isEmpty()) {
					p.connection.sendPacket(new SPacketEntityEquipment(E.getEntityId(), slot, stack));
				}
			}

			Collection<PotionEffect> effects = elb.getActivePotionEffects();
			if (!effects.isEmpty()) {
				for (PotionEffect effect : effects) {
					p.connection.sendPacket(new SPacketEntityEffect(E.getEntityId(), effect));
				}
			}

			if (E instanceof EntityPlayer) {
				EntityPlayer player = (EntityPlayer) E;
				if (player.isPlayerSleeping()) {
					p.connection.sendPacket(new SPacketUseBed(player, new BlockPos(E)));
				}
			}
		}

		lastTrackedEntityMotionX = E.motionX;
		lastTrackedEntityMotionY = E.motionY;
		motionZ = E.motionZ;

		if (vup && !(packet instanceof SPacketSpawnMob)) {
			p.connection.sendPacket(new SPacketEntityVelocity(E.getEntityId(), E.motionX, E.motionY, E.motionZ));
		}

		if (!E.getPassengers().isEmpty()) {
			p.connection.sendPacket(new SPacketSetPassengers(E));
		}

		if (E.isRiding()) {
			p.connection.sendPacket(new SPacketSetPassengers(E.getRidingEntity()));
		}

		E.addTrackingPlayer(p);
		p.addEntity(E);
		ForgeEventFactory.onStartEntityTracking(E, p);
	}

	@Shadow
	private void sendMetadata() {}

	@Shadow
	private boolean isPlayerWatchingThisChunk(EntityPlayerMP playerMP) {
		return false;
	}
}
