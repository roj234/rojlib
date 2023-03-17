package ilib.entity.utill;

import ilib.util.EntityHelper;
import ilib.util.PlayerUtil;
import roj.reflect.ReflectionUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * @author Roj234
 * @since 2021/5/2 8:27
 */
public class ControllHelper {
	private final EntityLiving entity;

	private Entity attackingTarget;
	private boolean using = false;
	private byte preyX, prevY, prevZ;
	private Path path;
	private byte way;

	public ControllHelper(EntityLiving entity) {
		this.entity = entity;

		// destroy ai
		entity.tasks.taskEntries.clear();
		entity.targetTasks.taskEntries.clear();
	}

	public void registerAiTask(PlayerController ai) {
		entity.tasks.addTask(0, new InjectedGoal(ai));
	}

	public void scheduleMoveForward() {
		way |= 3;
	}

	public void scheduleMoveBackward() {
		way |= 1;
		way &= ~2;
	}

	public void scheduleMoveLeft() {
		way |= 12;
	}

	public void scheduleMoveRight() {
		way |= 4;
		way &= ~8;
	}

	public void cancelMoveForward() {
		way |= 16;
	}

	public void cancelMoveLeft() {
		way |= 32;
	}

	public void scheduleJump() {
		entity.setJumping(true);
	}

	public void cancelJump() {
		entity.setJumping(false);
	}

	public void scheduleSneak() {
		entity.dismountRidingEntity();
	}

	public void update() {
		if (attackingTarget != null) {
			System.out.println("attack");
			entity.attackEntityAsMob(attackingTarget);
			attackingTarget = null;
		}

		byte x = preyX;
		byte y = prevY;
		byte z = prevZ;

		if (way != 0) {
			if ((way & 1) != 0) {
				System.out.println((way & 2) != 0 ? "moveForward" : "moveBackward");
				x = (byte) ((way & 2) != 0 ? 1 : -1);
			}
			if ((way & 4) != 0) {
				System.out.println((way & 8) != 0 ? "moveLeft" : "moveRight");
				z = (byte) ((way & 8) != 0 ? 1 : -1);
			}

			if ((way & 16) != 0) {
				x = 0;
			}
			if ((way & 32) != 0) {
				z = 0;
			}

			way = 0;

			//if(this.path != null) {
			if (x == 0 && /* y == 0 && */z == 0) {
				System.out.println("cancelMove");
				entity.getNavigator().clearPath();
				//cancelled = true;
			} else {
				//}
				//if ((preyX != x && /*prevY != y &&*/ prevZ != z) || (this.path != null && this.path.hasRemaining())) {
				path = entity.getNavigator().getPathToXYZ(entity.posX + x, entity.posY + y, entity.posZ + z);
				entity.getNavigator().setPath(path, entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue());
				//}
			}

			preyX = x;
			prevY = y;
			prevZ = z;
		}

		if (this.entity.isHandActive()) {
			if (!using) {
				System.out.println("cancelUse");
				this.entity.resetActiveHand();
			} else {
				int duration = this.entity.getItemInUseMaxCount();
				if (duration >= 20) {
					this.entity.resetActiveHand();
					attackEntityWithRangedAttack(ItemBow.getArrowVelocity(20));
					System.out.println("use");
					this.using = false;
				}
			}
		}
	}

	public void attackEntityWithRangedAttack(float velocity) {
		EntityTippedArrow arrow = new EntityTippedArrow(entity.world, entity);
		arrow.setEnchantmentEffectsFromEntity(entity, velocity);

		Vec3d direction = entity.getLookVec();

		arrow.shoot(direction.x, direction.y, direction.z, 1.6F * velocity, (float) (14 - entity.world.getDifficulty().getId() * 4));
		entity.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (entity.getRNG().nextFloat() * 0.4F + 0.8F));
		entity.world.spawnEntity(arrow);
	}

	public void scheduleAttack() {
		if (entity instanceof EntityCreeper) {
			return;
		}

		if (attackingTarget != null) {
			return;
		}

		Vec3d eye = entity.getPositionEyes(1);
		Vec3d direction = entity.getLookVec();

		DamageSource source = DamageSource.causeMobDamage(entity);

		// find entity on eyes.
		Entity target = EntityHelper.getNearestWatching(entity.world, entity, EntityHelper.getEntitiesInRange(entity, 3, 2, 3, (entity) -> entity.isEntityInvulnerable(source)));

		if (target == null) return;

		// okay, calculate the blocks.
		RayTraceResult result = EntityHelper.rayTraceBlock(entity.world, new roj.math.Vec3d(entity.posX + direction.x, entity.posY + direction.y, entity.posZ + direction.z),
														   new roj.math.Vec3d(entity.posX + direction.x * 4, entity.posY + direction.y * 4, entity.posZ + direction.z * 4), 0);
		PlayerUtil.broadcastAll(result == null ? "~null~" : result.toString());

		if (result != null && entity.getDistanceSq(result.getBlockPos()) > entity.getDistanceSq(target)) {
			attackingTarget = null;
		}
	}

	public void cancelAttack() {
		if (attackingTarget != null) {
			System.out.println("cancelAttack");
			attackingTarget = null;
		}
	}

	public void scheduleUse() {
		if (using) {
			return;
		}

		Iterable<ItemStack> handInv = entity.getHeldEquipment();
		for (ItemStack stack : handInv) {
			if (!stack.isEmpty()) {
				//if(stack.getItem() == Items.BOW) {
				//
				//}
				PlayerUtil.broadcastAll("MobHeldStack: " + stack);
				if (!this.entity.isHandActive()) {
					this.entity.setActiveHand(EnumHand.MAIN_HAND);
				}
				using = true;
			}
		}
	}

	public void cancelUse() {
		if (using) {
			using = false;
		}
	}

	public void scheduleFuse() {
		if (!(entity instanceof EntityCreeper)) {
			return;
		}
		((EntityCreeper) entity).ignite();
	}

	public void scheduleTurn(double yaw, double pitch) {
		entity.rotationYaw = (float) yaw;
		entity.rotationPitch = (float) pitch;
		entity.rotationPitch = MathHelper.clamp(entity.rotationPitch, -90.0F, 90.0F);
		entity.prevRotationPitch = entity.rotationPitch;
		entity.prevRotationYaw = entity.rotationYaw;
		if (entity.getRidingEntity() != null) {
			entity.getRidingEntity().applyOrientationToEntity(entity);
		}
	}

	static DataParameter<Boolean> IGNITED;

	@SuppressWarnings("unchecked")
	public void cancelFuse() {
		if (!(entity instanceof EntityCreeper)) {
			return;
		}
		if (IGNITED == null) {
			try {
				IGNITED = (DataParameter<Boolean>) ReflectionUtils.access(EntityCreeper.class.getDeclaredField("field_184715_c")).getObject(null);
			} catch (NoSuchFieldException e) {
				e.printStackTrace();
			}
		}

		entity.getDataManager().set(IGNITED, false);
	}

	private static class InjectedGoal extends EntityAIBase {
		private final PlayerController ai;

		public InjectedGoal(PlayerController ai) {
			this.ai = ai;
		}

		@Override
		public boolean shouldExecute() {
			return ai.enabled;
		}

		@Override
		public boolean shouldContinueExecuting() {
			return ai.enabled;
		}

		@Override
		public void startExecuting() {
			ai.startExecuting();
		}

		@Override
		public void resetTask() {
			ai.resetTask();
		}

		@Override
		public void updateTask() {
			ai.updateTask();
		}
	}
}
