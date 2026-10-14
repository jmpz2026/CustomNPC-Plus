package noppes.npcs.idioma;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nombre y titulo de un NPC en el idioma del cliente (DBR, LANGUAGE UPDATE). Solo cliente.
 *
 * <p>El staff pone el nombre en castellano ("Raditz Corrupto [Jefe]"). Las traducciones van en
 * el .lang de customnpcs del DbrServerPack con la clave {@code dbr.nombre.<nombre>} (las arma
 * {@code LANGUAGE UPDATE/tools/cnpc_nombres.py}). Sin clave se ve el nombre tal cual.
 *
 * <p>Un cliente en castellano ve siempre el original: Minecraft carga en_US debajo de cualquier
 * idioma, asi que sin este corte un es_ES veria la clave inglesa.
 *
 * <p>Solo para mostrar: el servidor compara nombres (objetivos de matar, comandos) con el
 * original, y eso no pasa por aqui.
 */
public final class NombresCliente {

    private static final String PREFIJO = "dbr.nombre.";

    // Se pide en cada frame por cada nombre dibujado; cambiar de idioma recarga los recursos y lo vacia
    private static final Map<String, String> traducidos = new ConcurrentHashMap<>();
    private static volatile Boolean esCastellano;

    private NombresCliente() {
    }

    public static String de(String nombre) {
        if (nombre == null || nombre.isEmpty() || castellano()) {
            return nombre;
        }
        String traducido = traducidos.get(nombre);
        if (traducido == null) {
            String clave = PREFIJO + nombre;
            traducido = StatCollector.canTranslate(clave) ? StatCollector.translateToLocal(clave) : nombre;
            traducidos.put(nombre, traducido);
        }
        return traducido;
    }

    public static void limpiar() {
        traducidos.clear();
        esCastellano = null;
    }

    private static boolean castellano() {
        Boolean es = esCastellano;
        if (es != null)
            return es;
        try {
            String codigo = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
            es = codigo != null && codigo.toLowerCase().startsWith("es");
            esCastellano = es;
            return es;
        } catch (Exception e) {
            return false;
        }
    }
}
