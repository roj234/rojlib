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
import ilib.ClientProxy;
import ilib.entity.EntityLightningBoltMI;
import ilib.util.BlockHelper.BresenhamLine;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
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
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.concurrent.Ref;
import roj.math.MathUtils;
import roj.math.Vec2d;
import roj.math.Vector;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class EntityHelper {
    public static final int RT_STOP_ON_LIQUID = 1, RT_RETURN_LAST_AIR = 2;

    /**
     * 射线追踪
     */
    public static RayTraceResult rayTraceBlock(IBlockAccess world, Vector begin, Vector end, int flag) {
        BresenhamLine itr = new BresenhamLine(begin.x(), begin.y(), begin.z(), end.x(), end.y(), end.z());

        while (itr.hasNext()) {
            BlockPos pos = itr.next();
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            if (!block.isAir(state, world, pos) && block.canCollideCheck(state, (flag & RT_STOP_ON_LIQUID) != 0)) {
                if ((block instanceof BlockLiquid || block instanceof IFluidBlock) && (flag & RT_STOP_ON_LIQUID) == 0) continue;

                AxisAlignedBB aabb = state.getBoundingBox(world, pos);
                if (aabb == Block.NULL_AABB) continue;
                Vec3d next = new Vec3d(itr.x - pos.getX(), itr.y - pos.getY(), itr.z - pos.getZ());
                Vec3d prev = next.subtract(itr.stepX, itr.stepY, itr.stepZ);
                RayTraceResult ic = aabb.calculateIntercept(prev, next);
                if (ic != null) {
                    if ((flag & RT_RETURN_LAST_AIR) != 0) {
                        return new RayTraceResult(Type.MISS, prev.subtract(itr.stepX, itr.stepY, itr.stepZ), null,
                        itr.pos.setPos(itr.x - itr.stepX, itr.y - itr.stepY, itr.z - itr.stepZ));
                    }
                    return new RayTraceResult(Type.BLOCK, ic.hitVec, ic.sideHit, pos);
                }
            }
        }
        return null;
    }

    public static Entity rayTraceEntity(World w, Vector begin, Vector end, Entity excludes) {
        List<Entity> entities = rayTraceEntity(w, begin, end, 0.4f, 1, Collections.singleton(excludes));
        return entities.isEmpty() ? null : entities.get(0);
    }

    public static List<Entity> rayTraceEntity(World w, Vector begin, Vector end, float range, int max, Collection<? extends Entity> excludes) {
        BresenhamLine itr = new BresenhamLine(begin.x(), begin.y(), begin.z(), end.x(), end.y(), end.z());

        AxisAlignedBB aabb = new AxisAlignedBB(itr.pos);
        for (FieldAccessor fa : AABB_FA) fa.setInstance(aabb);

        Chunk ch = null;
        List<Entity> list = new SimpleList<>();
        int size = 0;

        Predicate<Entity> p = (e) -> list.size() < max && !excludes.contains(e);
        while (itr.hasNext()) {
            BlockPos pos = itr.next();
            if (ch == null || pos.getX() >> 4 != ch.x || pos.getZ() >> 4 != ch.z) {
                ch = w.getChunk(pos);
            }

            grow0(pos, range);

            ch.getEntitiesOfTypeWithinAABB(Entity.class, aabb, list, p);
            if (list.size() >= max) return list;
        }
        return list;
    }

    public static List<Entity> getEntitiesInRange(Entity entity, double rx, double ry, double rz, @Nullable Predicate<? super Entity> filter) {
        AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(rx, ry, rz);
        return entity.world.getEntitiesInAABBexcluding(entity, aabb, filter);
    }

    /**
     * 获得能看得见目标方块的最近实体
     */
    @Nullable
    public static <T extends Entity> T getNearestWatching(World world, BlockPos pos, List<T> entities) {
        return getNearestWatching(world, new Vec3d(pos.getX(), pos.getY(), pos.getZ()), entities);
    }

    /**
     * 获得能看得见目标实体的最近实体
     */
    @Nullable
    public static <T extends Entity> T getNearestWatching(World world, Entity target, List<T> entities) {
        return getNearestWatching(world, target.getPositionVector(), entities);
    }

    /**
     * 获得能看得见目标位置的最近实体
     */
    @Nullable
    public static <T extends Entity> T getNearestWatching(World world, Vec3d pos, List<T> entities) {
        double minDist = Double.MAX_VALUE;
        T min = null;

        roj.math.Vec3d srcEye = new roj.math.Vec3d();
        roj.math.Vec3d ray = new roj.math.Vec3d();
        for (T src : entities) {
            srcEye.set(src.posX, src.posY + src.getEyeHeight(), src.posZ);

            ray.set(pos.x - srcEye.x, pos.y - srcEye.y, pos.z - srcEye.z);
            if (ray.len2() >= minDist) continue;

            roj.math.Vec3d dir = direction(src);

            double angle = Math.toDegrees(Math.acos(ray.angle(dir)));
            double fov_angle = FOV * Math.pow(ray.len(), -0.1);
            if (angle > fov_angle) continue;

            double l2 = ray.len2();
            if (null != rayTraceBlock(src.world, srcEye, ray.add(srcEye), 0) ||
                !rayTraceEntity(src.world, srcEye, ray, Math.max(src.width, src.height), 1, Collections.emptyList()).isEmpty()) continue;
            min = src;
            minDist = l2;
        }
        return min;
    }

    /**
     * 观察者是否能看得见目标
     */
    public static boolean canSee(EntityLivingBase entity, Entity target) {
        if (entity.world != target.world) return false;
        return canSee(entity, target.getEntityBoundingBox(), FOV, 64);
    }

    /**
     * 观察者是否在盯着目标的头看
     */
    public static boolean canSeeHead(EntityLivingBase entity, EntityLivingBase target) {
        if (entity.world != target.world) return false;
        // 盯着头,FOV设小一点
        return canSee(entity, vec(target).add(0, target.getEyeHeight(), 0), 4, 64);
    }

    public static boolean canPlayerSee(AxisAlignedBB box) {
        Minecraft mc = ClientProxy.mc;
        GameSettings set = mc.gameSettings;
        float fov = set.fovSetting;
        if (set.thirdPersonView == 1) {
            System.out.println("TPV = 1");
        } else if (set.thirdPersonView == 2) {
            System.out.println("TPV = 2");
        }
        return canSee(mc.player, box, fov, set.renderDistanceChunks << 4);
    }

    // 实体默认视野角度
    public static final float FOV = 70;

    public static boolean canSee(Entity entity, AxisAlignedBB box, float fov, float maxDist) {
        maxDist *= maxDist;

        roj.math.Vec3d srcEye = new roj.math.Vec3d(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);

        if (srcEye.x > box.minX && srcEye.x < box.maxY &&
            srcEye.y > box.minY && srcEye.y < box.maxY &&
            srcEye.z > box.minZ && srcEye.z < box.maxZ) return true;

        // 方位角
        roj.math.Vec3d dir = direction(entity);

        double sx = MathUtils.clamp(srcEye.x, box.minX, box.maxX),
               sy = MathUtils.clamp(srcEye.y, box.minY, box.maxY),
               sz = MathUtils.clamp(srcEye.z, box.minZ, box.maxZ);

        for (int i = 0; i < 7; i++) {
            double x = sx;
            double y = sy;
            double z = sz;

            // 六面最接近玩家的点, 和视线指向的角落
            switch (i) {
                case 0:
                    x = box.maxX;
                    break;
                case 1:
                    x = box.minX;
                    break;
                case 2:
                    y = box.maxY;
                    break;
                case 3:
                    y = box.minY;
                    break;
                case 4:
                    z = box.maxZ;
                    break;
                case 5:
                    z = box.minZ;
                    break;
                case 6:
                    x = dir.x < 0 ? box.minX : box.maxX;
                    y = dir.y < 0 ? box.minY : box.maxY;
                    z = dir.z < 0 ? box.minZ : box.maxZ;
                    break;
            }

            // roj.opengl.render.ArenaRenderer.INSTANCE.render(new roj.math.Vec3d(x, y, z), new roj.math.Vec3d(x, y, z), 1);
            if (entity.getDistanceSq(x, y, z) > maxDist) continue;

            // 原点射向目标的直线
            roj.math.Vec3d ray = new roj.math.Vec3d(x - srcEye.x + 0.5,
                                                    y - srcEye.y + 0.5,
                                                    z - srcEye.z + 0.5);

            // 视线与目标连线之间的夹角
            double angle = Math.toDegrees(Math.acos(ray.angle(dir)));
            // 投影方式下视线角度最大值
            double fov_angle = fov * Math.pow(ray.len(), -0.1);

            // 如果角度在视野内
            if (angle <= fov_angle) return true;
        }

        return false;
    }

    public static boolean canSee(Entity src, Vector pos, float fov, float maxDist) {
        // 检测无障碍能否看到
        maxDist *= maxDist;

        roj.math.Vec3d srcEye = new roj.math.Vec3d(src.posX, src.posY + src.getEyeHeight(), src.posZ);

        roj.math.Vec3d dir = direction(src);

        if (src.getDistanceSq(pos.x(), pos.y(), pos.z()) > maxDist) return false;

        roj.math.Vec3d ray = new roj.math.Vec3d(pos.x() - srcEye.x,
                                                pos.y() - srcEye.y,
                                                pos.z() - srcEye.z);

        double angle = Math.toDegrees(Math.acos(ray.angle(dir)));
        double fov_angle = fov * Math.pow(ray.len(), -0.1);

        if (angle > fov_angle) return false;

        // 检测有障碍能否看到
        return null == rayTraceBlock(src.world, srcEye, ray.add(srcEye), 0);
    }

    public static roj.math.Vec3d direction(Entity entity) {
        return new roj.math.Vec3d(Math.toRadians(entity.rotationYaw), Math.toRadians(entity.rotationPitch));
    }

    // 露出背部的最大角度
    public static final float ANGLE_BACK = 60;

    /**
     * 观察者在目标的身后
     *
     * @param entity 观察者
     * @param target 目标
     * @return boolean 是否在身后
     */
    public static boolean isBehind(Entity entity, Entity target) {
        // 不同世界无法判断
        if (entity.world != target.world) return false;
        return isInAngle(entity, target, ANGLE_BACK);
    }

    /**
     * b面对a的角度在多少呢
     */
    public static boolean isInAngle(Entity a, Entity b, double max) {
        roj.math.Vec3d ray = new roj.math.Vec3d(b.posX - a.posX,
                                                b.posY - a.posY,
                                                b.posZ - a.posZ);
        roj.math.Vec3d facing = direction(b);

        // 只考虑平面方向
        ray.y = 0;
        facing.y = 0;

        double angle = Math.toDegrees(Math.acos(ray.angle(facing)));
        System.out.println("Angle " + angle);

        // 若射线方向跟目标面向方向夹角小于露出背部的最大角度就认为是背对了
        // 这里我没有判断目标是否在观察者视野里，背对背也是存在的情况
        return angle < max;
    }

    private static final FieldAccessor[] AABB_FA;
    static {
        AABB_FA = new FieldAccessor[6];
        Field[] fields = AxisAlignedBB.class.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            AABB_FA[i] = ReflectionUtils.access(fields[i]);
        }
    }

    public static void grow0(BlockPos pos, double size) {
        AABB_FA[0].setDouble(pos.getX() - size);
        AABB_FA[1].setDouble(pos.getY() - size);
        AABB_FA[2].setDouble(pos.getZ() - size);
        AABB_FA[3].setDouble(pos.getX() + size);
        AABB_FA[4].setDouble(pos.getY() + size);
        AABB_FA[5].setDouble(pos.getZ() + size);
    }

    public static void grow0(BlockPos pos, double rx, double ry, double rz) {
        AABB_FA[0].setDouble(pos.getX() - rx);
        AABB_FA[1].setDouble(pos.getY() - ry);
        AABB_FA[2].setDouble(pos.getZ() - rz);
        AABB_FA[3].setDouble(pos.getX() + rx);
        AABB_FA[4].setDouble(pos.getY() + ry);
        AABB_FA[5].setDouble(pos.getZ() + rz);
    }



    public static void knockback(EntityLivingBase to, Entity from, float strength) {
        knockback(to, from, strength, MathHelper.sin(MathUtils.rad(from.rotationYaw)), -MathHelper.cos(MathUtils.rad(from.rotationYaw)));
    }

    public static void knockback(EntityLivingBase to, Entity from, float strength, double xRatio, double zRatio) {
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
     * 多结构实体解决方案
     */
    @Nullable
    public static Entity getMultipartRoot(Entity entity) {
        if (entity instanceof MultiPartEntityPart) {
            IEntityMultiPart parent = ((MultiPartEntityPart) entity).parent;
            if (parent instanceof Entity) {
                return (Entity) parent;
            }
            return null;
        }
        return entity;
    }

    public static void dismountEntity(EntityLivingBase rider, Entity riding) {
        if (!riding.isDead && rider.world.getBlockState(riding.getPosition()).getMaterial() != Material.PORTAL) {
            Vec3d pos = null;
            if (riding instanceof EntityBoat) pos = dismountBoat(rider, (EntityBoat) riding);
            else if (riding instanceof EntityMinecart) pos = dismountCart(rider, (EntityMinecart) riding);
            else if (riding instanceof EntityPig) pos = dismountPig(rider, (EntityPig) riding);

            if (pos != null) rider.setPositionAndUpdate(pos.x, pos.y, pos.z);
            else rider.setPositionAndUpdate(riding.posX, riding.getEntityBoundingBox().maxY, riding.posZ);
        } else {
            rider.setPositionAndUpdate(riding.posX, riding.posY + riding.height, riding.posZ);
        }
    }

    private static Vec3d dismountBoat(EntityLivingBase rider, EntityBoat boat) {
        Vec2d xz = xzOffset((boat.width * MathHelper.SQRT_2), rider.width, boat.rotationYaw);

        double x = boat.posX + xz.x;
        double z = boat.posZ + xz.y;

        BlockPos pos = new BlockPos(x, boat.getEntityBoundingBox().maxY, z);

        double y = yOffset(boat.world, pos);
        if (isValidY(y)) {
            Vec3d v = new Vec3d(x, pos.getY() + y, z);
            if (!rider.world.checkBlockCollision(rider.getEntityBoundingBox().offset(v)))
                return v;
        }

        pos = pos.down();

        y = yOffset(boat.world, pos);
        if (isValidY(y)) {
            Vec3d v = new Vec3d(x, pos.getY() + y, z);
            if (!rider.world.checkBlockCollision(rider.getEntityBoundingBox().offset(v)))
                return v;
        }

        return null;
    }

    private static Vec3d dismountCart(EntityLivingBase rider, EntityMinecart cart) {
        EnumFacing facing = cart.getAdjustedHorizontalFacing();
        if (facing.getAxis() == EnumFacing.Axis.Y) return null;

        BlockPos pos = cart.getPosition();
        BlockPos.PooledMutableBlockPos mutable = BlockPos.PooledMutableBlockPos.retain();

        Ref<Vec3d> ref = Ref.from();
        try {
            offsets(facing, (a, b) -> {
                mutable.setPos(pos.getX() + a, pos.getY() - 1, pos.getZ() + b);
                for (int i = 0; i < 3; i++) {
                    double y = yOffset(rider.world, mutable.move(EnumFacing.UP), state -> {
                        if (state.getBlock().isLadder(state, rider.world, pos, rider)) return true;
                        return (state.getBlock() instanceof BlockTrapDoor && state.getValue(BlockTrapDoor.OPEN));
                    });
                    if (isValidY(y)) {
                        Vec3d v = new Vec3d(mutable.getX() + 0.5D, mutable.getY() + y, mutable.getZ() + 0.5D);
                        if (!rider.world.checkBlockCollision(rider.getEntityBoundingBox().offset(v))) {
                            ref.set(v);
                            throw OperationDone.INSTANCE;
                        }
                    }
                }
            });
        } catch (OperationDone ignored) {}

        mutable.release();

        return ref.get();
    }

    private static Vec3d dismountPig(EntityLivingBase rider, EntityPig pig) {
        EnumFacing face = pig.getAdjustedHorizontalFacing();
        if (face.getAxis() == EnumFacing.Axis.Y) return null;

        BlockPos pos = pig.getPosition();
        BlockPos.PooledMutableBlockPos mutable = BlockPos.PooledMutableBlockPos.retain();

        Ref<Vec3d> ref = Ref.from();
        try {
            offsets(face, (a, b) -> {
                double y = yOffset(rider.world, mutable.setPos(pos.getX() + a, pos.getY(), pos.getZ() + b));
                if (isValidY(y)) {
                    Vec3d v = new Vec3d(mutable.getX() + 0.5D, mutable.getY() + y, mutable.getZ() + 0.5D);
                    if (!rider.world.checkBlockCollision(rider.getEntityBoundingBox().offset(v))) {
                        ref.set(v);
                        throw OperationDone.INSTANCE;
                    }
                }
            });
        } catch (OperationDone ignored) {}

        mutable.release();
        return ref.get();
    }

    private static Vec2d xzOffset(double ridingWidth, double riderWidth, float ridingYaw) {
        double averageWidth = (ridingWidth + riderWidth) / 2.0D;
        float sin = -MathHelper.sin((float) (ridingYaw * Math.PI / 180.0D));
        float cos = MathHelper.cos((float) (ridingYaw * Math.PI / 180.0D));
        float max = Math.max(Math.abs(sin), Math.abs(cos));
        return new Vec2d(sin * averageWidth / max, cos * averageWidth / max);
    }

    private static double yOffset(World world, BlockPos pos) {
        return yOffset(world, pos, state -> false);
    }

    private static double yOffset(World world, BlockPos pos1, Predicate<IBlockState> ignore) {
        IBlockState state = world.getBlockState(pos1);

        AxisAlignedBB box = ignore.test(state) ? null : state.getCollisionBoundingBox(world, pos1);
        if (box != null && box.maxY != 0.0D) return box.maxY;

        PooledMutableBlockPos pos = PooledMutableBlockPos.retain(pos1);
        state = world.getBlockState(pos.move(EnumFacing.DOWN));
        box = ignore.test(state) ? null : state.getCollisionBoundingBox(world, pos);
        pos.release();

        double maxY = (box == null) ? 0 : box.maxY;

        return (maxY >= 1) ? (maxY - 1) : Double.NEGATIVE_INFINITY;
    }

    private static boolean isValidY(double y) {
        return (!Double.isInfinite(y) && y < 1.0D);
    }

    private static void offsets(EnumFacing a, BiIntConsumer csm) {
        EnumFacing b = a.rotateY();
        EnumFacing c = b.getOpposite();
        EnumFacing d = a.getOpposite();

        csm.accept(b.getXOffset(), b.getZOffset());
        csm.accept(c.getXOffset(), c.getZOffset());
        csm.accept(d.getXOffset() + b.getXOffset(),
                d.getZOffset() + b.getZOffset());
        csm.accept(d.getXOffset() + c.getXOffset(),
                d.getZOffset() + c.getZOffset());
        csm.accept(a.getXOffset() + b.getXOffset(),
                a.getZOffset() + b.getZOffset());
        csm.accept(a.getXOffset() + c.getXOffset(),
                a.getZOffset() + c.getZOffset());
        csm.accept(d.getXOffset(), d.getZOffset());
        csm.accept(a.getXOffset(), a.getZOffset());
    }

    public static roj.math.Vec3d vec(Vec3d v) {
        return new roj.math.Vec3d(v.x, v.y, v.z);
    }

    public static roj.math.Vec3d vec(Entity v) {
        return new roj.math.Vec3d(v.posX, v.posY, v.posZ);
    }

    public static roj.math.Vec3i vec(Vec3i v) {
        return new roj.math.Vec3i(v.getX(), v.getY(), v.getZ());
    }

    private interface BiIntConsumer {
        void accept(int a, int b);
    }



    public static final Removed SOMEHOW = new Removed("unknown");
    public static final int REMOVE_LIGHTNING = 1, REMOVE_NOTIFY = 2, REMOVE_FORCE = 4;
    public static void remove(Entity entity, World world) {
        remove(entity, world, REMOVE_NOTIFY);
    }

    public static void remove(Entity entity, World world, int flag) {
        world = world == null ? entity.world : world;

        if ((flag & EntityHelper.REMOVE_NOTIFY) != 0) {
            entity.dismountRidingEntity();
            entity.removePassengers();
        }

        entity.setEntityInvulnerable(false);
        entity.setDropItemsWhenDead(false);

        if ((flag & EntityHelper.REMOVE_FORCE) != 0) {
            entity.setOutsideBorder(true);
        }

        if ((flag & EntityHelper.REMOVE_NOTIFY) != 0) {
            entity.setDead();
        }

        entity.isDead = true;

        if (entity instanceof EntityLivingBase) {
            EntityLivingBase e = (EntityLivingBase) entity;
            if ((flag & EntityHelper.REMOVE_NOTIFY) != 0) {
                e.clearActivePotions();
                e.setHealth(0.0f);

                if ((flag & EntityHelper.REMOVE_LIGHTNING) != 0) {
                    e.attackEntityFrom(SOMEHOW, Float.MAX_VALUE);
                    e.onDeath(SOMEHOW);
                    world.addWeatherEffect(new EntityLightningBoltMI(world, e.posX, e.posY, e.posZ));
                }
                e.setHealth(Float.MIN_VALUE);
            }
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                world.playerEntities.remove(player);
                final MinecraftServer server = world.getMinecraftServer();
                if(server != null && (flag & EntityHelper.REMOVE_FORCE) != 0) {
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
        Chunk chunk = world.getChunkProvider().getLoadedChunk(i, j);
        if(chunk != null) chunk.removeEntity(entity);
        entity.addedToChunk = false;

        world.loadedEntityList.remove(entity);

        if ((flag & EntityHelper.REMOVE_NOTIFY) != 0) {
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

        if ((flag & EntityHelper.REMOVE_FORCE) != 0) {
            entity.serverPosX = entity.serverPosY = entity.serverPosZ = 0;
            entity.world = null;
            entity.setEntityId(-1);
        }

        if ((flag & EntityHelper.REMOVE_NOTIFY) != 0) {
            try {
                entity.onUpdate();
            } catch (Exception ignored) {}
        }
    }

    public static DamageSource damage(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer)
            return DamageSource.causePlayerDamage((EntityPlayer) entity);
        else return DamageSource.causeMobDamage(entity);
    }

    private static final class Removed extends DamageSource {
        public Removed(String type) {
            super(type);
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



    public static NBTTagCompound getTagFromEntity(Entity entity) {
        NBTTagCompound tag = new NBTTagCompound();
        entity.writeToNBT(tag);
        tag.removeTag("Motion"); // clear motion
        tag.removeTag("HurtTime");
        tag.setString("id", getEntityId(entity)); // For mc

        return tag;
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

    public static String getEntityId(Entity entity) {
        EntityEntry entry = EntityRegistry.getEntry(entity.getClass());
        if (entry == null) return "";
        return entry.getRegistryName().toString();
    }
}
