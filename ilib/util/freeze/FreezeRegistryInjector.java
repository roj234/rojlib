/*
 * This file is a part of MoreItems
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
package ilib.util.freeze;

import com.google.common.collect.BiMap;
import ilib.Config;
import ilib.util.ForgeUtil;
import ilib.util.Registries;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry.EntityRegistration;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry.MissingFactory;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * @author Roj233
 * @since 2021/8/26 20:03
 */
public class FreezeRegistryInjector {
    static BiMap<Class<? extends Entity>, EntityRegistration> entityClassRegistrations;

    /**
     * @see ilib.asm.nixim.FastTileConst#create(World, NBTTagCompound)
     */
    @SuppressWarnings("unchecked")
    public static void inject() {
        FieldAccessor accessor, a2;
        try {
            accessor = ReflectionUtils.access(ForgeRegistry.class.getDeclaredField("missing"));
            if(entityClassRegistrations == null) {
                a2 = ReflectionUtils.access(EntityRegistry.class.getDeclaredField("entityClassRegistrations"));
                a2.setInstance(EntityRegistry.instance());
                entityClassRegistrations = (BiMap<Class<? extends Entity>, EntityRegistration>) a2.getObject();
                entityClassRegistrations.put(FreezedEntity.class, EntityRegistry.instance().
                        new EntityRegistration(ForgeUtil.getCurrentMod(), new ResourceLocation("armor_stand"),
                                               FreezedEntity.class, "armor_stand_1", 12580,
                                               32, 999999, false, null));
                a2.clearInstance();
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return;
        }
        accessor.setInstance(Registries.item());

        if(Config.freezeUnknownEntries.contains("item"))
            accessor.setObject(new Injector<>(0)); // Item inject

        accessor.setInstance(Registries.block()); // Block inject
        if(Config.freezeUnknownEntries.contains("block"))
            accessor.setObject(new Injector<>(1));

        accessor.setInstance(Registries.entity()); // Entity inject
        if(Config.freezeUnknownEntries.contains("entity"))
            accessor.setObject(new Injector<>(2));

        accessor.clearInstance();
    }

    static final class Injector<T extends IForgeRegistryEntry<T>> implements MissingFactory<T> {
        final byte type;
        public Injector(int i) {
            this.type = (byte) i;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T createMissing(ResourceLocation reg, boolean b) {
            switch (type) {
                case 0:
                    return (T) new FreezedBlock().setRegistryName(reg);
                case 1:
                    return (T) new FreezedItem().setRegistryName(reg);
                case 2:
                    return (T) new EntityEntry(FreezedEntity.class, "freezed") {
                        @Override
                        public int hashCode() {
                            return getRegistryName() == null ? 0 : getRegistryName().hashCode();
                        }

                        @Override
                        public boolean equals(Object obj) {
                            return obj instanceof EntityEntry && ((EntityEntry) obj).getRegistryName().equals(getRegistryName());
                        }
                    }.setRegistryName(reg);
            }
            throw new InternalError("Unknown type " + type);
        }
    }
}
