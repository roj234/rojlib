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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : Common ME energy interface
 * Author: R__
 * Filename: CMEnergy.java
 */
package ilib.util.energy;

import ilib.api.energy.IMEnergy;
import ilib.api.energy.IMEnergyStorage;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

public final class CMEnergy {
    public final IMEnergy e;
    private IMEnergyStorage s;
    public final TileEntity te;

    public CMEnergy(TileEntity te, IMEnergy me) {
        e = me;
        if (me instanceof IMEnergyStorage)
            s = (IMEnergyStorage) me;
        this.te = te;
    }

    public TileEntity getTile() {
        return te;
    }

    public boolean equals(Object o) {
        return o instanceof CMEnergy && te.equals(((CMEnergy) o).te);
    }

    public int hashCode() {
        return te.hashCode();
    }

    public String toString() {
        return "TE at dimension " + te.getWorld().provider.getDimension() + " pos " + te.getPos() + " className: " + te.getClass().getName() + '\n';
    }

    public int mayGetEnergyFlow(EnumFacing side, int def) {
        return s == null ? def : s.getEnergyFlow(side);
    } // 1 out, 0 no, -1 in
}
