package ilib.asm.nixim;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.SimpleList;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/6 21:13
 */
@Nixim(value = "net.minecraft.entity.Entity")
class Misc1 extends Entity {
    @Shadow("field_184244_h")
    private List<Entity> riddenByEntities;

    public Misc1(World worldIn) {
        super(worldIn);
    }

    @Override
    protected void entityInit() {

    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbtTagCompound) {

    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbtTagCompound) {

    }

    @Inject("func_184188_bt")
    public List<Entity> getPassengers() {
        return this.riddenByEntities.isEmpty() ? Collections.emptyList() : new SimpleList<>(this.riddenByEntities);
    }
}
