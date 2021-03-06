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
package ilib.asm.nixim;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import java.net.SocketAddress;
import java.util.UUID;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.server.management.PlayerList")
abstract class NoDuplicateLogin extends PlayerList {
    public NoDuplicateLogin(MinecraftServer serv) {
        super(serv);
    }

    @Inject("func_148542_a")
    public String allowUserToConnect(SocketAddress address, GameProfile profile) {
        String result = super.allowUserToConnect(address, profile);
        if (result != null)
            return result;
        UUID uuid = EntityPlayer.getUUID(profile);
        if (getPlayerByUUID(uuid) != null) {
            return "????????????????????????";
        }
        return null;
    }
}
