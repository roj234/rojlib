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

import net.minecraft.entity.player.EntityPlayerMP;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.WeakHashMap;

/**
 * @author Roj234
 * @since  2020/9/19 20:38
 */
public final class PlayerChatApi {
    /**
     * Example
     * final ChatProcessor processor = new LambdaChatProcessor().then((text, context) -> {
     * if(text.equals("1")) {
     * PlayerUtil.sendToPlayer(context.player, "答案正确!");
     * PlayerUtil.sendToPlayer(context.player, "请回答问题2: 飞行速度为: (0-1)");
     * return true;
     * } else {
     * PlayerUtil.sendToPlayer(context.player, "答案错误!");
     * return false;
     * }
     * }).then((text, context) -> {
     * int result = TextUtil.isNumber(text);
     * if(result > -1) {
     * double fly = Double.parseDouble(text);
     * PlayerUtil.sendToPlayer(context.player, Colors.GREY + "飞行速度: (" + Colors.ORANGE + (fly / 0.1f) + 'x' + Colors.GREY + ')');
     * return true;
     * } else {
     * PlayerUtil.sendToPlayer(context.player, "不是数字!");
     * return false;
     * }
     * });
     * <p>
     * protected ItemStack onRightClick(World world, EntityPlayer player, ItemStack stack, EnumHand hand) {
     * if(player instanceof EntityPlayerMP) {
     * if(PlayerChatApi.beginChat((EntityPlayerMP) player, processor)) {
     * PlayerUtil.sendToPlayer(player, "欢迎使用!");
     * PlayerUtil.sendToPlayer(player, "请输入: 1");
     * } else {
     * PlayerUtil.sendToPlayer(player, "你还没回答完!");
     * }
     * }
     * return stack;
     * }
     * }, ModTabs.tabMI, 1, false);
     */

    static final WeakHashMap<EntityPlayerMP, ChatContext> chatting = new WeakHashMap<>();

    public static boolean beginChat(EntityPlayerMP player, ChatProcessor processor) {
        ChatContext context = chatting.get(player);
        if (context != null)
            return false;
        chatting.put(player, new ChatContext(player, processor));
        return true;
    }

    static {
        MinecraftForge.EVENT_BUS.register(PlayerChatApi.class);
    }

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        ChatContext context = chatting.get(event.getPlayer());
        if (context != null) {
            event.setCanceled(true);
            ChatProcessor processor = context.currentProcessor.processChat(event.getMessage(), context);
            if (processor != null) {
                if (processor == ChatProcessor.END) {
                    chatting.remove(event.getPlayer());
                    return;
                }
                context.playerSaid.add(event.getMessage());
                context.currentProcessor = processor;
            }
        }
    }
}
