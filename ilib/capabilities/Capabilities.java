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
package ilib.capabilities;

import ilib.api.energy.IMEnergy;
import ilib.api.energy.IMEnergyCap;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

import java.util.concurrent.Callable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class Capabilities {
    @CapabilityInject(EntitySize.class)
    public static Capability<EntitySize> RENDERING_SIZE;
    @CapabilityInject(TmpData.class)
    public static Capability<TmpData> TEMP_STORAGE;
    @CapabilityInject(IMEnergyCap.class)
    public static Capability<IMEnergyCap> MENERGY;
    @CapabilityInject(IMEnergy.class)
    public static Capability<IMEnergy> MENERGY_TILE;

    public static final NilStorage<?> NIL_STORAGE = new NilStorage<>();
    public static final INBTStorage<?> NBT_STORAGE = new INBTStorage<>();
    public static final Callable<?> NIL_FACTORY = () -> null;

    @SuppressWarnings("unchecked")
    public static void init() {
        // interface, storage, implementation
        CapabilityManager.INSTANCE.register(IMEnergy.class, (NilStorage<IMEnergy>) NIL_STORAGE, (Callable<IMEnergy>) NIL_FACTORY);
        CapabilityManager.INSTANCE.register(IMEnergyCap.class, (INBTStorage<IMEnergyCap>) NBT_STORAGE, IMEnergyImpl::new);
        CapabilityManager.INSTANCE.register(TmpData.class, (NilStorage<TmpData>) NIL_STORAGE, (Callable<TmpData>) NIL_FACTORY);
        CapabilityManager.INSTANCE.register(EntitySize.class, (NilStorage<EntitySize>) NIL_STORAGE, (Callable<EntitySize>) NIL_FACTORY);
        // todo impl
    }
}
