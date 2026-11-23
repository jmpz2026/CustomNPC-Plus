package noppes.npcs.ai;

import kamkeel.npcs.addon.DBCAddon;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.config.ConfigMain;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public class CombatHandler {

    private Map<EntityLivingBase, Float> aggressors = new HashMap<EntityLivingBase, Float>();
    private Map<EntityLivingBase, LinkedList<Float>> recentDamages = new HashMap<>();
    private EntityNPCInterface npc;
    private long startTime = 0;
    private int combatResetTimer = 0;
    /**
     * Dbr (GENERAL UPDATE P2): atacantes que el NPC dejo por lejos o por no poder llegar. Sus
     * golpes no lo meten en combate (regenera con healthRegen) hasta que vuelva a tenerlos a tiro.
     */
    private final Map<EntityLivingBase, Boolean> fuera = new WeakHashMap<EntityLivingBase, Boolean>();
    /** Ticks seguidos sin tener al objetivo a tiro. */
    private int sinLlegar = 0;

    public CombatHandler(EntityNPCInterface npc) {
        this.npc = npc;
    }

    public void update() {
        if (npc.isKilled()) {
            if (npc.isAttacking()) {
                reset();
            }
            return;
        }
        if (persigue()) vigilarPersecucion();
        if (npc.getAttackTarget() != null && !npc.isAttacking()) {
            start();
        }

        if (!shouldCombatContinue()) {
            if (combatResetTimer++ > 40) {
                reset();
            }
            return;
        }
        combatResetTimer = 0;
    }


    private boolean shouldCombatContinue() {
        if (npc.getAttackTarget() == null)
            return false;
        return isValidTarget(npc.getAttackTarget());
    }

    public void damage(DamageSource source, float damageAmount) {
        Entity e = NoppesUtilServer.GetDamageSource(source);
        // Dbr P2: el golpe de alguien que el NPC dejo no alarga el combate
        if (!(e instanceof EntityLivingBase && estaFuera((EntityLivingBase) e)))
            combatResetTimer = 0;

        if (e instanceof EntityLivingBase) {
            EntityLivingBase el = (EntityLivingBase) e;

            // Update recent damages
            LinkedList<Float> recentDamageList = recentDamages.computeIfAbsent(el, k -> new LinkedList<>());
            recentDamageList.addLast(damageAmount);
            if (recentDamageList.size() > 15) {
                recentDamageList.removeFirst();
            }

            // Update total aggressor damage
            Float f = aggressors.get(el);
            if (f == null) {
                f = 0f;
            }
            aggressors.put(el, f + damageAmount);
        }
    }

    public void start() {
        combatResetTimer = 0;
        startTime = npc.worldObj.getWorldInfo().getWorldTotalTime();
        npc.setBoolFlag(true, 4);
    }

    public void reset() {
        combatResetTimer = 0;
        startTime = 0;
        aggressors.clear();
        recentDamages.clear();
        npc.setBoolFlag(false, 4);
    }

    public boolean checkTarget() {
        if (aggressors.isEmpty() || npc.ticksExisted % 10 != 0)
            return false;
        EntityLivingBase target = npc.getAttackTarget();
        Float current = 0f;
        if (isValidTarget(target)) {
            current = aggressors.get(target);
            if (current == null)
                current = 0f;
        } else
            target = null;
        for (Map.Entry<EntityLivingBase, Float> entry : aggressors.entrySet()) {
            if (entry.getValue() > current && isValidTarget(entry.getKey())) {
                current = entry.getValue();
                target = entry.getKey();
            }
        }
        return target == null;
    }

    public boolean isValidTarget(EntityLivingBase target) {
        if (target == null || !target.isEntityAlive())
            return false;

        if (target instanceof EntityPlayer && (((EntityPlayer) target).capabilities.disableDamage || DBCAddon.instance.isKO(npc, (EntityPlayer) target)))
            return false;

        if (estaFuera(target))
            return false;
        return npc.isInRange(target, rango(target));
    }

    // ------------------------------------------------------------ Dbr (GENERAL UPDATE P2)

    /** Solo los NPC que se defienden (onAttack 0). Los pasivos y los 'quieto' de Universos (3) no. */
    public boolean persigue() {
        return ConfigMain.DbrPersecucion && npc.ais.onAttack == 0;
    }

    /**
     * Distancia hasta la que el NPC sigue a un objetivo: a quien le pego, hasta NpcNavRange (32);
     * a los demas, su aggroRange como siempre.
     */
    public double rango(EntityLivingBase target) {
        if (target != null && persigue() && aggressors.containsKey(target))
            return Math.max(npc.stats.aggroRange, ConfigMain.NpcNavRange);
        return npc.stats.aggroRange;
    }

    /** El NPC dejo a este atacante: no lo vuelve a fijar hasta tenerlo a tiro. */
    public boolean estaFuera(EntityLivingBase e) {
        return e != null && fuera.containsKey(e);
    }

    /** Lo tiene a tiro: cuerpo a cuerpo con margen, o a distancia con proyectil y viendolo. */
    public boolean aTiro(EntityLivingBase t) {
        double d2 = npc.getDistanceSq(t.posX, t.boundingBox.minY, t.posZ);
        double melee = npc.stats.attackRange * npc.stats.attackRange + t.width;
        double minimo = npc.width * 2.0F * npc.width * 2.0F + t.width;
        double alcance = Math.sqrt(Math.max(melee, minimo)) + 1.5;
        if (d2 <= alcance * alcance)
            return true;
        if (npc.inventory.getProjectile() != null && npc.ais.useRangeMelee != 1) {
            double r = npc.stats.rangedRange;
            return d2 <= r * r && npc.canSee(t);
        }
        return false;
    }

    private void vigilarPersecucion() {
        if (!fuera.isEmpty() && npc.ticksExisted % 20 == 0) {
            Iterator<Map.Entry<EntityLivingBase, Boolean>> it = fuera.entrySet().iterator();
            while (it.hasNext()) {
                EntityLivingBase e = it.next().getKey();
                // vuelve a contar si murio, se fue del mundo o el NPC lo tiene a tiro otra vez
                if (e == null || !e.isEntityAlive() || e.worldObj != npc.worldObj
                        || npc.getDistanceToEntity(e) <= ConfigMain.NpcNavRange && aTiro(e))
                    it.remove();
            }
        }
        EntityLivingBase t = npc.getAttackTarget();
        if (t == null || !aggressors.containsKey(t)) {
            sinLlegar = 0;
            return;
        }
        if (npc.getDistanceToEntity(t) > ConfigMain.NpcNavRange) {
            dejar(t);
            return;
        }
        if (aTiro(t)) {
            sinLlegar = 0;
        } else if (++sinLlegar >= Math.max(1, ConfigMain.DbrSegundosSinLlegar) * 20) {
            dejar(t);
        }
    }

    /** Sale de combate con este atacante aunque le siga pegando: regenera con healthRegen. */
    private void dejar(EntityLivingBase t) {
        fuera.put(t, Boolean.TRUE);
        sinLlegar = 0;
        npc.setAttackTarget(null);
        reset();
    }

    public float calculateThreatLevel(EntityLivingBase entity) {
        float threatLevel = 0.0f;
        LinkedList<Float> recentDamageList = recentDamages.get(entity);

        if (recentDamageList != null) {
            long currentTime = npc.worldObj.getWorldInfo().getWorldTotalTime();
            for (Float damage : recentDamageList) {
                float decayFactor = Math.max(0, 1 - (float) startTime / currentTime);

                threatLevel += damage * decayFactor;
            }
        }

        return threatLevel;
    }

    public boolean shouldChangeTarget(double chance) {
        // Assuming randomNum is a random number between 0 and 100
        double randomNum = Math.random() * 100; // Generates a random number between 0 and 100
        return randomNum < chance;
    }

    public boolean shouldSwitchTactically(EntityLivingBase originalTarget, EntityLivingBase newTarget, boolean reverse) {
        float currentThreat = calculateThreatLevel(originalTarget);
        float newThreat = calculateThreatLevel(newTarget);
        if (reverse) {
            return newThreat < currentThreat;
        }
        return newThreat > currentThreat;
    }
}
