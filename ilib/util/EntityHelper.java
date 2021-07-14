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

package ilib.util;

import com.google.common.base.Predicate;
import ilib.entity.EntityLightningBoltMI;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import roj.concurrent.Holder;
import roj.concurrent.OperationDone;
import roj.math.MathUtils;
import roj.math.Vec2d;
import roj.reflect.DirectConstructorAccess;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class EntityHelper {
    public static final InfinityDamageSource I_N_F_I_N_I_T_Y = new InfinityDamageSource("unknown");
    public static boolean CLEAR_PLAYER_DATA_WHEN_DEATH = false;

    public static NBTTagCompound getTagFromEntity(Entity entity) {
        NBTTagCompound entityTag = new NBTTagCompound();
        entity.writeToNBT(entityTag);
        entityTag.removeTag("Motion"); // clear motion 
        entityTag.removeTag("HurtTime");
        entityTag.setString("id", getEntityId(entity)); // For mc

        return entityTag;
    }

    public static Entity getEntityFromTag(NBTTagCompound tag, World world) {
        Entity entity = EntityList.createEntityFromNBT(tag, world);

        if (entity != null) {
            entity.setLocationAndAngles(0, 0, 0, 0, 0);
        }
        return entity;
    }

    public static Entity spawnEntityFromTag(NBTTagCompound tag, World world, double x, double y, double z) {
        Entity entity = getEntityFromTag(tag, world);

        if (entity != null) {
            entity.setLocationAndAngles(x, y, z, 0, 0);
            if (!world.isRemote) {
                world.spawnEntity(entity);
            }
        }
        return entity;
    }

    public static double getDistanceSq(Entity entity, Vec3d v) {
        double d0 = entity.posX - v.x;
        double d1 = entity.posY - v.y;
        double d2 = entity.posZ - v.z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    /**
     * 获得能看得见目标方块的最近实体
     *
     * @param world    world
     * @param entities list of entities
     * @param pos      position
     * @return entity
     */
    @Nullable
    public static <T extends Entity> T getNearestEntity(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull List<T> entities) {
        double minDistance = Double.MAX_VALUE;
        T nearest = null;
        Vec3d selfPos = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        for (T entity : entities) {
            RayTraceResult result = world.rayTraceBlocks(new Vec3d(entity.posX, entity.posY, entity.posZ), selfPos);
            if (result == null) {
                continue;
            } else if (result.typeOfHit != RayTraceResult.Type.ENTITY) {
                if (!result.getBlockPos().equals(pos))
                    continue;
            }
            double j = getDistanceSq(entity, selfPos);
            if (j < minDistance) {
                minDistance = j;
                nearest = entity;
            }
        }
        return nearest;
    }

    /**
     * 获得能看得见目标pos的最近实体
     *
     * @param world    world
     * @param entities list of entities
     * @param selfPos  vector
     * @return entity
     */
    @Nullable
    public static <T extends Entity> T getNearestEntity(@Nonnull World world, @Nonnull Vec3d selfPos, @Nonnull List<T> entities) {
        double minDistance = Double.MAX_VALUE;
        T nearest = null;
        for (T entity : entities) {
            RayTraceResult result = world.rayTraceBlocks(new Vec3d(entity.posX, entity.posY, entity.posZ), selfPos);
            if (result == null || result.typeOfHit != RayTraceResult.Type.ENTITY) {
                continue;
            }
            double j = getDistanceSq(entity, selfPos);
            if (j < minDistance) {
                minDistance = j;
                nearest = entity;
            }
        }
        return nearest;
    }

    /**
     * 获得entity眼睛能看得见的最近实体
     *
     * @param world    world
     * @param entities list of entities
     * @param entity   the entity
     * @return entity
     */
    @Nullable
    public static <T extends Entity> T getNearestEntity(@Nonnull World world, @Nonnull Entity entity, @Nonnull List<T> entities) {
        double minDistance = Double.MAX_VALUE;
        T nearest = null;

        Vec3d eyeVec = entity.getPositionEyes(1);

        double eyeYaw = Math.toRadians(entity.rotationYaw);
        double eyePitch = Math.toRadians(entity.rotationPitch);

        for (T target : entities) {
            // 当然了，不能穿墙
            Vec3d targetVec = target.getPositionVector();
            RayTraceResult result = world.rayTraceBlocks(targetVec, eyeVec);
            if (result == null || result.typeOfHit != RayTraceResult.Type.ENTITY) {
                continue;
            }

            Vec3d locationDifference = eyeVec.subtract(targetVec);

            double len = locationDifference.length();
            double yaw = Math.acos(locationDifference.y / len);
            double pitch = Math.atan2(-locationDifference.x, locationDifference.z);

            if (Math.abs(yaw - eyeYaw) < Math.PI / 36 && Math.abs(pitch - eyePitch) < 2 * Math.PI / 45) { // 看着
                if (len < minDistance) {
                    nearest = target;
                    minDistance = len;
                }
            }
        }
        return nearest;
    }

    /**
     * 射线追踪不是空气
     *
     * @param x1 x起始
     * @param y1 y起始
     * @param z1 z起始
     * @param x2 x结束
     * @param y2 y结束
     * @param z2 z结束
     */
    public static RayTraceResult myRayTrace(World world, double x1, double y1, double z1, double x2, double y2, double z2) {
        if (x1 != x1 || y1 != y1 || z1 != z1 || x2 != x2 || y2 != y2 || z2 != z2) return null;

        BlockHelper.BresenhamIterator iterator = new BlockHelper.BresenhamIterator(x1, y1, z1, x2, y2, z2);

        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            IBlockState state = world.getBlockState(pos);
            if (!state.getBlock().isAir(state, world, pos)) {
                AxisAlignedBB aabb = state.getBoundingBox(world, pos);
                Vec3d nextVec = new Vec3d(iterator.p_x, iterator.p_y, iterator.p_z);
                Vec3d currVec = nextVec.subtract(iterator.s_x, iterator.s_y, iterator.s_z);
                RayTraceResult result = aabb.calculateIntercept(currVec, nextVec);
                if (result != null)
                    return new RayTraceResult(RayTraceResult.Type.BLOCK, result.hitVec, result.sideHit, pos);
            }
        }
        return null;
    }

    public static List<Entity> getEntitiesInRange(@Nonnull Entity entity, double rangeX, double rangeY, double rangeZ, @Nullable Predicate<? super Entity> predicate) {
        AxisAlignedBB aabb = entity.getEntityBoundingBox();
        aabb = new AxisAlignedBB(aabb.minX - rangeX, aabb.minY - rangeY, aabb.minZ - rangeZ, aabb.maxX + rangeX, aabb.maxY + rangeY, aabb.maxZ + rangeZ);
        return entity.world.getEntitiesInAABBexcluding(entity, aabb, predicate);

    }

    public static DamageSource damage(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer)
            return DamageSource.causePlayerDamage((EntityPlayer) entity);
        else return DamageSource.causeMobDamage(entity);
    }

    public static boolean canEntitySee(World world, Vec3d startPos, Vec3d endPos) {
        RayTraceResult result = world.rayTraceBlocks(startPos, endPos);
        return result != null && result.typeOfHit == RayTraceResult.Type.ENTITY;
    }

    public static boolean canEntitySee(Entity entity, Vec3d startPos, Vec3d endPos) {
        return canEntitySee(entity.world, startPos, endPos);
    }

    public static String getEntityId(Entity entity) {
        EntityEntry entry = EntityRegistry.getEntry(entity.getClass());
        if (entry == null) {
            return "";
        }
        return entry.getRegistryName().toString();
    }

    // 视野阻碍判断的间隔
    public static final double SPACING = 0.2;

    // 视野最大角度
    public static final float FOV = 60;

    // 聚焦视野最大角度基数
    public static final float FOV_FOCUS_NORMAL = 25;

    // 露出背部的最大角度
    public static final float ANGLE_BACK = 60;

    /**
     * 观察者是否能看得见目标
     *
     * @param entity 观察者
     * @param target 目标
     * @return boolean 希望能看到我吧
     */
    public static boolean isTargetInSight(EntityLivingBase entity, Entity target) {
        // 不同世界无法判断
        if (entity.world != target.world) {
            return false;
        }

        // 获取目标位置
        Vec3d pos = new Vec3d(entity.posX, entity.posY, entity.posZ);
        Vec3d targetPos = new Vec3d(target.posX, target.posY, target.posZ);

        // 若目标非活物，这里只判断底部
        // 若目标是活物，则同时检查目标的头和脚以减小误差
        // 可以再加上几个身体的判断，服务器资源的消耗和判断的准确性自己把握
        return canEntitySee(entity, pos, targetPos) || (target instanceof EntityLivingBase && canEntitySee(entity, pos, targetPos.add(0, target.getEyeHeight(), 0)));
    }

    public static roj.math.Vec3d direction(Entity entity) {
        return new roj.math.Vec3d(Math.toRadians(entity.rotationYaw), Math.toRadians(entity.rotationPitch));
    }

    /**
     * 观察者是否在盯着目标的头看
     *
     * @param entity 观察者
     * @param target 目标
     * @return boolean 盯久了会害羞所以还是false比较好
     */
    public static boolean isLookingHead(EntityLivingBase entity, EntityLivingBase target) {
        // 不同世界无法判断
        if (entity.world != target.world) {
            return false;
        }

        // 获取观察者眼睛的位置
        Vec3d loc_entity_eye = new Vec3d(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
        // 获取观察者视线向量
        roj.math.Vec3d lookingVec = direction(entity);

        // TODO 获取目标头的位置, 大概没有眼睛不在头上的生物吧？！
        // 有的话就改改
        Vec3d loc_target_head = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);

        double distance = loc_entity_eye.distanceTo(loc_target_head);

        // 得到空间坐标中起点射向终点的向量
        Vec3d ray1 = loc_target_head.subtract(loc_entity_eye);
        roj.math.Vec3d ray = new roj.math.Vec3d(ray1.x, ray1.y, ray1.z);

        // 获取观察者视线向量与射线向量之间的夹角
        float angle = (float) Math.toDegrees(ray.angle(lookingVec));

        double fov_focus_fixed = FOV_FOCUS_NORMAL / distance;

        // 如果角度超出了聚焦视野最大角度
        if (angle > fov_focus_fixed) {
            // 就认为观察者没有盯着目标的头看
            return false;
        }

        // 判断目标是否能被观察者看见
        return isTargetInSight(entity, target);
    }

    /**
     * 观察者在目标的身后
     *
     * @param entity 观察者
     * @param target 目标
     * @return boolean 是否在身后
     */
    public static boolean isBehind(Entity entity, Entity target) {
        // 不同世界无法判断
        if (entity.world != target.world) {
            return false;
        }

        // 获取观察者位置
        Vec3d loc_entity = new Vec3d(entity.posX, entity.posY, entity.posZ);

        // 获取目标的位置
        Vec3d loc_target = new Vec3d(target.posX, target.posY, target.posZ);

        double distance = loc_entity.distanceTo(loc_target);

        // 和平面坐标系上 B点坐标减去A点坐标得到A->B 一样
        // 得到空间坐标中起点射向终点的向量
        Vec3d ray1 = loc_target.subtract(loc_entity);
        // 只考虑平面偏移
        roj.math.Vec3d ray = new roj.math.Vec3d(ray1.x, 0, ray1.z);

        // 获取目标正视的方向
        roj.math.Vec3d lookingVec_target = direction(target);

        // 只考虑平面方向
        lookingVec_target.y = 0;

        // 得到两向量夹角
        double angle = Math.toDegrees(ray.angle(lookingVec_target));

        // 若射线方向跟目标面向方向夹角小于露出背部的最大角度就认为是背对了
        // 这里我没有判断目标是否在观察者视野里，背对背也是存在的情况
        return angle < ANGLE_BACK;
    }

    public static void killIt(Entity entity, World world) {
        killIt(entity, world, false, true, false);
    }

    public static void killIt(Entity entity, World world, boolean lightning, boolean notify, boolean force) {
        world = world == null ? entity.world : world;

        if (notify) {
            entity.dismountRidingEntity();
            entity.removePassengers();
        }

        entity.setEntityInvulnerable(false);
        entity.setDropItemsWhenDead(lightning);

        if (force) {
            entity.setOutsideBorder(true);
        }

        if (notify) {
            entity.setDead();
        }

        entity.isDead = true;

        if (entity instanceof EntityLivingBase) {
            EntityLivingBase e = (EntityLivingBase) entity;
            if (notify) {
                e.clearActivePotions();
                e.setHealth(0.0f);

                if (lightning) {
                    e.attackEntityFrom(I_N_F_I_N_I_T_Y, Float.MAX_VALUE);
                    e.onDeath(I_N_F_I_N_I_T_Y);

                    EntityLightningBoltMI TJZY = new EntityLightningBoltMI(world, entity.posX, entity.posY, entity.posZ); // 天降正义!
                    world.addWeatherEffect(TJZY);
                }
                e.setHealth(Float.MIN_VALUE);
            }
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                world.playerEntities.remove(player);
                if (CLEAR_PLAYER_DATA_WHEN_DEATH) {
                    player.inventory.dropAllItems();
                    player.experienceLevel = 0;
                    player.experience = 0.0F;
                    player.experienceTotal = 0;
                }
                final MinecraftServer server = world.getMinecraftServer();
                if(server != null && force) {
                    final WorldServer worldServer = (WorldServer) world;
                    worldServer.getPlayerChunkMap().removePlayer((EntityPlayerMP) player);
                    final EntityTracker tracker = worldServer.getEntityTracker();
                    tracker.removePlayerFromTrackers((EntityPlayerMP) player);
                    tracker.untrack(player);
                }
                world.updateAllPlayersSleepingFlag();
            }
        }

        int i = entity.chunkCoordX;
        int j = entity.chunkCoordZ;
        //if (entity.addedToChunk) {
            final Chunk chunk = world.getChunkProvider().getLoadedChunk(i, j);
            if(chunk != null)
                chunk.removeEntity(entity);
        //}
        entity.addedToChunk = false;

        world.loadedEntityList.remove(entity);

        if (notify) {
            world.onEntityRemoved(entity);
        }

        entity.updateBlocked = true;
        entity.motionX = entity.motionY = entity.motionZ = 0;
        entity.lastTickPosX = entity.posX = entity.prevPosX = Double.NaN;
        entity.lastTickPosY = entity.posY = entity.prevPosY = Double.NaN;
        entity.lastTickPosZ = entity.posZ = entity.prevPosZ = Double.NaN;
        entity.noClip = true;
        entity.collided = entity.collidedVertically = entity.collidedHorizontally = false;
        entity.forceSpawn = false;
        entity.velocityChanged = true;

        if (force) {
            entity.serverPosX = entity.serverPosY = entity.serverPosZ = 0;
            entity.world = null;
            entity.setEntityId(-1);
        }

        if (notify) {
            try {
                entity.onUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * knocks the target back, simplified call of the other knockback function because I'm too lazy to type.
     */
    public static void knockBack(EntityLivingBase to, Entity from, float strength) {
        knockBack(to, from, strength, MathHelper.sin(MathUtils.rad(from.rotationYaw)), -MathHelper.cos(MathUtils.rad(from.rotationYaw)));
    }

    /**
     * knockback in EntityLivingBase except it makes sense and the resist is factored into the event
     */
    public static void knockBack(EntityLivingBase to, Entity from, float strength, double xRatio, double zRatio) {
        LivingKnockBackEvent event = ForgeHooks.onLivingKnockBack(to, from, strength * (float) (1 - to.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue()), xRatio, zRatio);
        if (event.isCanceled()) return;
        strength = event.getStrength();
        xRatio = event.getRatioX();
        zRatio = event.getRatioZ();
        if (strength != 0) {
            to.isAirBorne = true;
            float pythagoras = MathHelper.sqrt(xRatio * xRatio + zRatio * zRatio);
            to.motionX /= 2.0D;
            to.motionZ /= 2.0D;
            to.motionX -= xRatio / (double) pythagoras * (double) strength;
            to.motionZ -= zRatio / (double) pythagoras * (double) strength;

            if (to.onGround) {
                to.motionY /= 2.0D;
                to.motionY += strength;

                if (to.motionY > 0.4) {
                    to.motionY = 0.4;
                }
            }
        }
    }

    /**
     * 多实体结构解决方案
     *
     * @param entity The entity
     * @return The real entity
     */
    @Nullable
    public static Entity getRealEntity(@Nullable Entity entity) {
        if (entity == null)
            return null;
        if (entity instanceof MultiPartEntityPart) {
            IEntityMultiPart parent = ((MultiPartEntityPart) entity).parent;
            if (parent instanceof Entity) {
                return (Entity) parent;
            }
            return null;
        }
        return entity;
    }

    public static Function<World, ? extends Entity> createEntityFactory(Class<? extends Entity> cls) {
        return Helpers.cast(DirectConstructorAccess.get(Constructor.class, "apply0", cls));
    }

    public static void dismountEntity(EntityLivingBase rider, Entity riding) {
        if (!riding.isDead && rider.world.getBlockState(riding.getPosition()).getMaterial() != Material.PORTAL) {
            Vec3d pos = getDismountPosition(rider, riding);
            if (pos != null)
                rider.setPositionAndUpdate(pos.x, pos.y, pos.z);
            else
                dismountDefault(rider, riding);
        } else {
            rider.setPositionAndUpdate(riding.posX, riding.posY + riding.height, riding.posZ);
        }
    }

    public static void dismountDefault(EntityLivingBase rider, Entity riding) {
        rider.setPositionAndUpdate(riding.posX, riding.getEntityBoundingBox().maxY, riding.posZ);
    }

    public static Vec3d getDismountPosition(EntityLivingBase rider, Entity riding) {
        if (riding instanceof EntityBoat)
            return dismountBoat(rider, (EntityBoat) riding);
        if (riding instanceof EntityMinecart)
            return dismountCart(rider, (EntityMinecart) riding);
        if (riding instanceof EntityPig)
            return dismountPig(rider, (EntityPig) riding);
        return null;
    }

    public static Vec3d dismountBoat(EntityLivingBase rider, EntityBoat boat) {
        Vec2d xz = xzOffset((boat.width * MathHelper.SQRT_2), rider.width, boat.rotationYaw);

        double x = boat.posX + xz.x;
        double z = boat.posZ + xz.y;

        BlockPos pos = new BlockPos(x, (boat.getEntityBoundingBox()).maxY, z);

        double yPos = yOffset(boat.world, pos);
        if (isValidY(yPos)) {
            Vec3d v = new Vec3d(x, pos.getY() + yPos, z);
            if (!rider.world.checkBlockCollision(rider.getEntityBoundingBox().offset(v)))
                return v;
        }

        pos = pos.down();

        yPos = yOffset(boat.world, pos);
        if (isValidY(yPos)) {
            Vec3d v = new Vec3d(x, pos.getY() + yPos, z);
            if (!rider.world.checkBlockCollision(rider.getEntityBoundingBox().offset(v)))
                return v;
        }

        return null;
    }

    public static Vec3d dismountCart(EntityLivingBase rider, EntityMinecart minecart) {
        EnumFacing facing = minecart.getAdjustedHorizontalFacing();
        if (facing.getAxis() == EnumFacing.Axis.Y)
            return null;

        BlockPos pos = minecart.getPosition();

        BlockPos.PooledMutableBlockPos mutable = BlockPos.PooledMutableBlockPos.retain();

        Holder<Vec3d> holder = Holder.from(null);

        try {
            acceptOffsets(facing, (a, b) -> {
                mutable.setPos(pos.getX() + a, 0, pos.getZ() + b);
                for (int i = -1; i < 2; i++) {
                    mutable.setY(pos.getY() + i);

                    double y = yOffset(minecart.world, mutable, state -> {
                        if (state.getBlock().isLadder(state, minecart.world, pos, rider))
                            return true;
                        return (state.getBlock() instanceof BlockTrapDoor && state.getValue(BlockTrapDoor.OPEN));
                    });
                    if (isValidY(y)) {
                        Vec3d v = new Vec3d(mutable.getX() + 0.5D, mutable.getY() + y, mutable.getZ() + 0.5D);
                        if (!rider.world.checkBlockCollision(rider.getEntityBoundingBox().offset(v))) {
                            holder.set(v);
                            throw OperationDone.INSTANCE;
                        }
                    }
                }
            });
        } catch (OperationDone ignored) {
        }

        mutable.release();

        return holder.get();
    }

    public static Vec3d dismountPig(EntityLivingBase rider, EntityPig pig) {
        EnumFacing facing = pig.getAdjustedHorizontalFacing();
        if (facing.getAxis() == EnumFacing.Axis.Y)
            return null;

        BlockPos pos = pig.getPosition();

        BlockPos.PooledMutableBlockPos mutable = BlockPos.PooledMutableBlockPos.retain();

        Holder<Vec3d> holder = Holder.from(null);

        try {
            acceptOffsets(facing, (a, b) -> {
                double y = yOffset(pig.world, mutable.setPos(pos.getX() + a, pos.getY(), pos.getZ() + b));
                if (isValidY(y)) {
                    Vec3d v = new Vec3d(mutable.getX() + 0.5D, mutable.getY() + y, mutable.getZ() + 0.5D);
                    if (!rider.world.checkBlockCollision(rider.getEntityBoundingBox().offset(v))) {
                        holder.set(v);
                        throw OperationDone.INSTANCE;
                    }
                }
            });
        } catch (OperationDone ignored) {
        }

        mutable.release();

        return holder.get();
    }

    public static Vec2d xzOffset(double ridingWidth, double riderWidth, float ridingYaw) {
        double averageWidth = (ridingWidth + riderWidth) / 2.0D;
        float sin = -MathHelper.sin((float) (ridingYaw * Math.PI / 180.0D));
        float cos = MathHelper.cos((float) (ridingYaw * Math.PI / 180.0D));
        float max = Math.max(Math.abs(sin), Math.abs(cos));
        return new Vec2d(sin * averageWidth / max, cos * averageWidth / max);
    }

    public static double yOffset(World world, BlockPos pos) {
        return yOffset(world, pos, state -> false);
    }

    public static double yOffset(World world, BlockPos pos, Predicate<IBlockState> ignore) {
        IBlockState state = world.getBlockState(pos);

        AxisAlignedBB box = ignore.test(state) ? null : state.getCollisionBoundingBox(world, pos);
        if (box != null && box.maxY != 0.0D)
            return box.maxY;

        pos = pos.down();
        state = world.getBlockState(pos);
        box = ignore.test(state) ? null : state.getCollisionBoundingBox(world, pos);

        double maxY = (box == null) ? 0 : box.maxY;

        return (maxY >= 1) ? (maxY - 1) : Double.NEGATIVE_INFINITY;
    }

    public static boolean isValidY(double y) {
        return (!Double.isInfinite(y) && y < 1.0D);
    }

    public static void acceptOffsets(EnumFacing a, BiIntConsumer consumer) {
        EnumFacing b = a.rotateY();
        EnumFacing c = b.getOpposite();
        EnumFacing d = a.getOpposite();

        consumer.accept(b.getXOffset(), b.getZOffset());
        consumer.accept(c.getXOffset(), c.getZOffset());
        consumer.accept(d.getXOffset() + b.getXOffset(),
                d.getZOffset() + b.getZOffset());
        consumer.accept(d.getXOffset() + c.getXOffset(),
                d.getZOffset() + c.getZOffset());
        consumer.accept(a.getXOffset() + b.getXOffset(),
                a.getZOffset() + b.getZOffset());
        consumer.accept(a.getXOffset() + c.getXOffset(),
                a.getZOffset() + c.getZOffset());
        consumer.accept(d.getXOffset(), d.getZOffset());
        consumer.accept(a.getXOffset(), a.getZOffset());
    }

    public interface Constructor extends Function<Object, Object> {
        @Override
        default Object apply(Object o) {
            return apply0((World) o);
        }

        Entity apply0(World world);
    }

    private interface BiIntConsumer {
        void accept(int a, int b);
    }

    private static final class InfinityDamageSource extends DamageSource {
        public InfinityDamageSource(String damageType) {
            super(damageType);
            setMagicDamage();
            setDamageBypassesArmor();
            setDamageIsAbsolute();
            setDamageAllowedInCreativeMode();
        }

        @Override
        public ITextComponent getDeathMessage(EntityLivingBase entity) {
            String s = "death.mi.removed";
            return new TextComponentTranslation(s, entity.getName());
        }
    }
}
