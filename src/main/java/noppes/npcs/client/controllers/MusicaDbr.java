package noppes.npcs.client.controllers;

import net.minecraft.client.audio.ISound;

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
final class MusicaDbr {

    private static boolean buscado;
    private static Object fuenteNpc;
    private static Method adoptar, parar;

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
            } catch (Throwable t) {
                fuenteNpc = null;
            }
        }
        return fuenteNpc != null;
    }
}
