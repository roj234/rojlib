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

import ilib.asm.util.MCHooks.ItemStackMap;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.common.FMLLog;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.item.crafting.FurnaceRecipes")
public abstract class NiximFastFurnace {
    @Shadow("field_77604_b")
    private Map<ItemStack, ItemStack> smeltingList;
    @Shadow("field_77605_c")
    private Map<ItemStack, Float> experienceList;

    @Inject("<init>")
    public NiximFastFurnace() {
        this.smeltingList = new ItemStackMap<>();
        this.experienceList = new ItemStackMap<>();
    }

    @Inject("func_151394_a")
    public void addSmeltingRecipe(ItemStack input, ItemStack stack, float experience) {
        ItemStack out = this.getSmeltingResult(input);
        if (out != ItemStack.EMPTY) {
            FMLLog.log.info("冲突的熔炉合成: {} => {} 和 {}", input, stack, out);
        } else {
            this.smeltingList.put(input, stack);
            this.experienceList.put(stack, experience);
        }
    }

    /**
     * HashMap的实现: comparableItemStack.equals(storedStack)
     */
    @Inject("func_151395_a")
    public ItemStack getSmeltingResult(ItemStack stack) {
        return smeltingList.getOrDefault(stack, ItemStack.EMPTY);
    }

    @Inject("func_151398_b")
    public float getSmeltingExperience(ItemStack stack) {
        float ret = stack.getItem().getSmeltingExperience(stack);
        if (ret != -1) {
            return ret;
        } else {
            return experienceList.getOrDefault(stack, 0.0f);
        }
    }
}
