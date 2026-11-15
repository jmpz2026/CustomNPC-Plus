package noppes.npcs.client.gui.roles;

import kamkeel.npcs.network.packets.request.jobs.JobSavePacket;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.ISubGuiListener;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobBard;
import noppes.npcs.roles.ListaBardo;

import java.util.ArrayList;
import java.util.List;

public class GuiNpcBard extends GuiNPCInterface2 implements ISubGuiListener, ICustomScrollListener, ITextfieldListener {
    /** Segundos que se proponen al agregar una pista. El dueño pone la duracion real. */
    private static final int SEG_NUEVA = 120;

    private JobBard job;

    // Copia de trabajo de la playlist. Solo pasa a job.lista al guardar.
    private final List<String> pistas = new ArrayList<String>();
    private final List<Integer> segs = new ArrayList<Integer>();
    private int modo;
    private int silencio;
    private int elegida = -1;

    private GuiCustomScroll scroll;
    /** true si el selector de sonido se abrio para agregar a la lista (y no para BardSong). */
    private boolean eligiendoParaLista;

    public GuiNpcBard(EntityNPCInterface npc) {
        super(npc);
        job = (JobBard) npc.jobInterface;
        for (ListaBardo.Entrada e : job.lista.entradas()) {
            pistas.add(e.pista);
            segs.add(e.seg);
        }
        modo = job.lista.modo();
        silencio = job.lista.silencioMs();
    }

    public void initGui() {
        super.initGui();

        this.addButton(new GuiNpcButton(1, guiLeft + 55, guiTop + 15, 20, 20, "X"));
        addLabel(new GuiNpcLabel(0, job.song, guiLeft + 80, guiTop + 20));
        this.addButton(new GuiNpcButton(0, guiLeft + 75, guiTop + 50, "gui.selectSound"));

        this.addButton(new GuiNpcButton(2, guiLeft + 75, guiTop + 71, new String[]{"gui.none", "item.npcBanjo.name", "item.npcViolin.name", "item.npcGuitar.name", "item.npcHarp.name", "item.npcFrenchHorn.name"}, job.getInstrument().ordinal()));
        this.addButton(new GuiNpcButton(3, guiLeft + 75, guiTop + 92, new String[]{"bard.jukebox", "bard.background"}, job.isStreamer ? 0 : 1));


        addLabel(new GuiNpcLabel(2, "bard.ondistance", guiLeft + 60, guiTop + 143));
        addTextField(new GuiNpcTextField(2, this, this.fontRendererObj, guiLeft + 160, guiTop + 138, 40, 20, job.minRange + ""));
        getTextField(2).integersOnly = true;
        getTextField(2).setMinMaxDefault(2, Integer.MAX_VALUE, 5);

        addLabel(new GuiNpcLabel(4, "bard.hasoff", guiLeft + 60, guiTop + 166));
        addButton(new GuiNpcButton(4, guiLeft + 160, guiTop + 161, 60, 20, new String[]{"gui.no", "gui.yes"}, job.hasOffRange ? 1 : 0));

        addLabel(new GuiNpcLabel(3, "bard.offdistance", guiLeft + 60, guiTop + 189));
        addTextField(new GuiNpcTextField(3, this, this.fontRendererObj, guiLeft + 160, guiTop + 184, 40, 20, job.maxRange + ""));
        getTextField(3).integersOnly = true;
        getTextField(3).setMinMaxDefault(2, Integer.MAX_VALUE, 10);

        getLabel(3).enabled = job.hasOffRange;
        getTextField(3).enabled = job.hasOffRange;

        // Playlist (panel derecho). Con la lista vacia el bardo toca BardSong como siempre.
        if (scroll == null) {
            scroll = new GuiCustomScroll(this, 0);
            scroll.setSize(176, 118);
        }
        scroll.guiLeft = guiLeft + 232;
        scroll.guiTop = guiTop + 4;
        refrescarScroll();
        addScroll(scroll);

        boolean hay = elegida >= 0 && elegida < pistas.size();
        addButton(new GuiNpcButton(10, guiLeft + 232, guiTop + 126, 56, 20, "gui.add"));
        getButton(10).enabled = pistas.size() < ListaBardo.MAX_PISTAS;
        addButton(new GuiNpcButton(11, guiLeft + 290, guiTop + 126, 56, 20, "gui.remove"));
        getButton(11).enabled = hay;
        addButton(new GuiNpcButton(12, guiLeft + 348, guiTop + 126, 29, 20, "^"));
        getButton(12).enabled = hay && elegida > 0;
        addButton(new GuiNpcButton(13, guiLeft + 379, guiTop + 126, 29, 20, "v"));
        getButton(13).enabled = hay && elegida < pistas.size() - 1;

        addLabel(new GuiNpcLabel(10, "bard.seg", guiLeft + 232, guiTop + 155));
        addTextField(new GuiNpcTextField(4, this, this.fontRendererObj, guiLeft + 262, guiTop + 150, 36, 20, hay ? segs.get(elegida) + "" : ""));
        getTextField(4).integersOnly = true;
        getTextField(4).setMinMaxDefault(ListaBardo.SEG_MIN, ListaBardo.SEG_MAX, SEG_NUEVA);
        getTextField(4).enabled = hay;
        getLabel(10).enabled = hay;

        addLabel(new GuiNpcLabel(11, "bard.silencio", guiLeft + 304, guiTop + 155));
        addTextField(new GuiNpcTextField(5, this, this.fontRendererObj, guiLeft + 368, guiTop + 150, 40, 20, silencio + ""));
        getTextField(5).integersOnly = true;
        getTextField(5).setMinMaxDefault(0, ListaBardo.SILENCIO_MAX_MS, ListaBardo.SILENCIO_DEF_MS);

        addButton(new GuiNpcButton(14, guiLeft + 232, guiTop + 174, 176, 20,
                new String[]{"bard.modo.orden", "bard.modo.aleatorio"}, modo == ListaBardo.MODO_ORDEN ? 0 : 1));
    }

    private void refrescarScroll() {
        List<String> filas = new ArrayList<String>();
        for (int i = 0; i < pistas.size(); i++) {
            filas.add((i + 1) + ". " + pistas.get(i) + " (" + segs.get(i) + "s)");
        }
        scroll.setUnsortedList(filas);
        scroll.selected = elegida;
    }

    /** Pasa a la copia de trabajo lo que haya escrito en los campos de segundos y silencio. */
    private void leerCampos() {
        GuiNpcTextField fSeg = getTextField(4);
        if (fSeg != null && elegida >= 0 && elegida < segs.size() && fSeg.isInteger()) {
            segs.set(elegida, ListaBardo.limitar(fSeg.getInteger(), ListaBardo.SEG_MIN, ListaBardo.SEG_MAX));
        }
        GuiNpcTextField fSil = getTextField(5);
        if (fSil != null && fSil.isInteger()) {
            silencio = ListaBardo.limitar(fSil.getInteger(), 0, ListaBardo.SILENCIO_MAX_MS);
        }
    }

    protected void actionPerformed(GuiButton guibutton) {
        GuiNpcButton button = (GuiNpcButton) guibutton;
        if (button.id == 0) {
            eligiendoParaLista = false;
            setSubGui(new GuiSoundSelection(job.song));
            MusicController.Instance.stopMusic();
        }
        if (button.id == 1) {
            job.song = "";
            getLabel(0).label = "";
            MusicController.Instance.stopMusic();
        }
        if (button.id == 2) {
            job.setInstrument(button.getValue());
        }
        if (button.id == 3) {
            job.isStreamer = button.getValue() == 0;
            initGui();
        }
        if (button.id == 4) {
            job.hasOffRange = button.getValue() == 1;
            initGui();
        }
        if (button.id >= 10 && button.id <= 14) {
            leerCampos();
        }
        if (button.id == 10 && pistas.size() < ListaBardo.MAX_PISTAS) {
            eligiendoParaLista = true;
            setSubGui(new GuiSoundSelection(""));
            MusicController.Instance.stopMusic();
        }
        if (button.id == 11 && elegida >= 0 && elegida < pistas.size()) {
            pistas.remove(elegida);
            segs.remove(elegida);
            elegida = Math.min(elegida, pistas.size() - 1);
            initGui();
        }
        if ((button.id == 12 || button.id == 13) && elegida >= 0 && elegida < pistas.size()) {
            int otra = button.id == 12 ? elegida - 1 : elegida + 1;
            if (otra >= 0 && otra < pistas.size()) {
                pistas.set(otra, pistas.set(elegida, pistas.get(otra)));
                segs.set(otra, segs.set(elegida, segs.get(otra)));
                elegida = otra;
            }
            initGui();
        }
        if (button.id == 14) {
            modo = button.getValue() == 0 ? ListaBardo.MODO_ORDEN : ListaBardo.MODO_ALEATORIO;
        }
    }

    @Override
    public void customScrollClicked(int i, int j, int k, GuiCustomScroll guiCustomScroll) {
        leerCampos();
        elegida = guiCustomScroll.selected;
        initGui();
    }

    @Override
    public void unFocused(GuiNpcTextField textfield) {
        if (textfield.id == 4 || textfield.id == 5) {
            leerCampos();
            refrescarScroll();
        }
    }

    @Override
    public void save() {
        job.minRange = getTextField(2).getInteger();
        job.maxRange = getTextField(3).getInteger();

        if (job.minRange > job.maxRange)
            job.maxRange = job.minRange;

        // Validacion de cortesia: la que manda es la de ListaBardo en el servidor.
        leerCampos();
        List<ListaBardo.Entrada> entradas = new ArrayList<ListaBardo.Entrada>();
        for (int i = 0; i < pistas.size(); i++) {
            entradas.add(new ListaBardo.Entrada(pistas.get(i), segs.get(i)));
        }
        job.lista.configurar(entradas, modo, silencio);

        MusicController.Instance.stopAllSounds();
        JobSavePacket.saveJob(job);
    }

    @Override
    public void close() {
        super.close();
        MusicController.Instance.stopAllSounds();
    }

    @Override
    public void subGuiClosed(SubGuiInterface subgui) {
        GuiSoundSelection gss = (GuiSoundSelection) subgui;
        if (gss.selectedResource != null) {
            String pista = gss.selectedResource.toString();
            if (eligiendoParaLista) {
                if (pistas.size() < ListaBardo.MAX_PISTAS && ListaBardo.pistaValida(pista)) {
                    pistas.add(pista);
                    segs.add(SEG_NUEVA);
                    elegida = pistas.size() - 1;
                }
            } else {
                job.song = pista;
                getLabel(0).label = job.song;
            }
            initGui();
            MusicController.Instance.stopAllSounds();
        }
        eligiendoParaLista = false;
    }
}
