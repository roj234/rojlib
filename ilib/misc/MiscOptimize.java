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
package ilib.misc;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import roj.config.data.CMapping;

import java.util.Arrays;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/18 0:20
 */
public class MiscOptimize {
    public static void giveMeSomeEggs() {
        EntityRegistry.registerEgg(new ResourceLocation("ender_dragon"), 1118481, 6715153);
        EntityRegistry.registerEgg(new ResourceLocation("wither"), 8392800, 2236962);
        EntityRegistry.registerEgg(new ResourceLocation("snowman"), 15658734, 15658734);
        EntityRegistry.registerEgg(new ResourceLocation("villager_golem"), 5592405, 12303291);
    }

    public static void attributeRangeSet(CMapping map) {
        IAttribute[] ATTRIBUTES = new IAttribute[]{
                SharedMonsterAttributes.MAX_HEALTH,
                SharedMonsterAttributes.FOLLOW_RANGE,
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE,
                SharedMonsterAttributes.MOVEMENT_SPEED,
                SharedMonsterAttributes.FLYING_SPEED,
                SharedMonsterAttributes.ATTACK_DAMAGE,
                SharedMonsterAttributes.ATTACK_SPEED,
                SharedMonsterAttributes.ARMOR,
                SharedMonsterAttributes.ARMOR_TOUGHNESS,
                SharedMonsterAttributes.LUCK,
                EntityPlayer.REACH_DISTANCE,
                EntityLivingBase.SWIM_SPEED
        };

        String[] names = new String[]{
                "generic.maxHealth",
                "generic.followRange",
                "generic.knockbackResistance",
                "generic.movementSpeed",
                "generic.flyingSpeed",
                "generic.attackDamage",
                "generic.attackSpeed",
                "generic.armor",
                "generic.armorToughness",
                "generic.luck",
                "generic.reachDistance",
                "forge.swimSpeed"
        };


        for (int i = 0; i < ATTRIBUTES.length; i++) {
            IAttribute attribute = ATTRIBUTES[i];
            if (attribute instanceof RangedAttribute) {
                RangedAttribute att = (RangedAttribute) attribute;

                CMapping map1 = map.getOrCreateMap(names[i]);

                att.minimumValue = map1.putIfAbsent("最低", att.minimumValue);
                att.maximumValue = map1.putIfAbsent("最高", att.maximumValue);

                if (att.getDescription() != null)
                    map1.putIfAbsent("描述", att.getDescription());
            }
        }
    }

    public static void fixVanillaTool() {
        Blocks.OBSIDIAN.setHarvestLevel("pickaxe", 3);
        for (Block block : Arrays.asList(Blocks.DIAMOND_BLOCK, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LIT_REDSTONE_ORE)) {
            block.setHarvestLevel("pickaxe", 2);
        }
        for (Block block : Arrays.asList(Blocks.IRON_BLOCK, Blocks.IRON_ORE, Blocks.LAPIS_BLOCK, Blocks.LAPIS_ORE)) {
            block.setHarvestLevel("pickaxe", 1);
        }
        for (Block block : Arrays.asList(Blocks.ACTIVATOR_RAIL, Blocks.COAL_ORE, Blocks.COBBLESTONE, Blocks.DETECTOR_RAIL, Blocks.DOUBLE_STONE_SLAB,
                Blocks.GOLDEN_RAIL, Blocks.ICE, Blocks.MOSSY_COBBLESTONE, Blocks.NETHERRACK, Blocks.PACKED_ICE,
                Blocks.RAIL, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.STONE, Blocks.STONE_SLAB, Blocks.STONE_BUTTON, Blocks.STONE_PRESSURE_PLATE)) {
            block.setHarvestLevel("pickaxe", 0);
        }
        for (Block block : Arrays.asList(Blocks.CLAY, Blocks.DIRT, Blocks.FARMLAND, Blocks.GRASS, Blocks.GRAVEL, Blocks.MYCELIUM, Blocks.SAND,
                Blocks.SNOW, Blocks.SNOW_LAYER, Blocks.SOUL_SAND, Blocks.GRASS_PATH, Blocks.CONCRETE_POWDER)) {
            block.setHarvestLevel("shovel", 0);
        }
        for (Block block : Arrays.asList(Blocks.PLANKS, Blocks.BOOKSHELF, Blocks.LOG, Blocks.LOG2, Blocks.CHEST, Blocks.PUMPKIN, Blocks.LIT_PUMPKIN,
                Blocks.MELON_BLOCK, Blocks.LADDER, Blocks.WOODEN_BUTTON, Blocks.WOODEN_PRESSURE_PLATE)) {
            block.setHarvestLevel("axe", 0);
        }
        for (Block block : Arrays.asList(Blocks.WEB, Blocks.TALLGRASS, Blocks.VINE, Blocks.TRIPWIRE, Blocks.WOOL)) {
            block.setHarvestLevel("shears", 0);
        }
    }
}
