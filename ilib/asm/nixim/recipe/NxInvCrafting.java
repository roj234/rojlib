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
package ilib.asm.nixim.recipe;

import ilib.misc.MCHooks;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;

/**
 * @author Roj234
 * @since 2021/4/21 22:51java
 */
@Nixim(value = "net.minecraft.inventory.InventoryCrafting", copyItf = true)
abstract class NxInvCrafting extends InventoryCrafting implements MCHooks.RecipeCache {
    // currentRecipe与CatServer冲突
    @Copy
    public IRecipe field_0_a;

    private NxInvCrafting(Container p_i1807_1_, int p_i1807_2_, int p_i1807_3_) {
        super(p_i1807_1_, p_i1807_2_, p_i1807_3_);
    }

    @Override
    @Copy
    public IRecipe getRecipe() {
        return field_0_a;
    }

    @Override
    @Copy
    public void setRecipe(IRecipe recipe) {
        this.field_0_a = recipe;
    }
}
