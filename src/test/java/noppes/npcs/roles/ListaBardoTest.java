package noppes.npcs.roles;

import noppes.npcs.roles.ListaBardo.Entrada;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Playlist del bardo: bolsa aleatoria, avance por reloj real y validacion de lo que llega por NBT. */
class ListaBardoTest {

    private static List<Entrada> villancicos() {
        return Arrays.asList(new Entrada("dbrmusica:navidad_deseo", 121), new Entrada("dbrmusica:navidad_cascabel", 128),
                new Entrada("dbrmusica:navidad_salon", 121), new Entrada("dbrmusica:navidad_noche", 126));
    }

    @Test
    void aleatoriaNoRepiteYCadaBolsaTocaTodas() {
        ListaBardo l = new ListaBardo();
        l.configurar(villancicos(), ListaBardo.MODO_ALEATORIO, 2000);
        long t = 1_000_000L;
        l.avanzar(t);
        int prev = l.indice();
        List<Integer> orden = new ArrayList<Integer>();
        orden.add(prev);
        for (int i = 0; i < 4000; i++) {
            t += l.entradas().get(l.indice()).ms() + 2000;
            l.avanzar(t);
            assertTrue(l.indice() != prev, "repitio seguidas en el cambio " + i);
            prev = l.indice();
            orden.add(prev);
        }
        for (int r = 0; r + 4 <= orden.size(); r += 4) {
            assertEquals(4, new HashSet<Integer>(orden.subList(r, r + 4)).size(), "bolsa " + r / 4);
        }
    }

    @Test
    void avanzaJustoEnDuracionMasSilencio() {
        ListaBardo o = new ListaBardo();
        o.configurar(villancicos(), ListaBardo.MODO_ORDEN, 2000);
        o.avanzar(0);
        assertEquals(0, o.indice());
        assertEquals(60_000, o.transcurridoMs(60_000));
        assertFalse(o.avanzar(122_999));
        assertTrue(o.avanzar(123_000));
        assertEquals(1, o.indice());
        assertEquals(0, o.transcurridoMs(123_000));
    }

    @Test
    void chunkDescargadoReinicioYRelojAtras() {
        ListaBardo o = new ListaBardo();
        o.configurar(villancicos(), ListaBardo.MODO_ORDEN, 2000);
        o.avanzar(0);
        o.avanzar(123_000);
        int sec = o.secuencia();
        // Chunk descargado 5 min: recorre las pistas que tocaban.
        o.avanzar(123_000 + 300_000);
        assertTrue(o.secuencia() > sec);
        assertTrue(o.transcurridoMs(423_000) < 130_000);
        // Parado un dia: no recorre miles de pistas, empieza otra ya.
        o.avanzar(86_400_000L);
        assertEquals(0, o.transcurridoMs(86_400_000L));
        // Reloj hacia atras: empieza ya, sin espera.
        o.avanzar(1000);
        assertEquals(0, o.transcurridoMs(1000));
    }

    @Test
    void validaLoQueLlegaPorNbt() {
        List<Entrada> malas = new ArrayList<Entrada>();
        for (int i = 0; i < 50; i++) malas.add(new Entrada("dbrmusica:p" + i, 999999));
        malas.add(0, new Entrada("DbrMusica:Mayus", 100));
        malas.add(0, new Entrada("a b", 100));
        malas.add(0, new Entrada(new String(new char[150]).replace('\0', 'a'), 100));
        malas.add(0, new Entrada("dbrmusica:corta", 1));
        ListaBardo v = new ListaBardo();
        v.configurar(malas, 7, -5);
        assertEquals(ListaBardo.MAX_PISTAS, v.entradas().size());
        assertEquals("dbrmusica:corta", v.entradas().get(0).pista);
        assertEquals(ListaBardo.SEG_MIN, v.entradas().get(0).seg);
        assertEquals(ListaBardo.SEG_MAX, v.entradas().get(1).seg);
        assertEquals(ListaBardo.MODO_ALEATORIO, v.modo());
        assertEquals(0, v.silencioMs());
        assertFalse(ListaBardo.pistaValida(null));
        assertFalse(ListaBardo.pistaValida(""));
    }

    @Test
    void listaVaciaEsElBardoDeSiempre() {
        ListaBardo e = new ListaBardo();
        e.configurar(null, 0, 0);
        assertFalse(e.avanzar(5));
        assertEquals(-1, e.indice());
        assertTrue(e.vacia());
    }

    @Test
    void guardarLaMismaListaNoCortaLaMusica() {
        ListaBardo l = new ListaBardo();
        l.configurar(villancicos(), ListaBardo.MODO_ALEATORIO, 2000);
        l.avanzar(1000);
        int idx = l.indice();
        l.configurar(villancicos(), ListaBardo.MODO_ALEATORIO, 2000);
        assertEquals(idx, l.indice());
        l.configurar(villancicos().subList(0, 2), ListaBardo.MODO_ALEATORIO, 2000);
        assertEquals(-1, l.indice());
    }
}
