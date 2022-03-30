package ilib.asm.nixim.recipe;

import ilib.asm.util.IOreIngredient;
import net.minecraftforge.oredict.OreIngredient;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.NiximSystem;

/**
 * @author solo6975
 * @since 2022/3/31 13:42
 */
@Nixim(value = "net.minecraftforge.oredict.OreIngredient", copyItf = true)
class NxOreIng extends OreIngredient implements IOreIngredient {
    @Copy
    private String oreName;

    public NxOreIng(String ore) {
        super(ore);
    }

    @Inject(value = "<init>", at = Inject.At.HEAD)
    public void x(String ore) {
        oreName = ore;
        NiximSystem.SpecMethods.$$$CONTINUE_V();
    }

    @Override
    @Copy
    public String getOredict() {
        return oreName;
    }
}
