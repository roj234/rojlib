package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.command.CommandResultStats;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;

import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2020/10/3 1:03
 */
@Nixim("/")
abstract class TrimNBT extends Entity {
	@Shadow("field_184244_h")
	private List<Entity> riddenByEntities;
	@Shadow("field_190534_ay")
	private int fire;
	@Shadow("field_83001_bt")
	private boolean invulnerable;
	@Shadow("field_174837_as")
	private CommandResultStats cmdResultStats;
	@Shadow("field_184236_aF")
	private Set<String> tags;
	@Shadow("customEntityData")
	private NBTTagCompound customEntityData;
	@Shadow("capabilities")
	private CapabilityDispatcher capabilities;

	public TrimNBT(World worldIn) {
		super(worldIn);
	}

	@Copy
	protected static NBTTagList list3d(double a, double b, double c) {
		NBTTagList list = new NBTTagList();
		list.appendTag(new NBTTagDouble(a));
		list.appendTag(new NBTTagDouble(b));
		list.appendTag(new NBTTagDouble(c));

		return list;
	}

	@Inject("/")
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		try {
			tag.setTag("Pos", list3d(posX, posY, posZ));
			tag.setTag("Motion", list3d(motionX, motionY, motionZ));
			tag.setTag("Rotation", newFloatNBTList(rotationYaw, rotationPitch));

			if (fallDistance > 0) tag.setFloat("FallDistance", fallDistance);
			if (fire > 0) tag.setShort("Fire", (short) fire);
			if (getAir() > 0) tag.setShort("Air", (short) getAir());
			if (onGround) tag.setBoolean("OnGround", true);
			if (dimension != 0) tag.setInteger("Dimension", dimension);
			if (invulnerable) tag.setBoolean("Invulnerable", true);
			if (timeUntilPortal > 0) tag.setInteger("PortalCooldown", timeUntilPortal);

			tag.setUniqueId("UUID", getUniqueID());
			if (hasCustomName()) {
				tag.setString("CustomName", getCustomNameTag());
			}

			if (getAlwaysRenderNameTag()) tag.setBoolean("CustomNameVisible", true);

			cmdResultStats.writeStatsToNBT(tag);

			if (isSilent()) tag.setBoolean("Silent", true);
			if (hasNoGravity()) tag.setBoolean("NoGravity", true);
			if (glowing) tag.setBoolean("Glowing", true);
			if (updateBlocked) tag.setBoolean("UpdateBlocked", true);

			if (!tags.isEmpty()) {
				NBTTagList list = new NBTTagList();

				for (String s : tags) {
					list.appendTag(new NBTTagString(s));
				}

				tag.setTag("Tags", list);
			}

			if (customEntityData != null) {
				tag.setTag("ForgeData", customEntityData);
			}

			if (capabilities != null) {
				tag.setTag("ForgeCaps", capabilities.serializeNBT());
			}

			writeEntityToNBT(tag);

			if (isBeingRidden()) {
				NBTTagList list = new NBTTagList();

				for (int i = 0; i < riddenByEntities.size(); i++) {
					NBTTagCompound tag1 = new NBTTagCompound();
					if (riddenByEntities.get(i).writeToNBTAtomically(tag1)) {
						list.appendTag(tag1);
					}
				}

				if (!list.isEmpty()) tag.setTag("Passengers", list);
			}

			return tag;
		} catch (Throwable e) {
			CrashReport rpt = CrashReport.makeCrashReport(e, "Saving entity NBT");
			this.addEntityCrashInfo(rpt.makeCategory("Entity being saved"));
			throw new ReportedException(rpt);
		}
	}
}
