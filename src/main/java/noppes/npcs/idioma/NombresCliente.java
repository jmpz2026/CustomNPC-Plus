package noppes.npcs.idioma;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

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

    private NombresCliente() {
    }

    public static String de(String nombre) {
        if (nombre == null || nombre.isEmpty() || castellano()) {
            return nombre;
        }
        String clave = PREFIJO + nombre;
        return StatCollector.canTranslate(clave) ? StatCollector.translateToLocal(clave) : nombre;
    }

    private static boolean castellano() {
        try {
            String codigo = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
            return codigo != null && codigo.toLowerCase().startsWith("es");
        } catch (Exception e) {
            return false;
        }
    }
}
