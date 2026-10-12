package noppes.npcs;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.constants.EnumQuestCompletion;
import noppes.npcs.controllers.PlayerQuestController;
import noppes.npcs.controllers.data.Party;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.Quest;

import java.util.HashMap;
import java.util.Vector;

public class QuestLogData {
    public String trackedQuestKey = "";
    public HashMap<String, Vector<String>> categories = new HashMap<String, Vector<String>>();
    public String selectedQuest = "";
    public String selectedCategory = "";
    public HashMap<String, String> questText = new HashMap<>();
    public HashMap<String, String> questAlerts = new HashMap<>();
    public HashMap<String, Vector<String>> questStatus = new HashMap<>();
    public HashMap<String, String> finish = new HashMap<>();
    public HashMap<String, Integer> partyQuests = new HashMap<>();
    public HashMap<String, Vector<String>> partyOptions = new HashMap<>();

    public NBTTagCompound writeNBT() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("Categories", NBTTags.nbtVectorMap(categories));
        compound.setTag("Logs", NBTTags.nbtStringStringMap(questText));
        compound.setTag("Alerts", NBTTags.nbtStringStringMap(questAlerts));
        compound.setTag("Status", NBTTags.nbtVectorMap(questStatus));
        compound.setTag("QuestFinisher", NBTTags.nbtStringStringMap(finish));
        compound.setString("TrackedQuestID", trackedQuestKey);
        compound.setTag("PartyQuests", NBTTags.nbtStringIntegerMap(partyQuests));
        compound.setTag("PartyOptions", NBTTags.nbtVectorMap(partyOptions));
        return compound;
    }

    public void readNBT(NBTTagCompound compound) {
        categories = NBTTags.getVectorMap(compound.getTagList("Categories", 10));
        questText = NBTTags.getStringStringMap(compound.getTagList("Logs", 10));
        questAlerts = NBTTags.getStringStringMap(compound.getTagList("Alerts", 10));
        questStatus = NBTTags.getVectorMap(compound.getTagList("Status", 10));
        finish = NBTTags.getStringStringMap(compound.getTagList("QuestFinisher", 10));
        trackedQuestKey = compound.getString("TrackedQuestID");
        partyQuests = NBTTags.getStringIntegerMap(compound.getTagList("PartyQuests", 10));
        partyOptions = NBTTags.getVectorMap(compound.getTagList("PartyOptions", 10));
    }

    public NBTTagCompound writeTrackedQuest() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString("TrackedQuestID", trackedQuestKey);
        return compound;
    }

    public void readTrackedQuest(NBTTagCompound compound) {
        trackedQuestKey = compound.getString("TrackedQuestID");
    }

    public void setData(EntityPlayer player) {
        PlayerData playerData = PlayerData.get(player);

        for (Quest quest : PlayerQuestController.getActiveQuests(player)) {
            // Categoria y titulo en el idioma del jugador: el cliente los muestra y devuelve
            // la clave al seguir la mision (NoppesUtilPlayer.getQuestFromStringKey la resuelve).
            String key = noppes.npcs.idioma.Traducciones.clave(player, quest);
            String category = key.substring(0, key.indexOf(':'));
            if (!categories.containsKey(category))
                categories.put(category, new Vector<String>());
            Vector<String> list = categories.get(category);
            list.add(key.substring(key.indexOf(':') + 1));

            questText.put(key, noppes.npcs.idioma.Traducciones.registro(player, quest));
            questAlerts.put(key, String.valueOf(playerData.questData.activeQuests.get(quest.id).sendAlerts));
            questStatus.put(key, noppes.npcs.idioma.Traducciones.objetivos(player, quest.questInterface.getQuestLogStatus(player)));
            if (quest.completion == EnumQuestCompletion.Npc && quest.questInterface.isCompleted(playerData))
                finish.put(key, noppes.npcs.idioma.Traducciones.nombre(player, quest.completerNpc));

            if (playerData.questData.getTrackedQuest() != null) {
                if (quest.id == playerData.questData.getTrackedQuest().getId()) {
                    trackedQuestKey = key;
                }
            }

            if (quest.partyOptions.allowParty) {
                partyQuests.put(key, quest.id);
                partyOptions.put(key, quest.partyOptions.getPartyOptionsList());
            }
        }

        Party party = playerData.getPlayerParty();
        if (party != null && party.getQuest() != null) {
            if (playerData.questData.getTrackedQuest() != null) {
                if (party.getQuest().getId() == playerData.questData.getTrackedQuest().getId()) {
                    trackedQuestKey = "P" + ":" + noppes.npcs.idioma.Traducciones.clave(player, (Quest) party.getQuest());
                }
            }
        }
    }

    public void setTrackedQuestKey(EntityPlayer player) {
        PlayerData playerData = PlayerData.get(player);

        Party party = playerData.getPlayerParty();
        if (party != null && party.getQuest() != null) {
            if (playerData.questData.getTrackedQuest() != null) {
                if (party.getQuest().getId() == playerData.questData.getTrackedQuest().getId()) {
                    trackedQuestKey = "P" + ":" + noppes.npcs.idioma.Traducciones.clave(player, (Quest) party.getQuest());
                }
            }
        } else if (playerData.questData != null && playerData.questData.getTrackedQuest() != null) {
            IQuest quest = playerData.questData.getTrackedQuest();
            trackedQuestKey = noppes.npcs.idioma.Traducciones.clave(player, (Quest) quest);
        } else {
            trackedQuestKey = "";
        }
    }

    public boolean hasSelectedQuest() {
        return !selectedQuest.isEmpty();
    }

    public String getQuestText() {
        return questText.get(selectedCategory + ":" + selectedQuest);
    }

    public Boolean getQuestAlerts() {
        return Boolean.valueOf(questAlerts.get(selectedCategory + ":" + selectedQuest));
    }

    public void toggleQuestAlerts() {
        questAlerts.put(selectedCategory + ":" + selectedQuest, String.valueOf(!getQuestAlerts()));
    }

    public Vector<String> getQuestStatus() {
        return questStatus.get(selectedCategory + ":" + selectedQuest);
    }

    public String getComplete() {
        return finish.get(selectedCategory + ":" + selectedQuest);
    }

    public Vector<String> getPartyOptions() {
        return partyOptions.get(selectedCategory + ":" + selectedQuest);
    }
}
