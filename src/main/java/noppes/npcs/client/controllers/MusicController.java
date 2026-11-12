package noppes.npcs.client.controllers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MusicController {
    public static MusicController Instance;
    private final HashMap<String, ScriptClientSound> sounds = new HashMap<>();
    private int playDelay;
    public ScriptClientSound playingSound;

    private Entity entity;
    private int offRange;
    /** Canciones que no existen ya avisadas en el log (una vez por id). */
    private final Set<String> desconocidas = new HashSet<>();

    public MusicController() {
        Instance = this;
    }

    public void onUpdate() {
        if (this.playingSound != null
            && !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(this.playingSound)) {
            Entity playingEntity = this.playingSound.getEntity();
            String sound = this.playingSound.sound;
            this.playingSound.stopSound();
            this.stopMusic();
            if (playingEntity == Minecraft.getMinecraft().thePlayer) {
                this.playMusicBackground(sound, this.entity, this.offRange);
            } else {
                this.playMusicJukebox(sound, this.entity, this.offRange);
            }
        }

        SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
        HashSet<String> keys = new HashSet<>();
        for (Map.Entry<String, ScriptClientSound> entry : this.sounds.entrySet()) {
            ScriptClientSound sound = entry.getValue();
            if (!soundHandler.isSoundPlaying(sound) && !sound.canRepeat() && !sound.paused) {
                keys.add(entry.getKey());
            }
        }
        for (String key : keys) {
            this.sounds.remove(key);
        }

        if (this.playDelay > 0) {
            this.playDelay--;
        }
    }

    public void stopMusic() {
        SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
        if (this.playingSound != null) {
            if (soundHandler != null) {
                try {
                    soundHandler.stopSound(this.playingSound);
                } catch (Exception ignored) {
                }
            }
            this.playingSound.stopSound();
            this.sounds.remove(this.playingSound.sound);
            this.playingSound = null;
            MusicaDbr.parar();
            this.entity = null;
            this.playDelay = 20; // Prevent immediate restart when music is stopped
        }
        // Dbr: sin sonido propio no se para nada. Antes llamaba a SoundHandler.stopSounds(), que corta
        // TODOS los sonidos del juego (la musica de las cinematicas del pack); con un bardo cuya
        // cancion no existe pasaba cada segundo (DbrServerPack/PLAN-musica-blindada.md).
    }

    public void playMusicJukebox(String music, Entity entity, int offRange) {
        if (this.playDelay > 0) {
            return;
        }
        this.playDelay = 20;
        if (!existe(music, entity)) {
            return;
        }

        if (this.isPlaying(music)) {
            return;
        }
        this.stopMusic();

        ScriptClientSound clientSound = new ScriptClientSound(music);
        clientSound.setEntity(entity);
        clientSound.setVolume(Math.max(1, offRange / 16.0F));
        clientSound.setAttenuationType(ISound.AttenuationType.LINEAR);
        clientSound.setRepeat(true);
        this.playingSound = clientSound;
        this.entity = entity;
        this.offRange = offRange;
        this.sounds.put(clientSound.sound, clientSound);
        // Gestor de musica del pack Dbr: antes de tocarlo (ver MusicaDbr).
        MusicaDbr.adoptar(clientSound);

        try {
            SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
            soundHandler.playSound(clientSound);
        } catch (Exception e) {
            this.stopAllSounds();
        }
    }

    public void playMusicBackground(String music, Entity entity, int offRange) {
        if (this.playDelay > 0) {
            return;
        }
        this.playDelay = 20;
        if (!existe(music, entity)) {
            return;
        }

        if (this.isPlaying(music)) {
            return;
        }
        this.stopMusic();

        ScriptClientSound clientSound = new ScriptClientSound(music);
        clientSound.setEntity(Minecraft.getMinecraft().thePlayer);
        clientSound.setRepeat(true);
        this.playingSound = clientSound;
        this.entity = entity;
        this.offRange = offRange;
        this.sounds.put(clientSound.sound, clientSound);
        // Gestor de musica del pack Dbr: antes de tocarlo (ver MusicaDbr).
        MusicaDbr.adoptar(clientSound);

        try {
            SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
            soundHandler.playSound(clientSound);
        } catch (Exception e) {
            this.stopAllSounds();
        }
    }

    public void playSound(String music, float x, float y, float z) {
        if (this.playDelay > 0) {
            return;
        }
        this.playDelay = 20;

        ScriptClientSound clientSound = new ScriptClientSound(music);
        clientSound.setPos(x, y, z);
        clientSound.setVolume(1.0F);
        clientSound.setAttenuationType(ISound.AttenuationType.NONE);
        clientSound.setRepeat(false);

        try {
            SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
            soundHandler.playSound(clientSound);
        } catch (Exception e) {
            this.stopAllSounds();
            return;
        }
        this.sounds.put(clientSound.sound, clientSound);
    }

    /**
     * Dbr: una cancion que no esta en ningun sounds.json no se intenta tocar (vanilla solo avisa
     * "Unable to play unknown soundEvent" y el bardo lo reintentaba cada segundo). Se avisa una vez
     * por id, con el NPC que la pide, para cambiarsela.
     */
    private boolean existe(String music, Entity entity) {
        if (music == null || music.isEmpty()) return false;
        ResourceLocation id = new ResourceLocation(music);
        if (Minecraft.getMinecraft().getSoundHandler().getSound(id) != null) return true;
        if (this.desconocidas.add(id.toString())) {
            String quien = entity == null ? "?" : entity.getCommandSenderName() + " en "
                + (int) Math.floor(entity.posX) + "," + (int) Math.floor(entity.posY) + "," + (int) Math.floor(entity.posZ)
                + " (dim " + entity.dimension + ")";
            noppes.npcs.LogWriter.info("[Dbr] cancion desconocida '" + id + "' pedida por " + quien + "; no se toca");
        }
        return false;
    }

    public boolean isPlaying(String music) {
        if (this.sounds.containsKey(music)) return true;

        if (this.playingSound != null && this.playingSound.sound.equals(music)) {
            ResourceLocation resource = new ResourceLocation(music);
            if (!this.playingSound.getPositionedSoundLocation().equals(resource)) {
                return false;
            }
            return Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(this.playingSound);
        } else {
            return false;
        }
    }

    public boolean isPlaying() {
        return this.playingSound != null;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public int getOffRange() {
        return this.offRange;
    }

    public double getDistance() {
        Entity player = Minecraft.getMinecraft().thePlayer;
        if (this.entity != null && this.entity.dimension == player.dimension) {
            return player.getDistanceToEntity(this.entity);
        }
        return Double.MAX_VALUE;
    }

    public void stopAllSounds() {
        this.stopMusic();
        for (ScriptClientSound sound : this.sounds.values()) {
            sound.stopSound();
        }
        this.sounds.clear();
    }

    /**
     * Stops a specific sound by its resource key.
     * This is used to stop dialog sounds without affecting Bard music.
     * @param soundKey The sound resource key to stop
     */
    public void stopSound(String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) {
            return;
        }
        ScriptClientSound sound = this.sounds.remove(soundKey);
        if (sound != null) {
            SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
            if (soundHandler != null) {
                try {
                    soundHandler.stopSound(sound);
                } catch (Exception ignored) {
                }
            }
            sound.stopSound();
        }
    }
}
