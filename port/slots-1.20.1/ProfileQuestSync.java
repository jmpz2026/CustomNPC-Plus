package noppes.npcs.controllers.data.profile;

import java.util.Collections;
import java.util.Set;

/**
 * point 6: cross-slot quest sharing ("universal" quests stay completed across
 * every slot of a player).
 *
 * ============================ INTEGRATION TODO ============================
 * BetaZavr's QuestController has NO "sharedQuests" concept, and
 * PlayerQuestData.finishedQuests is `protected` (not reachable from here).
 * To enable this faithfully you must, on the BetaZavr side:
 *   1. add a way to mark a Quest as shared/universal (a field + GUI), and expose
 *      the set of shared quest ids via sharedQuestIds() below; AND
 *   2. add public accessors to PlayerQuestData for finishedQuests
 *      (get/merge completion time), so ProfileController can propagate it.
 *
 * Until then sharedQuestIds() returns empty -> the propagation in
 * ProfileController.verifySlotQuests()/shareQuestCompletion() is a safe NO-OP
 * and every slot's quests are fully independent.
 * =========================================================================
 */
public class ProfileQuestSync {

    public static Set<Integer> sharedQuestIds() {
        // TODO: return QuestController.instance shared/universal quest ids.
        return Collections.emptySet();
    }
}
