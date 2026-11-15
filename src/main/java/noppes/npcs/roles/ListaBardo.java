package noppes.npcs.roles;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Playlist del bardo (DBR, {@code CHRISTMAS UPDATE/PLAN-fase6-npc-musica.md}). Java puro, sin
 * clases de Minecraft, para poder probarla sola.
 *
 * <p>El servidor manda: elige la pista y guarda cuando empezo con el reloj real
 * ({@link System#currentTimeMillis()}), que no se atrasa con el lag. {@link #avanzar} pone al dia
 * el estado aunque el NPC haya estado sin tickear (chunk descargado). El estado de ejecucion no se
 * guarda en disco: tras un reinicio empieza una pista nueva.
 *
 * <p>Todo lo que entra se valida aqui ({@link #pistaValida}, {@link #limitar}); da igual si viene
 * de la GUI, de un clon o de un NBT manipulado.
 */
public final class ListaBardo {

    public static final int MAX_PISTAS = 32;
    public static final int MAX_LARGO_PISTA = 100;
    public static final int SEG_MIN = 5;
    public static final int SEG_MAX = 1200;
    public static final int SILENCIO_MAX_MS = 10000;
    public static final int SILENCIO_DEF_MS = 2000;

    public static final int MODO_ORDEN = 0;
    public static final int MODO_ALEATORIO = 1;

    /** Si el NPC estuvo parado mas de tantas pistas, no se recorren una a una: se empieza otra. */
    private static final int MAX_AVANCES = MAX_PISTAS * 4;

    private static final Pattern PISTA = Pattern.compile("[a-z0-9_.:/-]+");
    private static final Random AZAR = new SecureRandom();

    public static final class Entrada {
        public final String pista;
        public final int seg;

        public Entrada(String pista, int seg) {
            this.pista = pista;
            this.seg = seg;
        }

        public long ms() {
            return seg * 1000L;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entrada)) return false;
            Entrada e = (Entrada) o;
            return seg == e.seg && pista.equals(e.pista);
        }

        @Override
        public int hashCode() {
            return pista.hashCode() * 31 + seg;
        }
    }

    private final List<Entrada> entradas = new ArrayList<Entrada>();
    private int modo = MODO_ALEATORIO;
    private int silencioMs = SILENCIO_DEF_MS;

    // ---- estado de ejecucion (no va a disco) ----
    private int idx = -1;
    private long inicioMs;
    private int sec;
    private final List<Integer> bolsa = new ArrayList<Integer>();

    public static boolean pistaValida(String p) {
        return p != null && !p.isEmpty() && p.length() <= MAX_LARGO_PISTA && PISTA.matcher(p).matches();
    }

    public static int limitar(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    /**
     * Cambia la configuracion. Lo invalido se descarta. Si la lista cambia, el estado se reinicia
     * (en el siguiente {@link #avanzar} empieza una pista nueva).
     */
    public void configurar(List<Entrada> nuevas, int modo, int silencioMs) {
        List<Entrada> limpias = new ArrayList<Entrada>();
        if (nuevas != null) {
            for (Entrada e : nuevas) {
                if (limpias.size() >= MAX_PISTAS) break;
                if (e == null || !pistaValida(e.pista)) continue;
                limpias.add(new Entrada(e.pista, limitar(e.seg, SEG_MIN, SEG_MAX)));
            }
        }
        this.modo = (modo == MODO_ORDEN) ? MODO_ORDEN : MODO_ALEATORIO;
        this.silencioMs = limitar(silencioMs, 0, SILENCIO_MAX_MS);
        if (!limpias.equals(entradas)) {
            entradas.clear();
            entradas.addAll(limpias);
            reiniciar();
        }
    }

    public void reiniciar() {
        idx = -1;
        bolsa.clear();
    }

    /**
     * Pone el estado al dia con la hora {@code ahora}.
     *
     * @return {@code true} si cambio la pista (hay que avisar a los clientes)
     */
    public boolean avanzar(long ahora) {
        if (entradas.isEmpty()) {
            boolean habia = idx >= 0;
            idx = -1;
            return habia;
        }
        if (idx < 0 || idx >= entradas.size()) {
            idx = -1;
            elegir();
            inicioMs = ahora;
            return true;
        }
        boolean cambio = false;
        int avances = 0;
        while (ahora - inicioMs >= entradas.get(idx).ms() + silencioMs) {
            if (++avances > MAX_AVANCES) {
                // Parado mucho tiempo: no hace falta recorrer todo, empieza otra ya.
                elegir();
                inicioMs = ahora;
                return true;
            }
            inicioMs += entradas.get(idx).ms() + silencioMs;
            elegir();
            cambio = true;
        }
        if (ahora < inicioMs) {
            // El reloj del servidor fue hacia atras: no se espera, se empieza ya.
            inicioMs = ahora;
            cambio = true;
        }
        return cambio;
    }

    private void elegir() {
        int n = entradas.size();
        int anterior = idx;
        if (n == 1) {
            idx = 0;
        } else if (modo == MODO_ORDEN) {
            idx = (anterior + 1) % n;
        } else {
            if (bolsa.isEmpty()) {
                for (int i = 0; i < n; i++) bolsa.add(i);
                Collections.shuffle(bolsa, AZAR);
                // Al rellenar, la primera no puede ser la que acaba de sonar.
                if (bolsa.get(0) == anterior) Collections.swap(bolsa, 0, 1 + AZAR.nextInt(n - 1));
            }
            idx = bolsa.remove(0);
        }
        sec++;
    }

    // ---- lectura ----

    public List<Entrada> entradas() {
        return Collections.unmodifiableList(entradas);
    }

    public boolean vacia() {
        return entradas.isEmpty();
    }

    public int modo() {
        return modo;
    }

    public int silencioMs() {
        return silencioMs;
    }

    /** Pista que suena, o -1. */
    public int indice() {
        return idx;
    }

    /** Sube con cada cambio de pista: el cliente lo usa para saber que hay que cambiar. */
    public int secuencia() {
        return sec;
    }

    public long transcurridoMs(long ahora) {
        return idx < 0 ? 0 : Math.max(0, ahora - inicioMs);
    }
}
