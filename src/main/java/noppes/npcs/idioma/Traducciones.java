package noppes.npcs.idioma;

import com.google.gson.Gson;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.CustomNpcs;
import noppes.npcs.LogWriter;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.controllers.data.Quest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Dialogos y misiones en el idioma de Minecraft de cada jugador (DBR, LANGUAGE UPDATE).
 *
 * <p>El texto que el staff escribe en el juego es el castellano y sigue siendo el original.
 * Las traducciones viven en {@code customnpcs/lang/en_US.json} y {@code pt_BR.json} del mundo
 * (las arma {@code LANGUAGE UPDATE/tools/cnpc_traducciones.py}). Se cambia solo lo que sale
 * hacia el jugador (el NBT del paquete, el aviso de chat, el registro de misiones): los
 * objetos del servidor no se tocan.
 *
 * <p>Cada entrada guarda la huella (CRC32) del castellano del que salio. Si alguien edita el
 * dialogo en el juego, la huella ya no coincide y se muestra el original hasta que se vuelva
 * a traducir: mejor el castellano nuevo que una traduccion vieja.
 *
 * <p>Idioma: es_* ve el original; pt_* ve pt_BR; cualquier otro, en_US.
 */
public final class Traducciones {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String[] IDIOMAS = {"en_US", "pt_BR"};

    /** Una entrada de dialogo o mision. Campos con los nombres del archivo. */
    static final class Entrada {
        String h;
        String titulo;
        String texto;
        Map<String, String> opciones;
        String registro;
        String completado;
    }

    static final class Archivo {
        Map<String, Entrada> dialogos;
        Map<String, Entrada> misiones;
        /** Nombres de NPC y de lugares, tal como los escribe el staff (objetivos de mision). */
        Map<String, String> nombres;
    }

    /**
     * Frases fijas que CustomNPCs pega a los objetivos, en ingles en el codigo. El castellano
     * tambien va aqui porque el original es el ingles: el jugador es_* las ve en castellano.
     */
    private static final String[][] FIJAS = {
            // ingles original, es_ES, pt_BR
            {"Not Found", "No encontrado", "Não encontrado"},
            {"Found", "Encontrado", "Encontrado"},
            {" (Done)", " (Hecho)", " (Feito)"},
            {" (unread)", " (sin leer)", " (não lido)"},
            {" (read)", " (leído)", " (lido)"},
            {"Completed: ", "Completado: ", "Concluído: "},
    };

    private static volatile Map<String, Archivo> cargados = Collections.emptyMap();

    private Traducciones() {
    }

    /** Lee los archivos de traduccion. Al arrancar el servidor y con /noppes reload. */
    public static void cargar() {
        Map<String, Archivo> nuevos = new HashMap<String, Archivo>();
        File dir = CustomNpcs.getWorldSaveDirectory("lang");
        if (dir != null) {
            for (String idioma : IDIOMAS) {
                File f = new File(dir, idioma + ".json");
                if (!f.isFile()) {
                    continue;
                }
                Reader r = null;
                try {
                    r = new InputStreamReader(new FileInputStream(f), UTF8);
                    Archivo a = new Gson().fromJson(r, Archivo.class);
                    if (a != null) {
                        nuevos.put(idioma, a);
                    }
                } catch (Exception e) {
                    LogWriter.error("No se pudo leer la traduccion " + f.getPath(), e);
                } finally {
                    if (r != null) {
                        try {
                            r.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        cargados = nuevos;
        viejas.clear();
        LogWriter.info("Traducciones de dialogos y misiones: " + nuevos.keySet());
    }

    // ------------------------------------------------------------------ idioma

    /** es_ES, en_US o pt_BR segun el idioma de Minecraft del jugador; null sin dato. */
    static String idiomaCompleto(EntityPlayer p) {
        if (!(p instanceof EntityPlayerMP)) {
            return null;
        }
        String t;
        try {
            t = ReflectionHelper.getPrivateValue(EntityPlayerMP.class, (EntityPlayerMP) p,
                    "translator", "field_71148_cg");
        } catch (Exception e) {
            return null;
        }
        if (t == null || t.length() < 2) {
            return null;
        }
        String l = t.substring(0, 2).toLowerCase();
        if (l.equals("es")) {
            return "es_ES";
        }
        return l.equals("pt") ? "pt_BR" : "en_US";
    }

    /** en_US, pt_BR, o null si el jugador ve el original (castellano o sin dato). */
    static String idiomaDe(EntityPlayer p) {
        String i = idiomaCompleto(p);
        return "es_ES".equals(i) ? null : i;
    }

    private static Archivo archivo(EntityPlayer p) {
        String idioma = idiomaDe(p);
        return idioma == null ? null : cargados.get(idioma);
    }

    // ------------------------------------------------------------------ huellas

    /** CRC32 en hex de las partes unidas con NUL. La misma cuenta que el exportador. */
    static String huella(List<String> partes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.size(); i++) {
            if (i > 0) {
                sb.append('\0');
            }
            sb.append(partes.get(i) == null ? "" : partes.get(i));
        }
        CRC32 c = new CRC32();
        c.update(sb.toString().getBytes(UTF8));
        return Long.toHexString(c.getValue());
    }

    static String huella(Dialog d) {
        List<String> partes = new ArrayList<String>();
        partes.add(d.title);
        partes.add(d.text);
        List<Integer> slots = new ArrayList<Integer>(d.options.keySet());
        Collections.sort(slots);
        for (Integer s : slots) {
            DialogOption o = d.options.get(s);
            partes.add(s + "=" + (o == null ? "" : o.title));
        }
        return huella(partes);
    }

    static String huella(Quest q) {
        List<String> partes = new ArrayList<String>();
        partes.add(q.title);
        partes.add(q.logText);
        partes.add(q.completeText);
        return huella(partes);
    }

    private static Entrada dialogo(EntityPlayer p, Dialog d) {
        Archivo a = archivo(p);
        if (a == null || a.dialogos == null || d == null) {
            return null;
        }
        return vigente(a.dialogos.get(String.valueOf(d.id)), huella(d), "dialogo " + d.id);
    }

    private static Entrada mision(EntityPlayer p, Quest q) {
        Archivo a = archivo(p);
        if (a == null || a.misiones == null || q == null) {
            return null;
        }
        return vigente(a.misiones.get(String.valueOf(q.id)), huella(q), "mision " + q.id);
    }

    /** Avisados una vez: traducciones cuyo castellano cambio desde que se tradujeron. */
    private static final java.util.Set<String> viejas =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** La entrada si su huella es la del castellano actual; si no, null y un aviso en el log. */
    private static Entrada vigente(Entrada e, String huella, String que) {
        if (e == null) {
            return null;
        }
        if (huella.equals(e.h)) {
            return e;
        }
        if (viejas.add(que)) {
            LogWriter.info("Traduccion vieja de " + que + " (castellano cambiado): se muestra el original."
                    + " Volver a correr LANGUAGE UPDATE/tools/cnpc_traducciones.py");
        }
        return null;
    }

    private static boolean hay(String s) {
        return s != null && !s.isEmpty();
    }

    // ------------------------------------------------------------------ lo que sale al jugador

    /** El NBT de un dialogo ya armado para mandar: titulo, texto y opciones. */
    public static void dialogo(EntityPlayer p, Dialog original, NBTTagCompound nbt) {
        Entrada e = dialogo(p, original);
        if (e == null) {
            return;
        }
        if (hay(e.titulo)) {
            nbt.setString("DialogTitle", e.titulo);
        }
        if (hay(e.texto)) {
            nbt.setString("DialogText", e.texto);
        }
        if (e.opciones != null && nbt.hasKey("Options")) {
            NBTTagList lista = nbt.getTagList("Options", 10);
            for (int i = 0; i < lista.tagCount(); i++) {
                NBTTagCompound op = lista.getCompoundTagAt(i);
                String t = e.opciones.get(String.valueOf(op.getInteger("OptionSlot")));
                if (hay(t)) {
                    op.getCompoundTag("Option").setString("Title", t);
                }
            }
        }
    }

    /** El NBT de una mision (pantalla de completado, mision seguida en el HUD). */
    public static void mision(EntityPlayer p, Quest q, NBTTagCompound nbt) {
        Entrada e = mision(p, q);
        if (e == null) {
            return;
        }
        if (hay(e.titulo)) {
            nbt.setString("Title", e.titulo);
        }
        if (hay(e.registro)) {
            nbt.setString("Text", e.registro);
        }
        if (hay(e.completado)) {
            nbt.setString("CompleteText", e.completado);
        }
    }

    /** El NBT de una mision, buscando la mision por su "Id" (pantalla de completado). */
    public static void mision(EntityPlayer p, NBTTagCompound nbt) {
        if (nbt != null && nbt.hasKey("Id")) {
            mision(p, noppes.npcs.controllers.QuestController.Instance.quests.get(nbt.getInteger("Id")), nbt);
        }
    }

    /** Titulo para mostrar (avisos de chat y logros). No para claves ni comparaciones. */
    public static String titulo(EntityPlayer p, Quest q) {
        Entrada e = mision(p, q);
        return e != null && hay(e.titulo) ? e.titulo : q.title;
    }

    /** Nombre de NPC o de lugar en el idioma del jugador; el original si no hay traduccion. */
    public static String nombre(EntityPlayer p, String nombre) {
        Archivo a = archivo(p);
        if (a == null || a.nombres == null || nombre == null) {
            return nombre;
        }
        String t = a.nombres.get(nombre);
        return hay(t) ? t : nombre;
    }

    /**
     * Una linea de objetivo ya armada por CustomNPCs: "Raditz Corrupto [Jefe]: 0/1",
     * "Lugar: Found", "Dialogo (read)", "Completed: jugadores". Traduce el nombre del principio
     * y las frases fijas en ingles del final. Lo que no reconoce lo deja igual.
     */
    public static String objetivo(EntityPlayer p, String texto) {
        String idioma = idiomaCompleto(p);
        if (texto == null || idioma == null) {
            return texto;
        }
        int col = idioma.equals("es_ES") ? 1 : idioma.equals("pt_BR") ? 2 : 0;
        String completado = FIJAS[FIJAS.length - 1][0];
        if (texto.startsWith(completado)) {
            return FIJAS[FIJAS.length - 1][col] + texto.substring(completado.length());
        }
        String cabeza = texto;
        String cola = "";
        for (int i = 0; i < FIJAS.length - 1; i++) {
            if (cabeza.endsWith(FIJAS[i][0])) {
                cabeza = cabeza.substring(0, cabeza.length() - FIJAS[i][0].length());
                cola = FIJAS[i][col];
                break;
            }
        }
        int dos = cabeza.lastIndexOf(": ");
        String nombre = dos >= 0 ? cabeza.substring(0, dos) : cabeza;
        String resto = dos >= 0 ? cabeza.substring(dos) : "";
        return nombre(p, nombre) + resto + cola;
    }

    /** Las lineas de estado del registro de misiones, una por objetivo. */
    public static java.util.Vector<String> objetivos(EntityPlayer p, java.util.Vector<String> lineas) {
        java.util.Vector<String> out = new java.util.Vector<String>(lineas.size());
        for (String l : lineas) {
            out.add(objetivo(p, l));
        }
        return out;
    }

    /** Texto del registro de misiones. */
    public static String registro(EntityPlayer p, Quest q) {
        Entrada e = mision(p, q);
        return e != null && hay(e.registro) ? e.registro : q.logText;
    }
}
