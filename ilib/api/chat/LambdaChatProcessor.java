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
package ilib.api.chat;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/19 20:49
 */
public class LambdaChatProcessor implements ChatProcessor {
    Node head, tail;

    public LambdaChatProcessor then(LinkedProcessor processor) {
        if (head == null) {
            head = tail = new Node(processor);
        } else {
            tail.next = new Node(processor);
            tail = tail.next;
        }
        return this;
    }

    @Override
    public ChatProcessor processChat(String chat, ChatContext context) {
        Node node = (Node) context.customData.get("currNode");
        if (node == null) {
            context.customData.put("currNode", head);
            node = head;
        }
        if (node.processor.onChat(chat, context)) {
            if (node.next == null)
                return END;
            context.customData.put("currNode", node.next);
        }
        return this;
    }

    private static class Node {
        Node next;
        final LinkedProcessor processor;

        private Node(LinkedProcessor processor) {
            this.processor = processor;
        }
    }

    public interface LinkedProcessor {
        boolean onChat(String text, ChatContext context);
    }
}
