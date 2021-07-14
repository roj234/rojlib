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
package ilib.client.renderer.mirror.portal;

import ilib.client.renderer.mirror.ClientEventHandler;
import ilib.client.renderer.mirror.EventHandler;
import ilib.client.renderer.mirror.MirrorSubSystem;
import ilib.client.renderer.mirror.PktEntityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRain;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import roj.collect.MyHashSet;
import roj.collect.ToIntMap;
import roj.util.Helpers;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static ilib.ClientProxy.mc;

public abstract class Portal {
    protected AxisAlignedBB scanEntitiesIn;

    public AxisAlignedBB box;
    public World world;
    public int time;
    public Set<Entity> lastScanEntities = new MyHashSet<>();
    public ToIntMap<Entity> tpCD = new ToIntMap<>();
    public boolean renderAll = false;
    private EnumFacing faceOn;
    private EnumFacing upDir; //Upwards direction of the portal.
    private QuaternionFormula formula; //used to calculate the rotation and the positional offset (as well as motion)
    private AxisAlignedBB plane;
    private AxisAlignedBB flatPlane; //AABB that defines the plane where the magic happens.
    private float width;
    private float height;
    private Vec3d position;
    private BlockPos posBlock;
    private Set<AxisAlignedBB> collisions;
    private Portal pair;

    private boolean firstUpdate = true;

    public Portal(World world) {
        this.world = world;
        this.position = Vec3d.ZERO;
        this.posBlock = BlockPos.ORIGIN;

        this.faceOn = EnumFacing.NORTH;
        this.upDir = EnumFacing.UP;
    }

    public Portal(World world, Vec3d position, EnumFacing faceOn, EnumFacing upDir, float width, float height) {
        this.world = world;
        this.position = position;
        this.posBlock = new BlockPos(this.position);

        this.faceOn = faceOn;
        this.upDir = upDir;

        this.width = width;
        this.height = height;

        this.setupAABBs();
    }

    public abstract float getPlaneOffset();

    public abstract boolean canCollideWithBorders();

    public abstract void drawPlane(float partialTick);

    public void setFace(EnumFacing faceOut, EnumFacing upDir) {
        this.faceOn = faceOut;
        this.upDir = upDir;
        setupAABBs();
    }

    public EnumFacing getFaceOn() {
        return faceOn;
    }

    public EnumFacing getUpDir() {
        return upDir;
    }

    public BlockPos getPos() {
        return posBlock;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        setupAABBs();
    }

    public float getScanDistance() {
        return 3F;
    }

    public void updateWorldPortal() {
        if (firstUpdate) {
            firstUpdate = false;
        }
        time++;

        if (!canTeleportEntities()) {
            return;
        }

        Iterator<ToIntMap.Entry<Entity>> itr = Helpers.cast(tpCD.entrySet().iterator());
        while (itr.hasNext()) {
            ToIntMap.Entry<Entity> e = itr.next();
            if (e.v-- <= 0) {
                EventHandler.removeMonitoredEntity(e.getKey(), this);
                itr.remove();
            }
        }

        if (!hasPair()) {
            return;
        }

        EnumFacing faceOn = getFaceOn();
        List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, scanEntitiesIn);
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);

            if (isAgainstWall()) {
                EventHandler.addMonitoredEntity(entity, this);
            }

            if (!canEntityTeleport(entity)) {
                entities.remove(i);
                continue;
            }

            if (tpCD.containsKey(entity) || entity instanceof EntityPlayerMP && !entity.getEntityWorld().isRemote) {
                continue;
            }

            double[] motions = new double[3];//EntityHelper.simulateMoveEntity(entity, entity.motionX, entity.motionY, entity.motionZ);

            Vec3d newPos = new Vec3d(entity.posX + motions[0], entity.posY + entity.getEyeHeight() + motions[1], entity.posZ + motions[2]);

            boolean doTP = false;

            AxisAlignedBB teleportPlane = flatPlane;
            float offset = 0.0F; //should I test player width specifically?
            if (isAgainstWall() && entity instanceof EntityPlayer) {
                offset = Math.min(0.05F, (float) Math.abs((flatPlane.minX - entity.posX) * faceOn.getXOffset() + (flatPlane.minY - entity.posY) * faceOn.getYOffset() + (flatPlane.minZ - entity.posZ) * faceOn.getZOffset()));
                if (!scanEntitiesIn.offset(faceOn.getXOffset() * offset, faceOn.getYOffset() * offset, faceOn.getZOffset() * offset).contains(newPos) &&
                        box.offset(faceOn.getXOffset() * offset, faceOn.getYOffset() * offset, faceOn.getZOffset() * offset).contains(newPos) &&
                        (faceOn.getAxis().isHorizontal() && entity.getEntityBoundingBox().minY >= flatPlane.minY && entity.getEntityBoundingBox().maxY <= flatPlane.maxY || faceOn.getAxis().isVertical() && entity.getEntityBoundingBox().minX >= flatPlane.minX && entity.getEntityBoundingBox().maxX <= flatPlane.maxX && entity.getEntityBoundingBox().minZ >= flatPlane.minZ && entity.getEntityBoundingBox().maxZ <= flatPlane.maxZ) // special casing cause of pushOutOfBlocks for player
                ) {
                    teleportPlane = getTeleportPlane(offset);
                    doTP = true;
                }
            } else {
                if (!scanEntitiesIn.contains(newPos) && box.contains(newPos)) {
                    doTP = true;
                }
            }

            if (doTP) {
                double centerX = (teleportPlane.maxX + teleportPlane.minX) / 2D;
                double centerY = (teleportPlane.maxY + teleportPlane.minY) / 2D;
                double centerZ = (teleportPlane.maxZ + teleportPlane.minZ) / 2D;

                if (pair != null) {
                    float[] appliedOffset = getFormula().calcPosRot(new float[]{(float) (newPos.x - centerX), (float) (newPos.y - centerY), (float) (newPos.z - centerZ)});
                    float[] appliedMotion = getFormula().calcPosRot(new float[]{(float) motions[0], (float) motions[1], (float) motions[2]});
                    float[] appliedRotation = getFormula().calcRotRot(new float[]{entity.rotationYaw, entity.rotationPitch, entity.getEntityWorld().isRemote ? getRoll(entity) : 0F});

                    AxisAlignedBB pairTeleportPlane = pair.getTeleportPlane(offset);

                    double destX = (pairTeleportPlane.maxX + pairTeleportPlane.minX) / 2D;
                    double destY = (pairTeleportPlane.maxY + pairTeleportPlane.minY) / 2D;
                    double destZ = (pairTeleportPlane.maxZ + pairTeleportPlane.minZ) / 2D;

                    EntityTranStack ets = new EntityTranStack(entity);
                    ets.translate(destX - entity.posX + appliedOffset[0], destY - (entity.posY + entity.getEyeHeight()) + appliedOffset[1], destZ - entity.posZ + appliedOffset[2]); //go to the centre of the dest portal and offset with the fields
                    ets.rotate(appliedRotation[0], appliedRotation[1], appliedRotation[2]);

                    entity.setPosition(entity.posX, entity.posY, entity.posZ);
                    double maxWidthHeight = Math.max(entity.width, entity.height);
                    //EntityHelper.putEntityWithinAABB(entity, pair.scanRange.expand(pair.getFaceOn().getXOffset() * -maxWidthHeight, pair.getFaceOn().getYOffset() * -maxWidthHeight, pair.getFaceOn().getZOffset() * -maxWidthHeight));

                    entity.motionX = appliedMotion[0];
                    entity.motionY = appliedMotion[1];
                    entity.motionZ = appliedMotion[2];

                    //no going faster than 1 block a tick
                    if (Math.abs(entity.motionX) > 0.99D) {
                        entity.motionX /= Math.abs(entity.motionX) + 0.001D;
                    }
                    if (Math.abs(entity.motionY) > 0.99D) {
                        entity.motionY /= Math.abs(entity.motionY) + 0.001D;
                    }
                    if (Math.abs(entity.motionZ) > 0.99D) {
                        entity.motionZ /= Math.abs(entity.motionZ) + 0.001D;
                    }
                    entity.fallDistance = 0.1F * ((float) entity.motionY / -0.1F * (float) entity.motionY / -0.1F);
                    entity.setPosition(entity.posX, entity.posY, entity.posZ);

                    //transfer over this entity to the other portal.
                    pair.tpCD.put(entity, 3);
                    pair.lastScanEntities.add(entity);
                    if (pair.isAgainstWall()) {
                        EventHandler.addMonitoredEntity(entity, pair);
                    }
                    tpCD.put(entity, 3);
                    lastScanEntities.remove(entity);
                    //                    if(isAgainstWall()) //now removed by the teleport cooldown
                    //                    {
                    //                        WorldPortals.eventHandler.removeMonitoredEntity(entity, this);
                    //                    }

                    handleSpecialEntities(entity);

                    if (entity.getEntityWorld().isRemote) {
                        handleClientEntityTeleport(entity, appliedRotation);
                    } else {
                        MirrorSubSystem.PORTAL.sendToAllTracking(new PktEntityData(entity), entity);
                    }
                }
            }
        }

        if (world.isRemote) {
            handleClient();
        }

        if (isAgainstWall()) {
            lastScanEntities.removeAll(entities); // now contains entities that are out of the range. Remove this from the tracking.
            for (Entity entity : lastScanEntities) {
                if (!tpCD.containsKey(entity)) {
                    EventHandler.removeMonitoredEntity(entity, this);
                }
            }

            lastScanEntities.clear();
            lastScanEntities.addAll(entities);
        }
    }

    public float getRoll(Entity entity) {
        if (entity == Minecraft.getMinecraft().getRenderViewEntity()) {
            return ClientEventHandler.cameraRoll;
        }
        return 0F;
    }

    public boolean canEntityTeleport(Entity entity) {
        return true;
    }

    public void handleSpecialEntities(Entity entity) {
        if (entity instanceof EntityFallingBlock) {
            ((EntityFallingBlock) entity).fallTime = 2;
        } else if (entity instanceof EntityFireball) {
            EntityFireball fireball = (EntityFireball) entity;
            float[] appliedAcceleration = getFormula().calcPosRot(new float[]{(float) fireball.accelerationX, (float) fireball.accelerationY, (float) fireball.accelerationZ});
            fireball.accelerationX = appliedAcceleration[0];
            fireball.accelerationY = appliedAcceleration[1];
            fireball.accelerationZ = appliedAcceleration[2];
        } else if (entity instanceof EntityArrow) {
            ((EntityArrow) entity).inGround = false;
        }
    }

    public void handleClient() {
        //TODO a config for this?
        EnumFacing faceOn = getFaceOn();
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 2; ++j) {
                for (Particle p : mc.effectRenderer.fxLayers[i][j]) {
                    Vec3d particlePos = new Vec3d(p.prevPosX, p.prevPosY, p.prevPosZ); //motion isn't accessible.
                    Vec3d newParticlePos = new Vec3d(p.posX, p.posY, p.posZ);

                    float offset = (float) Math.abs((p.prevPosX - p.posX) * faceOn.getXOffset() * 1.5D + (p.prevPosY - p.posY) * faceOn.getYOffset() * 1.5D + (p.prevPosZ - p.posZ) * faceOn.getZOffset() * 1.5D);
                    boolean isRain = p instanceof ParticleRain && faceOn == EnumFacing.UP && scanEntitiesIn.contains(particlePos);
                    if (isRain || !box.offset(faceOn.getXOffset() * offset, faceOn.getYOffset() * offset, faceOn.getZOffset() * offset).intersects(p.getBoundingBox()) && box.offset(faceOn.getXOffset() * offset, faceOn.getYOffset() * offset, faceOn.getZOffset() * offset).intersects(p.getBoundingBox().offset(p.motionX, p.motionY, p.motionZ))) {
                        AxisAlignedBB teleportPlane = getTeleportPlane(offset);

                        double centerX = (teleportPlane.maxX + teleportPlane.minX) / 2D;
                        double centerY = (teleportPlane.maxY + teleportPlane.minY) / 2D;
                        double centerZ = (teleportPlane.maxZ + teleportPlane.minZ) / 2D;

                        if (pair != null) {
                            float[] appliedOffset = getFormula().calcPosRot(new float[]{(float) (newParticlePos.x - centerX), (float) (newParticlePos.y - centerY), (float) (newParticlePos.z - centerZ)});
                            float[] appliedMotion = getFormula().calcPosRot(new float[]{(float) (newParticlePos.x - particlePos.x), (float) (newParticlePos.y - particlePos.y), (float) (newParticlePos.z - particlePos.z)});

                            AxisAlignedBB pairTeleportPlane = pair.getTeleportPlane(offset);

                            double destX = (pairTeleportPlane.maxX + pairTeleportPlane.minX) / 2D;
                            double destY = (pairTeleportPlane.maxY + pairTeleportPlane.minY) / 2D;
                            double destZ = (pairTeleportPlane.maxZ + pairTeleportPlane.minZ) / 2D;

                            double x = destX - p.posX + appliedOffset[0];
                            double y = destY - p.posY + appliedOffset[1];
                            double z = destZ - p.posZ + appliedOffset[2];
                            p.posX += x;
                            p.posY += y;
                            p.posZ += z;
                            p.prevPosX += x;
                            p.prevPosY += y;
                            p.prevPosZ += z;
                            p.setPosition(p.posX, p.posY, p.posZ);
                            p.motionX = appliedMotion[0];
                            p.motionY = appliedMotion[1];
                            p.motionZ = appliedMotion[2];
                            if (isRain) {
                                p.motionX *= 5D;
                                p.motionY *= 5D;
                                p.motionZ *= 5D;
                            }
                        }
                    }
                }
            }
        }
    }

    public void terminate() {
        if (isAgainstWall()) {
            for (Entity entity : lastScanEntities) {
                if (entity.getEntityBoundingBox().intersects(box)) {
                    EnumFacing faceOn = getFaceOn();
                    //EntityHelper.putEntityWithinAABB(entity, flatPlane.offset(faceOn.getXOffset() * 0.5D, faceOn.getYOffset() * 0.5D, faceOn.getZOffset() * 0.5D));
                    entity.setPosition(entity.posX, entity.posY, entity.posZ);
                }
                EventHandler.removeMonitoredEntity(entity, this);
            }
        }
        if (hasPair()) {
            pair.setPair(null);
            setPair(null);
        }
    }

    public boolean isValid() {
        return !firstUpdate;
    }

    public boolean isFirstUpdate() {
        return firstUpdate;
    }

    public void forceFirstUpdate() {
        firstUpdate = true;
    }

    //Only for WorldPortals that can teleport
    public boolean isAgainstWall() //you have world, pos, faceOn, etc all to check. This is to remove the collision behind the portal.
    {
        return false;
    }

    private AxisAlignedBB createPlaneAround(double size) {
        return createPlaneAround(getPosition(), size);
    }

    private AxisAlignedBB createPlaneAround(Vec3d pos, double size) {
        double halfW = width / 2D;
        double halfH = height / 2D;

        AxisAlignedBB plane = new AxisAlignedBB(pos.x - halfW, pos.y - halfH, pos.z - size, pos.x + halfW, pos.y + halfH, pos.z + size);
        EnumFacing faceOn = getFaceOn();
        if (faceOn.getAxis() == EnumFacing.Axis.Y) {
            //plane = EntityHelper.rotateAABB(EnumFacing.Axis.X, plane, faceOn == EnumFacing.UP ? -90F : 90F, pos.x, pos.y, pos.z);
        }
        //plane = EntityHelper.rotateAABB(EnumFacing.Axis.Y, plane, faceOn.getAxis() == EnumFacing.Axis.X ? 90F : faceOn.getAxis() == EnumFacing.Axis.Y && getUpDir().getAxis() == EnumFacing.Axis.X ? 90F : 0F, pos.x, pos.y, pos.z).offset(faceOn.getXOffset() * getPlaneOffset(), faceOn.getYOffset() * getPlaneOffset(), faceOn.getZOffset() * getPlaneOffset());
        return plane;
    }

    public AxisAlignedBB getCollisionRemovalAabbForEntity(Entity entity) {
        double max = Math.max(Math.max(entity.width, entity.height) + Math.sqrt(entity.motionX * entity.motionX + entity.motionY * entity.motionY + entity.motionZ * entity.motionZ), 1D);
        EnumFacing faceOn = getFaceOn();
        return flatPlane.expand(faceOn.getXOffset() * -max, faceOn.getYOffset() * -max, faceOn.getZOffset() * -max);
    }

    public AxisAlignedBB getPortalInsides(Entity entity) {
        if (isAgainstWall() && entity instanceof EntityPlayer) {
            EnumFacing faceOn = getFaceOn();
            float offset = Math.min(0.05F, (float) Math.abs((flatPlane.minX - entity.posX) * faceOn.getXOffset() + (flatPlane.minY - entity.posY) * faceOn.getYOffset() + (flatPlane.minZ - entity.posZ) * faceOn.getZOffset()));
            return box.offset(faceOn.getXOffset() * offset, faceOn.getYOffset() * offset, faceOn.getZOffset() * offset);
        }
        return box;
    }

    public AxisAlignedBB getPlane() {
        return plane;
    }

    private void setupAABBs() {
        EnumFacing faceOn = getFaceOn();
        plane = createPlaneAround(0.0125D);
        flatPlane = createPlaneAround(0);
        scanEntitiesIn = flatPlane.expand(faceOn.getXOffset() * getScanDistance(), faceOn.getYOffset() * getScanDistance(), faceOn.getZOffset() * getScanDistance());
        box = flatPlane.expand(faceOn.getXOffset() * -100D, faceOn.getYOffset() * -100D, faceOn.getZOffset() * -100D);
    }

    public AxisAlignedBB getFlatPlane() {
        return flatPlane;
    }

    public AxisAlignedBB getTeleportPlane(float offset) {
        if (offset != 0F) {
            EnumFacing faceOn = getFaceOn();
            return flatPlane.offset(faceOn.getXOffset() * offset, faceOn.getYOffset() * offset, faceOn.getZOffset() * offset);
        }
        return flatPlane;
    }

    public boolean getCullRender() {
        return !renderAll; //cullRender;
    }

    public void setCullRender(boolean flag) {
        //cullRender = flag;
        renderAll = !flag;
    }

    public boolean canTeleportEntities() {
        return true;
    }

    public Set<AxisAlignedBB> getCollisionBoundaries() {
        if (collisions == null) {
            collisions = new MyHashSet<>(4);

            if (canCollideWithBorders()) {
                double size = 0.0125D;
                AxisAlignedBB plane = flatPlane;

                if (plane.maxX - plane.minX > size * 3D) {
                    collisions.add(new AxisAlignedBB(plane.maxX, plane.minY, plane.minZ, plane.maxX + size, plane.maxY, plane.maxZ));
                    collisions.add(new AxisAlignedBB(plane.minX - size, plane.minY, plane.minZ, plane.minX, plane.maxY, plane.maxZ));
                }
                if (plane.maxY - plane.minY > size * 3D) {
                    collisions.add(new AxisAlignedBB(plane.minX, plane.maxY, plane.minZ, plane.maxX, plane.maxY + size, plane.maxZ));
                    collisions.add(new AxisAlignedBB(plane.minX, plane.minY - size, plane.minZ, plane.maxX, plane.minY, plane.maxZ));
                }
                if (plane.maxZ - plane.minZ > size * 3D) {
                    collisions.add(new AxisAlignedBB(plane.minX, plane.minY, plane.maxZ, plane.maxX, plane.maxY, plane.maxZ + size));
                    collisions.add(new AxisAlignedBB(plane.minX, plane.minY, plane.minZ - size, plane.maxX, plane.maxY, plane.minZ));
                }
            }
        }
        return collisions;
    }

    public boolean hasPair() {
        return pair != null && pair.position.y > 0D;
    }

    public Portal getPair() {
        return pair;
    }

    public void setPair(Portal portal) {
        if (pair != portal) {
            pair = portal;
            if (pair != null) {
                formula = QuaternionFormula.createFromPlanes(getFaceOn(), getUpDir(), pair.getFaceOn(), pair.getUpDir());
            }
        }
    }

    public Vec3d getPosition() //position of the world portal, pre-offset
    {
        // todo
        return position;
    }

    public void setPosition(Vec3d v) {
        this.position = v;
        this.posBlock = new BlockPos(v);
        setupAABBs();
    }

    public QuaternionFormula getFormula() {
        return pair != null ? formula : QuaternionFormula.NO_ROTATION;
    }

    public NBTTagCompound write(NBTTagCompound tag) {
        return writePair(writeSelf(tag));
    }

    public NBTTagCompound writeSelf(NBTTagCompound tag) {
        tag.setFloat("width", width);
        tag.setFloat("height", height);

        tag.setInteger("faceOn", faceOn.getIndex());
        tag.setInteger("up", upDir.getIndex());

        tag.setDouble("posX", position.x);
        tag.setDouble("posY", position.y);
        tag.setDouble("posZ", position.z);

        tag.setInteger("time", time);

        return tag;
    }

    public NBTTagCompound writePair(NBTTagCompound tag) {
        if (hasPair()) {
            tag.setTag("pair", pair.writeSelf(new NBTTagCompound()));
        }
        return tag;
    }

    public void read(NBTTagCompound tag) {
        readSelf(tag);
        readPair(tag);
    }

    public void readSelf(NBTTagCompound tag) {
        setSize(tag.getFloat("width"), tag.getFloat("height"));
        setFace(EnumFacing.byIndex(tag.getInteger("faceOn")), EnumFacing.byIndex(tag.getInteger("up")));
        setPosition(new Vec3d(tag.getDouble("posX"), tag.getDouble("posY"), tag.getDouble("posZ")));

        time = tag.getInteger("time");

        firstUpdate = true;
    }

    public void readPair(NBTTagCompound tag) {
        if (tag.hasKey("pair")) {
            setPair(createFakeInstance(tag.getCompoundTag("pair")));
        }
    }

    public abstract <T extends Portal> T createFakeInstance(NBTTagCompound tag);

    public void handleClientEntityTeleport(Entity entity, float[] rotations) {
        if (entity == mc.player) {
            ClientEventHandler.prevCameraRoll = ClientEventHandler.cameraRoll = rotations[2];
            MirrorSubSystem.PORTAL.sendToServer(new PktEntityData(entity));
        }
    }

    public boolean shouldRenderFront(Entity viewer, float partialTicks) {
        //TODO THIS
        Vec3d pos = Vec3d.ZERO;//RendererHelper.getCameraPosition(viewer, partialTicks);
        return !getCullRender() || faceOn.getXOffset() < 0 && pos.x < flatPlane.minX || faceOn.getXOffset() > 0 && pos.x > flatPlane.minX ||
                faceOn.getYOffset() < 0 && pos.y < flatPlane.minY || faceOn.getYOffset() > 0 && pos.y > flatPlane.minY ||
                faceOn.getZOffset() < 0 && pos.z < flatPlane.minZ || faceOn.getZOffset() > 0 && pos.z > flatPlane.minZ;
    }

    public int getRenderDistanceChunks() {
        return Math.min(mc.gameSettings.renderDistanceChunks, MirrorSubSystem.maxRender);
    }
}
