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
package ilib.asm.util;

import ilib.Config;
import ilib.asm.nixim.EventInvokerV2;
import roj.collect.IntMap;
import roj.reflect.ClassDefiner;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/14 21:33
 */
public final class InvokerCompressor implements IEventListener {
    private final ListenerList target;
    private final List<IEventListener> allListeners;
    private final int busID;

    public InvokerCompressor(List<IEventListener> list1, ListenerList list, int busID) {
        this.target = list;
        this.allListeners = list1;
        this.busID = busID;
    }

    static final EventPriority[] priorities = EventPriority.values();
    static final Supplier<List<EventInvokerV2>> aNew = ArrayList::new;

    @Override
    public void invoke(Event event) {
        if (allListeners.remove(0) != this)
            throw new IllegalStateException("Unexpected error : first is not self!!!");

        if (!Config.eventInvokerMost) {
            for (ListIterator<IEventListener> iterator = allListeners.listIterator(); iterator.hasNext(); ) {
                IEventListener listener = iterator.next();
                if (listener instanceof EventInvokerV2) {
                    final EventInvokerV2 v2 = (EventInvokerV2) listener;
                    if (v2.canCompress() && !v2.receiveCanceled()) {
                        IEventListener asmListener = v2.compress();
                        iterator.set(asmListener);
                        target.unregister(busID, v2);
                        target.register(busID, priorities[v2.getPriority()], asmListener);
                    }
                }
            }
        } else {
            ClassDefiner.debug = true;
            IntMap<List<EventInvokerV2>> nocancel = new IntMap<>(), cancel = new IntMap<>();

            for (ListIterator<IEventListener> iterator = allListeners.listIterator(); iterator.hasNext(); ) {
                IEventListener listener = iterator.next();
                if (listener instanceof EventInvokerV2) {
                    final EventInvokerV2 v2 = (EventInvokerV2) listener;
                    if (v2.canCompress()) {
                        (v2.receiveCanceled() ? cancel : nocancel).computeIfAbsentSp(v2.getPriority(), aNew).add(v2);
                        iterator.remove();
                    }
                }
            }

            if (!nocancel.isEmpty()) {
                for (IntMap.Entry<List<EventInvokerV2>> entry : nocancel.entrySet()) {
                    final List<EventInvokerV2> list = entry.getValue();
                    if(list.size() != 1) {
                        for (EventInvokerV2 v2 : list)
                            target.unregister(busID, v2);

                        IEventListener asmListener = EventInvokerV2.compressAll(list, true);
                        target.register(busID, priorities[entry.getKey()], (event1) -> {
                            if (!event1.isCanceled()) {
                                asmListener.invoke(event1);
                            }
                        });
                    } else {
                        allListeners.add(list.get(0));
                    }
                }
            }

            if (!cancel.isEmpty()) {
                for (IntMap.Entry<List<EventInvokerV2>> entry : cancel.entrySet()) {
                    final List<EventInvokerV2> list = entry.getValue();
                    if(list.size() != 1) {
                        for (EventInvokerV2 v2 : list)
                            target.unregister(busID, v2);

                        IEventListener asmListener = EventInvokerV2.compressAll(entry.getValue(), false);
                        target.register(busID, priorities[entry.getKey()], asmListener);
                    } else {
                        allListeners.add(list.get(0));
                    }
                }
            }
        }

        target.unregister(busID, this);
    }
}
