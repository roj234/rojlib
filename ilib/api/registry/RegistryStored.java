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
package ilib.api.registry;

import ilib.network.IMessage;
import ilib.network.IMessageHandler;
import ilib.network.MessageContext;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.ToIntMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import net.minecraftforge.fml.relauncher.Side;

import java.util.function.IntFunction;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/27 23:25
 */
public class RegistryStored<T extends Indexable> extends Registry<T> {
    static final MyHashMap<String, RegistryStored<?>> syncSystem = new MyHashMap<>();

    private final String  id;
    private       Snap<T> local;

    public RegistryStored(String id, IntFunction<T[]> arrayGet) {
        super(arrayGet);
        this.id = id;
        syncSystem.put(id, this);
    }

    public static final class Snap<T extends Indexable> {
        private Snap() {}

        private Snap(T[] v) {
            v.getClass();
            arr = v;
        }

        private T[] arr;
    }

    public Snap<T> snapshot() {
        return new Snap<>(values());
    }

    public void restore(Snap<T> snapshot) {
        arr = snapshot.arr;
        for (int i = 0; i < arr.length; i++) {
            values.putByValue(i, arr[i]);
        }
    }

    public void leaveServer() {
        restore(local);
        local = null;
    }

    public SyncPacket createPacket() {
        ToIntMap<String> mapping = new ToIntMap<>();

        for (int i = 0; i < values.size(); i++) {
            mapping.putInt(values.get(i).getName(), i);
        }

        return new SyncPacket(id, mapping);
    }

    public void save() {

    }

    public void load() {

    }

    static class SyncPacket implements IMessage, IMessageHandler<SyncPacket> {
        String id;
        ToIntMap<String> mapping;

        public SyncPacket() {
            mapping = new ToIntMap<>();
        }

        public SyncPacket(String id, ToIntMap<String> mapping) {
            this.id = id;
            this.mapping = mapping;
        }

        @Override
        public void fromBytes(ByteReader buf) {
            id = buf.readVString();
            int len = buf.readVarInt(false);
            mapping.ensureCapacity(len);
            for (int i = 0; i < len; i++) {
                mapping.putInt(buf.readVString(), buf.readVarInt(false));
            }
        }

        @Override
        public void toBytes(ByteWriter buf) {
            buf.writeVString(id).writeVarInt(mapping.size(), false);
            for(ToIntMap.Entry<String> entry : mapping.selfEntrySet()) {
                buf.writeVarInt(entry.v, false).writeVString(entry.k);
            }
        }

        @Override
        public void onMessage(SyncPacket message, MessageContext ctx) {
            if(ctx.side == Side.CLIENT) {
                RegistryStored<?> registry = syncSystem.get(message.id);
                if(registry == null)
                    throw new IllegalArgumentException("Registry " + message.id + " is missing");
                IntBiMap<?> values = registry.values;
                ToIntMap<String> serverVal = message.mapping;
                if(values.size() != serverVal.size()) {
                    String info;
                    if(values.size() < serverVal.size()) {
                        for (Indexable i : registry.values()) {
                            serverVal.remove(i.getName());
                        }
                        info = serverVal.values().toString();
                    } else {
                        MyHashSet<String> keys = new MyHashSet<>();
                        for (Indexable i : registry.values()) {
                            keys.add(i.getName());
                        }

                        for (String name : serverVal.keySet()) {
                            keys.remove(name);
                        }
                        info = keys.toString();
                    }

                    throw new IllegalStateException("Fatal index missing " + info);
                }

                registry.local = Helpers.cast(registry.snapshot());

                for (ToIntMap.Entry<String> entry : serverVal.selfEntrySet()) {
                    Indexable obj = registry.nameIndex.get(entry.k);
                    values.putByValue(entry.v, Helpers.cast(obj));
                }
            }
        }
    }
}
