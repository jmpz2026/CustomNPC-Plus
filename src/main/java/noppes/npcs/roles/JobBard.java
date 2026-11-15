package noppes.npcs.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.client.controllers.MusicaDbr;
import noppes.npcs.constants.EnumBardInstrument;
import noppes.npcs.entity.EntityNPCInterface;

import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

public class JobBard extends JobInterface {
    public int minRange = 2;
    public int maxRange = 64;

    public boolean isStreamer = true;
    public boolean hasOffRange = true;

    public String song = "";

    private EnumBardInstrument instrument = EnumBardInstrument.Banjo;

    /** Playlist DBR. Vacia = el bardo de siempre con {@link #song}. */
    public final ListaBardo lista = new ListaBardo();

    // Estado recibido del servidor (solo cliente).
    public int estadoIdx = -1;
    public int estadoSec = -1;
    /** Hora local del cliente en que empezo la pista que suena. */
    public long estadoBaseLocal;

    public JobBard(EntityNPCInterface npc) {
        super(npc);
        if (CustomItems.banjo != null) {
            mainhand = new ItemStack(CustomItems.banjo);
            overrideMainHand = overrideOffHand = true;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbttagcompound) {
        nbttagcompound.setString("BardSong", song);
        nbttagcompound.setInteger("BardMinRange", minRange);
        nbttagcompound.setInteger("BardMaxRange", maxRange);
        nbttagcompound.setInteger("BardInstrument", instrument.ordinal());
        nbttagcompound.setBoolean("BardStreamer", isStreamer);
        nbttagcompound.setBoolean("BardHasOff", hasOffRange);

        NBTTagList l = new NBTTagList();
        for (ListaBardo.Entrada e : lista.entradas()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Pista", e.pista);
            c.setInteger("Seg", e.seg);
            l.appendTag(c);
        }
        nbttagcompound.setTag("BardLista", l);
        nbttagcompound.setInteger("BardModo", lista.modo());
        nbttagcompound.setInteger("BardSilencio", lista.silencioMs());

        return nbttagcompound;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        song = nbttagcompound.getString("BardSong");
        minRange = nbttagcompound.getInteger("BardMinRange");
        maxRange = nbttagcompound.getInteger("BardMaxRange");
        setInstrument(nbttagcompound.getInteger("BardInstrument"));
        isStreamer = nbttagcompound.getBoolean("BardStreamer");
        hasOffRange = nbttagcompound.getBoolean("BardHasOff");

        // Rangos limitados (llegan tambien de la GUI y de clones). Holgados: un bardo viejo no cambia.
        minRange = ListaBardo.limitar(minRange, 0, 256);
        maxRange = ListaBardo.limitar(maxRange, 0, 256);

        List<ListaBardo.Entrada> entradas = new ArrayList<ListaBardo.Entrada>();
        NBTTagList l = nbttagcompound.getTagList("BardLista", 10);
        for (int i = 0; i < l.tagCount() && i < ListaBardo.MAX_PISTAS * 2; i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            entradas.add(new ListaBardo.Entrada(c.getString("Pista"), c.getInteger("Seg")));
        }
        int silencio = nbttagcompound.hasKey("BardSilencio")
                ? nbttagcompound.getInteger("BardSilencio") : ListaBardo.SILENCIO_DEF_MS;
        lista.configurar(entradas, nbttagcompound.getInteger("BardModo"), silencio);
    }

    /**
     * Servidor, cada tick del NPC: pone la playlist al dia y, si cambio la pista, reenvia el NPC a
     * los clientes que lo ven (spawn data con el estado). Una cuenta por tick, nada mas.
     */
    public void tickServidor() {
        if (lista.vacia() || npc.worldObj.isRemote) return;
        if (lista.avanzar(System.currentTimeMillis())) npc.updateClient = true;
    }

    /** Servidor: estado de ejecucion para el spawn data. No va a disco ni a los clones. */
    public void escribirEstado(NBTTagCompound c) {
        if (lista.vacia() || npc.worldObj == null || npc.worldObj.isRemote) return;
        lista.avanzar(System.currentTimeMillis());
        c.setInteger("BardIdx", lista.indice());
        c.setInteger("BardSec", lista.secuencia());
        c.setLong("BardTransc", lista.transcurridoMs(System.currentTimeMillis()));
    }

    /** Cliente: lee el estado del spawn data. El reloj es el local, no el del servidor. */
    public void leerEstado(NBTTagCompound c) {
        if (!c.hasKey("BardIdx")) {
            estadoIdx = -1;
            return;
        }
        int idx = c.getInteger("BardIdx");
        estadoIdx = (idx >= 0 && idx < lista.entradas().size()) ? idx : -1;
        estadoSec = c.getInteger("BardSec");
        long transc = Math.max(0L, c.getLong("BardTransc"));
        estadoBaseLocal = System.currentTimeMillis() - transc;
    }

    public void setInstrument(int i) {
        if (CustomItems.banjo == null)
            return;
        instrument = EnumBardInstrument.values()[i];
        overrideMainHand = overrideOffHand = instrument != EnumBardInstrument.None;
        switch (instrument) {
            case None:
                this.mainhand = null;
                this.offhand = null;
                break;
            case Banjo:
                this.mainhand = new ItemStack(CustomItems.banjo);
                this.offhand = null;
                break;
            case Violin:
                this.mainhand = new ItemStack(CustomItems.violin);
                this.offhand = new ItemStack(CustomItems.violinbow);
                break;
            case Guitar:
                this.mainhand = new ItemStack(CustomItems.guitar);
                this.offhand = null;
                break;
            case Harp:
                this.mainhand = new ItemStack(CustomItems.harp);
                this.offhand = null;
                break;
            case FrenchHorn:
                this.mainhand = new ItemStack(CustomItems.frenchHorn);
                this.offhand = null;
                break;
        }
    }

    public EnumBardInstrument getInstrument() {
        return instrument;
    }

    // ---- Cliente: playlist DBR (PLAN-fase6-npc-musica.md) ----

    /** Fundido al entrar o salir del rango. */
    private static final int FUNDIDO_RANGO_MS = 2000;
    /** Fundido de entrada de una pista nueva (y de salida de la vieja si aun sonaba). */
    private static final int FUNDIDO_PISTA_MS = 3000;
    /** Cerca del final no se empieza: sonaria un trozo suelto. */
    private static final long MARGEN_FINAL_MS = 4000L;
    /** Reintento si la pista dejo de sonar sola (otro mod la corto). */
    private static final long REINTENTO_MS = 5000L;
    /**
     * Si una pista se corta antes de esto tras tocarla, el salto cayo pasado su final real (el Seg
     * de la lista es mayor que la duracion): no se reintenta hasta la pista siguiente.
     */
    private static final long CORTE_INMEDIATO_MS = 2000L;

    /** Bardo con playlist que suena en este cliente (uno a la vez, el mas cercano). */
    private static JobBard sonando;
    private static int secSonando = Integer.MIN_VALUE;
    /** Camino viejo (sin gestor DBR): la pista se toca con el MusicController, desde 0. */
    private static boolean sonandoViejo;
    private static long ultimoIntento;
    /** Primera vez que se vio cortada la pista que suena (0 = sonando). */
    private static long cortadaEn;
    /** La pista actual ya se acabo de verdad: no reintentar hasta que cambie la secuencia. */
    private static boolean agotada;

    /** Posicion en ms de la pista que toca segun el servidor, con el reloj local. */
    private long posicionMs() {
        return System.currentTimeMillis() - estadoBaseLocal;
    }

    private ListaBardo.Entrada entradaActual() {
        List<ListaBardo.Entrada> l = lista.entradas();
        return (estadoIdx >= 0 && estadoIdx < l.size()) ? l.get(estadoIdx) : null;
    }

    /**
     * Toca la pista actual desde donde va. Si cae en el silencio entre pistas o muy al final, no toca
     * nada: la siguiente llega con el aviso del servidor.
     */
    private void tocarActual(int fundidoMs) {
        ListaBardo.Entrada e = entradaActual();
        if (estadoSec != secSonando) agotada = false;
        secSonando = estadoSec;
        ultimoIntento = System.currentTimeMillis();
        cortadaEn = 0L;
        if (e == null) return;
        long pos = posicionMs();
        if (pos >= e.ms() - MARGEN_FINAL_MS) return;
        if (MusicController.Instance.isPlaying()) MusicController.Instance.stopMusic();
        if (MusicaDbr.tocarDesde(e.pista, (int) pos, fundidoMs)) {
            sonandoViejo = false;
        } else {
            // Sin gestor DBR o sin tocarDesde: el reproductor viejo, desde 0. Nunca silencio.
            sonandoViejo = true;
            MusicController.Instance.playMusicJukebox(e.pista, npc, maxRange);
        }
    }

    private static void pararPlaylist(int fundidoMs) {
        if (sonando == null) return;
        if (sonandoViejo) MusicController.Instance.stopMusic();
        else MusicaDbr.pararCon(fundidoMs);
        sonando = null;
        secSonando = Integer.MIN_VALUE;
        sonandoViejo = false;
    }

    /**
     * Cada tick de cliente (ClientTickHandler): corta la playlist si su NPC desaparecio, cambio de
     * mundo o el jugador se fue lejos sin que el NPC tickeara (teletransporte).
     */
    public static void vigilarCliente() {
        JobBard b = sonando;
        if (b == null) return;
        EntityPlayer p = CustomNpcs.proxy.getPlayer();
        if (p == null || b.npc.isDead || b.npc.worldObj != p.worldObj || b.lista.vacia()
                || b.npc.getDistanceToEntity(p) > b.maxRange + 2) {
            pararPlaylist(FUNDIDO_RANGO_MS);
        }
    }

    private void tickPlaylist() {
        EntityPlayer p = CustomNpcs.proxy.getPlayer();
        if (p == null || estadoIdx < 0) return;
        double dist = npc.getDistanceToEntity(p);
        if (sonando == this) {
            if (dist > maxRange) {
                pararPlaylist(FUNDIDO_RANGO_MS);
            } else if (estadoSec != secSonando) {
                tocarActual(FUNDIDO_PISTA_MS);
            } else if (!agotada
                    && (sonandoViejo ? !MusicController.Instance.isPlaying() : !MusicaDbr.activaNpc())) {
                long ahora = System.currentTimeMillis();
                // Medio segundo de gracia: el sonido puede tardar un tick en figurar como activo.
                if (cortadaEn == 0L && ahora - ultimoIntento >= 500L) cortadaEn = ahora;
                if (cortadaEn == 0L) {
                    // aun en la gracia
                } else if (cortadaEn - ultimoIntento < CORTE_INMEDIATO_MS) {
                    // Se acabo al empezar: el Seg es mayor que la pista real. Espera la siguiente.
                    agotada = true;
                } else if (ahora - ultimoIntento > REINTENTO_MS) {
                    // Se corto sola (no por el final): vuelve donde va.
                    tocarActual(FUNDIDO_RANGO_MS);
                }
            }
            if (!sonandoViejo) Minecraft.getMinecraft().mcMusicTicker.field_147676_d = 12000;
            return;
        }
        if (dist > minRange) return;
        if (sonando != null && sonando.npc.getDistanceToEntity(p) <= dist) return;
        if (sonando != null) pararPlaylist(FUNDIDO_RANGO_MS);
        sonando = this;
        tocarActual(FUNDIDO_RANGO_MS);
    }

    public void onLivingUpdate() {
        if (!npc.isRemote())
            return;
        if (!lista.vacia()) {
            tickPlaylist();
            return;
        }
        if (song.isEmpty())
            return;

        if (!MusicController.Instance.isPlaying() && Minecraft.getMinecraft().currentScreen == null) {
            if (!this.play()) return;
        } else if (MusicController.Instance.getEntity() != npc) {
            EntityPlayer player = CustomNpcs.proxy.getPlayer();
            double distanceToPlayer = npc.getDistanceToEntity(player);
            double distanceToMusic = MusicController.Instance.getDistance();
            if (Math.ceil(distanceToPlayer) < Math.ceil(distanceToMusic)) {
                if (!this.play()) return;
            }
        } else if (hasOffRange) {
            List<EntityPlayer> list = npc.worldObj.getEntitiesWithinAABB(EntityPlayer.class, npc.boundingBox.expand(maxRange, maxRange, maxRange));
            if (!list.contains(CustomNpcs.proxy.getPlayer()))
                MusicController.Instance.stopMusic();
        }

        if (MusicController.Instance.isPlaying(song)) {
            Minecraft.getMinecraft().mcMusicTicker.field_147676_d = 12000;
        }
    }

    private boolean play() {
        List<EntityPlayer> list = npc.worldObj.getEntitiesWithinAABB(EntityPlayer.class, npc.boundingBox.expand(minRange, minRange, minRange));
        if (!list.contains(CustomNpcs.proxy.getPlayer()))
            return false;

        if (isStreamer)
            MusicController.Instance.playMusicJukebox(song, npc, hasOffRange ? maxRange : 0);
        else
            MusicController.Instance.playMusicBackground(song, npc, hasOffRange ? maxRange : 0);
        return true;
    }

    @Override
    public void killed() {
        delete();
    }

    @Override
    public void delete() {
        if (npc.worldObj.isRemote && sonando == this) {
            pararPlaylist(FUNDIDO_RANGO_MS);
            return;
        }
        if (npc.worldObj.isRemote && hasOffRange) {
            if (MusicController.Instance.isPlaying(song)) {
                MusicController.Instance.stopAllSounds();
            }
        }
    }
}
