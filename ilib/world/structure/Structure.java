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

package ilib.world.structure;

import ilib.util.BlockHelper;
import ilib.world.structure.schematic.Schematic;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class Structure extends AbstractStructure {
    protected GenerationType genType;

    public Structure(Schematic schematic, GenerationType type) {
        super(schematic);
        this.genType = type;
    }

    public Structure(Schematic schematic) {
        this(schematic, GenerationType.SURFACE);
    }

    @Override
    public void generate(World world, BlockPos loc) {
        int yCoord = getYCoord(world, loc);
        generate(world, loc.getX(), yCoord, loc.getZ());
    }

    protected int getYCoord(World world, BlockPos pos) {
        int yCoord = pos.getY();
        switch (this.genType) {
            case SURFACE:
                yCoord = BlockHelper.getSurfaceBlockY(world, pos.getX(), pos.getZ());
        }
        return yCoord;
    }

    public GenerationType getGenerationType() {
        return this.genType;
    }
}