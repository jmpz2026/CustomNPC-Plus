package noppes.npcs.client.controllers;

import net.minecraft.client.audio.ISound;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Method;

/**
 * Puente con el gestor de musica del pack Dbr ({@code dbr.machitos.core.audio.Musica} de
 * DbrServerPack, {@code PLAN-musica-central.md}). La musica de NPC (bardo y fondo) entra en su capa
 * {@code NPC}, la de menos prioridad: la tapan las pistas del servidor, las pantallas y las
 * cinematicas, y la escala el slider «Musica del servidor» en vez de «Jugadores».
 *
 * <p>Por reflexion, con los {@code Method} en cache: sin el pack Dbr esto no hace nada y la musica
 * suena como siempre.
 */
public final class MusicaDbr {

    private static boolean buscado;
    private static Object fuenteNpc;
    private static Method adoptar, parar, tocarDesde, hayGestor, activa;

    private MusicaDbr() {
    }

    /** Antes de {@code playSound}: el gestor pasa el sonido a MASTER antes de que nazca. */
    static void adoptar(ISound sonido) {
        if (!buscar()) return;
        try {
            adoptar.invoke(null, fuenteNpc, sonido);
        } catch (Throwable ignored) {
        }
    }

    static void parar() {
        if (!buscar()) return;
        try {
            parar.invoke(null, fuenteNpc, 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Hay gestor y sabe empezar a mitad de pista (DbrCore con {@code tocarDesde}). Si no, el bardo
     * con playlist usa el {@link MusicController} viejo, desde 0.
     */
    public static boolean puedeSaltar() {
        if (!buscar() || tocarDesde == null || hayGestor == null) return false;
        try {
            return Boolean.TRUE.equals(hayGestor.invoke(null));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Toca {@code pista} en la capa NPC empezando en {@code desdeMs}. false si no se pudo. */
    public static boolean tocarDesde(String pista, int desdeMs, int fundidoMs) {
        if (!puedeSaltar()) return false;
        try {
            Object r = tocarDesde.invoke(null, fuenteNpc, new ResourceLocation(pista), false,
                    Math.max(0, desdeMs), Math.max(0, fundidoMs), 1f);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Para la capa NPC con fundido. */
    public static void pararCon(int fundidoMs) {
        if (!buscar()) return;
        try {
            parar.invoke(null, fuenteNpc, Math.max(0, fundidoMs));
        } catch (Throwable ignored) {
        }
    }

    /** Suena algo en la capa NPC. */
    public static boolean activaNpc() {
        if (!buscar() || activa == null) return false;
        try {
            return Boolean.TRUE.equals(activa.invoke(null, fuenteNpc));
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean buscar() {
        if (!buscado) {
            buscado = true;
            try {
                Class<?> c = Class.forName("dbr.machitos.core.audio.Musica");
                Class<? extends Enum> f = (Class<? extends Enum>) Class.forName("dbr.machitos.core.audio.Musica$Fuente");
                adoptar = c.getMethod("adoptar", f, Object.class);
                parar = c.getMethod("parar", f, int.class);
                fuenteNpc = Enum.valueOf(f, "NPC");
                // Opcionales: un DbrCore viejo no los tiene y el bardo usa el camino viejo.
                try {
                    tocarDesde = c.getMethod("tocarDesde", f, ResourceLocation.class, boolean.class,
                            int.class, int.class, float.class);
                    hayGestor = c.getMethod("hayGestor");
                    activa = c.getMethod("activa", f);
                } catch (Throwable t) {
                    tocarDesde = null;
                }
            } catch (Throwable t) {
                fuenteNpc = null;
            }
        }
        return fuenteNpc != null;
    }
}
