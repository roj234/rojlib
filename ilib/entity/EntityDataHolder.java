package ilib.entity;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2022/5/16 1:37
 */
public class EntityDataHolder extends Entity {
	public EntityDataHolder(World w) {
		super(w);
	}

	public NBTTagCompound data = new NBTTagCompound();

	@Override
	protected void entityInit() {}

	@Override
	protected void readEntityFromNBT(NBTTagCompound tag) {
		data = tag.getCompoundTag("Data");
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound tag) {
		if (!data.isEmpty()) tag.setTag("Data", data);
	}

	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		try {
			tag.setTag("Pos", newDoubleNBTList(posX, posY, posZ));

			if (capabilities != null) {
				tag.setTag("ForgeCaps", capabilities.serializeNBT());
			}

			writeEntityToNBT(tag);
			return tag;
		} catch (Throwable e) {
			CrashReport rpt = CrashReport.makeCrashReport(e, "Saving entity NBT");
			CrashReportCategory cat = rpt.makeCategory("Entity being saved");
			addEntityCrashInfo(cat);
			throw new ReportedException(rpt);
		}
	}

	public void addEntityCrashInfo(CrashReportCategory category) {
		category.addCrashSection("类型", "IMPLIB数据容器");
		category.addCrashSection("实体ID", getEntityId());
		category.addDetail("存储的数据", () -> data.toString());
		category.addCrashSection("位置", String.format("%.2f, %.2f, %.2f", posX, posY, posZ));
	}

	public void readFromNBT(NBTTagCompound tag) {
		try {
			NBTTagList pos1 = tag.getTagList("Pos", 6);
			posX = pos1.getDoubleAt(0);
			posY = pos1.getDoubleAt(1);
			posZ = pos1.getDoubleAt(2);
			lastTickPosX = posX;
			lastTickPosY = posY;
			lastTickPosZ = posZ;
			prevPosX = posX;
			prevPosY = posY;
			prevPosZ = posZ;

			if (tag.hasUniqueId("UUID")) {
				entityUniqueID = tag.getUniqueId("UUID");
				cachedUniqueIdString = entityUniqueID.toString();
			}

			if (capabilities != null && tag.hasKey("ForgeCaps")) {
				capabilities.deserializeNBT(tag.getCompoundTag("ForgeCaps"));
			}

			readEntityFromNBT(tag);

			setPosition(posX, posY, posZ);
		} catch (Throwable var8) {
			CrashReport rpt = CrashReport.makeCrashReport(var8, "Loading entity NBT");
			CrashReportCategory cat = rpt.makeCategory("Entity being loaded");
			addEntityCrashInfo(cat);
			throw new ReportedException(rpt);
		}
	}

	@Override
	public void onUpdate() {
		onEntityUpdate();
	}

	@Override
	public void onEntityUpdate() {
		prevPosX = posX;
		prevPosY = posY;
		prevPosZ = posZ;

		firstUpdate = false;
	}
}
