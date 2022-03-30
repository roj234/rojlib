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

package ilib.tile;

import com.mojang.authlib.GameProfile;
import ilib.util.NBTType;
import roj.collect.SimpleList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.UUID;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class OwnerManager {
    public static final byte PUBLIC = 0, PRIVATE = 1, PROTECTED = 2;

    public static OwnerManager getById(String id) {
        return null;
    }

    public static OwnerManager getByPlayer(GameProfile profile) {
        return new OwnerManager().setOwner(profile);
    }

    private GameProfile owner;
    private final SimpleList<GameProfile> trusted;

    public OwnerManager() {
        this.trusted = new SimpleList<>();
    }

    public OwnerManager setOwner(GameProfile player) {
        owner = player;
        trusted.clear();
        return this;
    }

    public boolean isTrusted(EntityPlayer player, int type) {
        GameProfile gp = player.getGameProfile();
        return type == PUBLIC || gp.equals(owner) || (type == PROTECTED && trusted.contains(gp));
    }

    public void addTrusted(EntityPlayer player) {
        if (isTrusted(player, PROTECTED)) trusted.add(player.getGameProfile());
    }

    public void removeTrusted(EntityPlayer player) {
        trusted.remove(player.getGameProfile());
    }

    public boolean isOwner(EntityPlayer player) {
        return player.getGameProfile().equals(owner);
    }

    public static NBTTagCompound serializeGameProfile(GameProfile gp) {
        NBTTagCompound tag = new NBTTagCompound();
        if (gp.getName() != null) {
            tag.setString("N", gp.getName());
        }
        UUID id = gp.getId();
        if (id != null) {
            tag.setLong("U", id.getMostSignificantBits());
            tag.setLong("u", id.getLeastSignificantBits());
        }
        return tag;
    }

    public static GameProfile deserializeGameProfile(NBTTagCompound tag) {
        String name = null;
        if (tag.hasKey("N", NBTType.STRING))
            name = tag.getString("N");

        UUID id = null;
        if (tag.hasKey("U", NBTType.LONG) && tag.hasKey("u", NBTType.LONG)) {
            id = new UUID(tag.getLong("U"), tag.getLong("u"));
        }

        try {
            return new GameProfile(id, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public NBTTagCompound serialize() {
        NBTTagCompound tag = serializeGameProfile(owner);
        if (trusted.size() > 0) {
            NBTTagList list = new NBTTagList();
            tag.setTag("T", list);

            for (int i = 0; i < trusted.size(); i++) {
                list.appendTag(serializeGameProfile(trusted.get(i)));
            }
        }

        return tag;
    }

    public void deserialize(NBTTagCompound tag) {
        owner = deserializeGameProfile(tag);
        if (tag.hasKey("T", NBTType.LIST)) {
            NBTTagList list = tag.getTagList("T", NBTType.COMPOUND);
            trusted.clear();
            trusted.ensureCapacity(list.tagCount());
            for (int i = 0; i < list.tagCount(); i++) {
                GameProfile gp = deserializeGameProfile(list.getCompoundTagAt(i));
                if (gp != null) trusted.add(gp);
            }
        }
    }

    public String getId() {
        return "//";
    }
}