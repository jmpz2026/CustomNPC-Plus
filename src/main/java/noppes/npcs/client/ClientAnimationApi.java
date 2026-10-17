package noppes.npcs.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.constants.EnumAnimationPart;
import noppes.npcs.controllers.data.Animation;
import noppes.npcs.controllers.data.AnimationData;
import noppes.npcs.controllers.data.Frame;
import noppes.npcs.controllers.data.FramePart;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Fork DBR: stable entry point to play an animation on this client only, without the server.
 * Other mods call it by reflection (DbrVfxMod plays the poses of its effects with it, so each
 * client decides whether to show them). Keep the signatures: they are a contract.
 *
 * <p>The animation is the NBT that {@link Animation#writeToNBT()} writes, the same format the
 * animation editor saves to {@code customnpcs/animations/*.json}.
 */
@SideOnly(Side.CLIENT)
public final class ClientAnimationApi {

    private ClientAnimationApi() {
    }

    /** Plays the animation on a player or a CustomNPC+ NPC. False if the entity cannot be animated. */
    public static boolean play(Entity entity, NBTTagCompound animation) {
        AnimationData data = data(entity, true);
        if (data == null || animation == null) {
            return false;
        }
        Animation anim = new Animation();
        anim.readFromNBT(animation);
        // Any state: the player standing still on the ground still has gravity in motionY.
        anim.whileStanding = true;
        anim.whileMoving = true;
        anim.whileAttacking = true;
        data.allowAnimation = true;
        data.setAnimation(anim);
        return true;
    }

    /**
     * Freezes the animation that is playing on the entity at {@code tick} ticks from its start
     * (fractions allowed): screenshots of an instant. Only the keyframe curves are exact; the
     * old modes stay where the jump leaves them. False if nothing is playing.
     */
    public static boolean freeze(Entity entity, float tick) {
        AnimationData data = data(entity, false);
        if (data == null || data.animation == null || data.animation.frames.isEmpty()) {
            return false;
        }
        Animation anim = data.animation;
        tick = Math.max(0, tick);
        int whole = (int) Math.floor(tick);
        float frac = tick - whole;
        int frame = 0;
        while (frame < anim.frames.size() - 1 && whole >= anim.frames.get(frame).duration) {
            whole -= anim.frames.get(frame).duration;
            frame++;
        }
        Frame current = anim.frames.get(frame);
        if (whole >= current.duration) {   // past the end: the last pose
            whole = Math.max(0, current.duration - 1);
            frac = 1f;
        }
        Frame previous = frame > 0 ? anim.frames.get(frame - 1) : null;
        float pi = (float) Math.PI / 180;
        for (FramePart part : current.frameParts.values()) {
            FramePart before = previous == null ? null : previous.frameParts.get(part.part);
            for (int i = 0; i < 3; i++) {
                part.prevRotations[i] = before == null ? 0 : before.rotation[i] * pi;
                part.prevPivots[i] = before == null ? 0 : before.pivot[i];
            }
        }
        anim.currentFrame = frame;
        anim.currentFrameTime = whole;
        anim.frameEntry++;
        anim.frozenPartial = frac;
        return true;
    }

    /**
     * Stops the animation if it is still the one with that name (another one is left alone). The
     * body goes back to rest in a few ticks instead of jumping.
     */
    public static void stop(Entity entity, String name) {
        AnimationData data = data(entity, false);
        if (data == null || data.animation == null || !data.animation.name.equals(name)) {
            return;
        }
        Frame current = (Frame) data.animation.currentFrame();
        if (current == null || !data.isActive() || current.frameParts.isEmpty()) {
            data.setAnimation(null);
            return;
        }
        Animation rest = new Animation();
        rest.name = name + "#rest";
        rest.firstPerson = data.animation.firstPerson;
        Frame back = new Frame(5, 1f, (byte) (FramePart.CURVE_FIRST + 3));
        for (EnumAnimationPart part : current.frameParts.keySet()) {
            back.addPart(new FramePart(part));
        }
        rest.addFrame(back);
        play(entity, rest.writeToNBT());
    }

    /** Name of the animation that is playing on the entity, or null. */
    public static String playing(Entity entity) {
        AnimationData data = data(entity, false);
        return data != null && data.animation != null && data.isActive() ? data.animation.name : null;
    }

    private static AnimationData data(Entity entity, boolean create) {
        if (entity instanceof EntityPlayer) {
            AnimationData data = ClientCacheHandler.playerAnimations.get(entity.getUniqueID());
            if (data == null && create) {
                data = new AnimationData(entity);
                ClientCacheHandler.playerAnimations.put(entity.getUniqueID(), data);
            }
            if (data != null) {
                data.parent = entity;
            }
            return data;
        }
        if (entity instanceof EntityNPCInterface) {
            return ((EntityNPCInterface) entity).display.animationData;
        }
        return null;
    }
}
