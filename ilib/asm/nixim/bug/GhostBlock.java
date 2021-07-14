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
package ilib.asm.nixim.bug;

import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import roj.asm.Opcodes;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.RemapTo;
import roj.asm.nixim.Shadow;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:50
 */
@Nixim("net.minecraft.server.management.PlayerInteractionManager")
abstract class GhostBlock extends PlayerInteractionManager {
    public GhostBlock(World p_i1524_1_) {
        super(p_i1524_1_);
    }

    /**
     * For non-void-return methods, use this to tell Nixim that this 'return' is not a really return, just continue run next part. <BR>
     * For void-return methods, call _return_void is needed, too. <BR>
     * This method must be static.
     */
    @Shadow("<RETURN>")
    public static void _return_some() {
    }

    @Shadow("field_180240_f")
    private BlockPos destroyPos;

    @RemapTo(value = "func_180784_a", injectPos = 465, codeAtPos = Opcodes.ALOAD_0)
    public void onBlockClicked(BlockPos pos, EnumFacing side) {
        this.player.connection.sendPacket(new SPacketBlockChange(world, this.destroyPos));
        _return_some();
    }
}
