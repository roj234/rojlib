package ilib.entity;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2022/4/27 18:24
 */
public abstract class EntityNPCPlayer extends EntityCreature {
	public EntityNPCPlayer(World worldIn) {
		super(worldIn);
	}

	@Override
	public void onLivingUpdate() {
		super.onLivingUpdate();
	}

	@Override
	protected void dropFewItems(boolean wasRecentlyHit, int lootingModifier) {

	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();

		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0F);
	}

	@Override
	protected void initEntityAI() {
		this.tasks.addTask(1, new EntityAISwimming(this));
		// this.tasks.addTask(2, new EntityAIMoveTowardsTarget(this, 0.9D, 32.0F));
		// this.tasks.addTask(3, new EntityAIPanic(this, 0.5D));
		this.tasks.addTask(1, new EntityAIMoveIndoors(this));
		this.tasks.addTask(1, new EntityAIRestrictOpenDoor(this));
		this.tasks.addTask(1, new EntityAIOpenDoor(this, true));
		// this.tasks.addTask(2, new EntityAIAttackMelee(this, 0.55D, true));
		// this.tasks.addTask(2, new EntityAIMoveTowardsTarget(this, 0.55D, 32.0F));

		this.tasks.addTask(6, new EntityAIWanderAvoidWater(this, 0.4D));
		this.tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 10.0F));

		this.targetTasks.addTask(9, new EntityAIWatchClosest(this, EntityLiving.class, 10.0F));

		// this.targetTasks.addTask(2, new EntityAINearestAttackableTarget(this,
		// EntityZombie.class, true));
	}

	@Override
	protected boolean processInteract(EntityPlayer player, EnumHand hand) {
		if (!world.isRemote) {

		}
		return super.processInteract(player, hand);
	}
}