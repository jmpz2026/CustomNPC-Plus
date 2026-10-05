package noppes.npcs.controllers.data.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.SyncController;
import noppes.npcs.controllers.data.PlayerData;

import java.util.ArrayList;
import java.util.List;

/**
 * The single default profile-data type: swaps BetaZavr's PlayerData
 * (quests, dialogs, factions, bank, mail, transport, game, compass, ...).
 *
 * This is the adapter that was the "rewrite target". On BetaZavr 1.20.1 the
 * accessors line up almost 1:1 with the original, so it's tiny.
 *
 * NOTE: PlayerData is a Forge Capability here, but PlayerData.get(player) hides
 * that, exactly like the 1.7.10 controller did.
 */
public class CNPCData implements IProfileData {

    @Override
    public String getTagName() {
        return "CNPC+";
    }

    @Override
    public CompoundTag getCurrentNBT(Player player) {
        return PlayerData.get(player).getNBT();
    }

    @Override
    public void save(Player player) {
        PlayerData data = PlayerData.get(player);
        data.save(false);                       // persist to disk (async, scheduler)
        if (player instanceof ServerPlayer sp) {
            SyncController.syncPlayer(sp);       // refresh client with swapped data
        }
    }

    @Override
    public void setNBT(Player player, CompoundTag replace) {
        PlayerData data = PlayerData.get(player);
        if (replace.isEmpty()) {
            data.clear();                       // empty slot -> fresh character
        } else {
            data.setNBT(replace);
        }
        data.updateClient = true;
    }

    @Override
    public List<ProfileInfoEntry> getInfo(Player player, CompoundTag compound) {
        // player left null on purpose: PlayerData.setNBT() with a non-null player
        // would SPAWN the slot's stored companion + load storeddata. We only read.
        PlayerData pd = new PlayerData();
        pd.setNBT(compound);

        List<ProfileInfoEntry> info = new ArrayList<>();
        info.add(new ProfileInfoEntry("profile.info.quest.active", 0xf75336, pd.questData.activeQuests.size()));
        // TODO: finishedQuests and dialogsRead are protected in BetaZavr's data
        //       classes — add public size()/getters to surface those counts here.
        return info;
    }
}
