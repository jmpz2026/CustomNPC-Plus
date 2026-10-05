package noppes.npcs.controllers.data.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Pluggable per-slot data component (1.20.1).
 *
 * Dropped vs 1.7.10: getSwitchPriority() + verifySwitch() — their only users were
 * the Party guard and Attribute checks, both set aside.
 * Kept: getInfo() (point 8).
 */
public interface IProfileData {

    String getTagName();

    CompoundTag getCurrentNBT(Player player);

    void save(Player player);

    void setNBT(Player player, CompoundTag replace);

    /** point 8: info lines shown in the slot GUI for a stored slot. */
    List<ProfileInfoEntry> getInfo(Player player, CompoundTag compound);
}
