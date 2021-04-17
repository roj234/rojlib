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
package ilib.client.renderer.mirror;

import ilib.client.renderer.mirror.portal.Portal;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import roj.collect.MyHashSet;
import roj.util.Helpers;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class EventHandler {
    public static WeakHashMap<Entity, Set<Portal>>[] monitoredEntities = Helpers.cast(new WeakHashMap<?, ?>[2]); // This is for portals that are against the wall.;

    static {
        monitoredEntities[0] = new WeakHashMap<>(); // client
        monitoredEntities[1] = new WeakHashMap<>(); // server
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource() == DamageSource.IN_WALL) {
            if (isInPortal(event.getEntity())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        monitoredEntities[event.getWorld().isRemote ? 0 : 1].entrySet().removeIf(e -> e.getKey().getEntityWorld() == event.getWorld());
    }

    @SubscribeEvent
    public static void onGetCollisionBoxesEvent(GetCollisionBoxesEvent event) {
        if (event.getEntity() == null) { //assume particles? too much work to figure out, ignore.
            return;
        }
        Map<Entity, Set<Portal>> sideMap = monitoredEntities[event.getWorld().isRemote ? 0 : 1];
        if (sideMap.containsKey(event.getEntity())) {
            Set<Portal> portals = sideMap.get(event.getEntity());
            for (Iterator<Portal> iterator = portals.iterator(); iterator.hasNext(); ) {
                Portal portal = iterator.next();
                if (!portal.isValid()) {
                    iterator.remove();
                    continue;
                }
                AxisAlignedBB check = portal.getCollisionRemovalAabbForEntity(event.getEntity()); //should I do it this way? WHAT ABOUT PARTICLES?
                if (check.intersects(event.getAabb())) {
                    if (event.getAabb().equals(event.getEntity().getEntityBoundingBox())) { //entity being pushed out of blocks
                        event.getCollisionBoxesList().clear();
                    } else {
                        //REMOVE ALL THOSE THAT INTERSECT
                        for (int i = event.getCollisionBoxesList().size() - 1; i >= 0; i--) {
                            AxisAlignedBB aabb = event.getCollisionBoxesList().get(i);
                            boolean flag = false;
                            for (AxisAlignedBB portalBorder : portal.getCollisionBoundaries()) {
                                if (portalBorder.equals(aabb)) {
                                    flag = true;
                                    break;
                                }
                            }
                            if (!flag && aabb.intersects(check)) {
                                event.getCollisionBoxesList().remove(i);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void addMonitoredEntity(Entity entity, Portal portal) {
        monitoredEntities[entity.world.isRemote ? 0 : 1].computeIfAbsent(entity, k -> new MyHashSet<>(2)).add(portal);
    }

    public static void removeMonitoredEntity(Entity entity, Portal portal) {
        Map<Entity, Set<Portal>> sideMap = monitoredEntities[entity.world.isRemote ? 0 : 1];
        Set<Portal> portals = sideMap.get(entity);
        if (portals != null) {
            portals.remove(portal);
            if (portals.isEmpty()) {
                sideMap.remove(entity);
            }
        }
    }

    public static boolean isInPortal(Entity ent) {
        Map<Entity, Set<Portal>> sideMap = monitoredEntities[ent.world.isRemote ? 0 : 1];
        Set<Portal> portals = sideMap.get(ent);
        if (portals != null) {
            for (Portal portal : portals) {
                if (!portal.isValid()) {
                    continue;
                }
                AxisAlignedBB check = portal.getCollisionRemovalAabbForEntity(ent);
                if (check.intersects(ent.getEntityBoundingBox())) {
                    return true;
                }
            }
        }
        return false;
    }
}
