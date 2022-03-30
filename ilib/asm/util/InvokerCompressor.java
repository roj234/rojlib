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

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import roj.collect.SimpleList;
import roj.reflect.DirectAccessor;

import java.util.List;

/**
 * @author Roj234
 * @since  2020/11/14 21:33
 */
public final class InvokerCompressor implements IEventListener {
    private final ListenerList target;
    private final int busID;

    public InvokerCompressor(List<IEventListener> list1, ListenerList list, int busID) {
        this.target = list;
        this.busID = busID;
    }

    public static final EventPriority[] priorities = EventPriority.values();
    public static H Helper;
    static {
        try {
            Class<?> inst = Class.forName("net.minecraftforge.fml.common.eventhandler.ListenerList$ListenerListInst");
            Helper = DirectAccessor
                .builder(H.class)
                .delegate_o(inst, new String[]{ "forceRebuild", "shouldRebuild" })
                .access(inst, "priorities", "getListeners", null)
                .delegate_o(ListenerList.class, "getInstance")
                .build();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public interface H {
        Object getListeners(Object o);
        void forceRebuild(Object o);
        boolean shouldRebuild(Object o);
        Object getInstance(Object o, int id);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void invoke(Event event) {
        Object instance = Helper.getInstance(target, busID);

        target.unregister(busID, this);
        if (!Helper.shouldRebuild(instance))
            throw new IllegalStateException("Unexpected error : first is not self!!!");

        List<EventInvokerV2> nocancel = new SimpleList<>(), cancel = new SimpleList<>();

        for (EventPriority priority : priorities) {
            List<IEventListener> listeners = ((List<List<IEventListener>>) Helper.getListeners(instance)).get(priority.ordinal());
            for (int i = 0; i < listeners.size(); i++) {
                IEventListener listener = listeners.get(i);
                if (listener instanceof EventInvokerV2) {
                    EventInvokerV2 v2 = (EventInvokerV2) listener;
                    if (v2.canBatch()) {
                        (v2.recvCancel ? cancel : nocancel).add(v2);
                    }
                }
            }

            if (nocancel.size() > 1) {
                for (int i = 0; i < cancel.size(); i++) {
                    EventInvokerV2 v2 = cancel.get(i);
                    target.unregister(busID, v2);

                    IEventListener merged = EventInvokerV2.batch(cancel, false);
                    target.register(busID, priority, merged);
                }
            }

            if (cancel.size() > 1) {
                for (int i = 0; i < cancel.size(); i++) {
                    EventInvokerV2 v2 = cancel.get(i);
                    target.unregister(busID, v2);

                    IEventListener merged = EventInvokerV2.batch(cancel, false);
                    target.register(busID, priority, merged);
                }
            }

            nocancel.clear();
            cancel.clear();
        }
    }
}
