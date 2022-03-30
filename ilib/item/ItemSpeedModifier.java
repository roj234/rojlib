package ilib.item;

import ilib.util.Colors;
import ilib.util.MCTexts;
import ilib.util.PlayerUtil;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/4/4 14:18
 */
public class ItemSpeedModifier extends ItemRightClick {
    public ItemSpeedModifier() {
        setCreativeTab(CreativeTabs.TOOLS);
        setMaxStackSize(1);
    }

    @Nonnull
    @Override
    public EnumAction getItemUseAction(@Nonnull ItemStack stack) {
        return EnumAction.BLOCK;
    }

    @Override
    protected void addTooltip(ItemStack stack, List<String> list) {
        list.add(MCTexts.format("tooltip.ilib.place_here.1"));
        list.add(MCTexts.format("tooltip.ilib.place_here.2"));
    }

    @Override
    protected ItemStack onRightClick(World world, EntityPlayer player, ItemStack stack, EnumHand hand) {
        if(player.capabilities.isFlying) {
            float fly = player.capabilities.getFlySpeed();
            player.capabilities.setFlySpeed(fly = (!player.isSneaking() ? (fly >= 3.2f ? 0.05f : fly * 2) : 0.05f));
            if(world.isRemote)
                PlayerUtil.sendTo(player, Colors.GREY + "飞行速度: (" + Colors.ORANGE + (fly / 0.05f) + 'x' + Colors.GREY + ')');
        } else {
            float walk = player.capabilities.getWalkSpeed();
            player.capabilities.setPlayerWalkSpeed(walk = (!player.isSneaking() ? (walk >= 3.2f ? 0.025f : walk * 2) : 0.1f));
            if(world.isRemote)
                PlayerUtil.sendTo(player, Colors.GREY + "走路速度: (" + Colors.ORANGE + (walk / 0.1f) + 'x' + Colors.GREY + ')');
        }
        // player.sendPlayerAbilities();
        return stack;
    }
}
