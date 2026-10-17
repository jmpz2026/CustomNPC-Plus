package noppes.npcs.controllers.data;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.handler.data.IFramePart;
import noppes.npcs.client.ClientEventHandler;
import noppes.npcs.constants.EnumAnimationPart;

public class FramePart implements IFramePart {
    public Animation parent;
    public EnumAnimationPart part;
    public float[] rotation = {0, 0, 0};
    public float[] pivot = {0, 0, 0};

    boolean customized = false;

    public float speed = 1.0F;
    //0 - Interpolated, 1 - Linear, 2 - None, 3.. - keyframe curves (fork DBR, see CURVE_NAMES)
    public byte smooth = 0;

    /** First smooth value that is a keyframe curve; the curve is {@code smooth - CURVE_FIRST}. */
    public static final byte CURVE_FIRST = 3;
    /** Lang keys of the curves: linear, in, out, in-out, back, sharp. */
    public static final String[] CURVE_NAMES = {"animation.curve.linear", "animation.curve.in",
        "animation.curve.out", "animation.curve.inout", "animation.curve.back", "animation.curve.sharp"};

    //Client-sided fields (unsaved)
    public float[] prevRotations = {0, 0, 0};
    public float[] prevPivots = {0, 0, 0};
    public float partialRotationTick;
    public float partialPivotTick;
    // Fork DBR: render frame of the last step of the old modes, and the start of a keyframe curve
    public long rotationFrame = -1;
    public long pivotFrame = -1;
    public int rotationEntry = -1;
    public int pivotEntry = -1;
    public float[] startRotations = {0, 0, 0};
    public float[] startPivots = {0, 0, 0};
    /** Frame that holds this part (client): the curves need its duration. */
    public transient Frame frame;

    public FramePart() {
    }

    public FramePart(EnumAnimationPart part) {
        this.part = part;
    }

    public EnumAnimationPart getPart() {
        return part;
    }

    public String getName() {
        return part.name();
    }

    public int getPartId() {
        return part.ordinal();
    }

    public void setPart(EnumAnimationPart part) {
        this.part = part;
    }

    public IFramePart setPart(String name) {
        try {
            this.setPart(EnumAnimationPart.valueOf(name));
        } catch (IllegalArgumentException ignored) {
        }
        return this;
    }

    public IFramePart setPart(int partId) {
        for (EnumAnimationPart enumPart : EnumAnimationPart.values()) {
            if (enumPart.ordinal() == partId) {
                this.setPart(enumPart);
                break;
            }
        }
        return this;
    }

    public float[] getRotations() {
        return rotation;
    }

    public IFramePart setRotations(float[] rotation) {
        this.rotation = rotation;
        return this;
    }

    public float[] getPivots() {
        return pivot;
    }

    public IFramePart setPivots(float[] pivot) {
        this.pivot = pivot;
        return this;
    }

    public boolean isCustomized() {
        return customized;
    }

    public IFramePart setCustomized(boolean customized) {
        this.customized = customized;
        return this;
    }

    public float getSpeed() {
        return speed;
    }

    public IFramePart setSpeed(float speed) {
        this.speed = speed;
        return this;
    }

    public byte isSmooth() {
        return smooth;
    }

    public IFramePart setSmooth(byte smooth) {
        this.smooth = smooth;
        return this;
    }

    public void readFromNBT(NBTTagCompound compound) {
        part = EnumAnimationPart.valueOf(compound.getString("Part"));
        for (int i = 0; i < 3; i++) {
            rotation[i] = compound.getFloat("Rotation" + i);
        }
        for (int i = 0; i < 3; i++) {
            pivot[i] = compound.getFloat("Pivot" + i);
        }

        // Customized = TRUE if Speed or Smooth Exist
        if (compound.hasKey("Speed")) {
            customized = true;
            speed = compound.getFloat("Speed");
        }
        if (compound.hasKey("Smooth")) {
            customized = true;
            smooth = compound.getByte("Smooth");
        }
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString("Part", part.toString());
        for (int i = 0; i < 3; i++) {
            compound.setFloat("Rotation" + i, rotation[i]);
        }
        for (int i = 0; i < 3; i++) {
            compound.setFloat("Pivot" + i, pivot[i]);
        }

        if (customized) {
            compound.setFloat("Speed", speed);
            compound.setByte("Smooth", smooth);
        }

        return compound;
    }

    public FramePart copy() {
        FramePart part = new FramePart(this.part);
        part.rotation = new float[]{this.rotation[0], this.rotation[1], this.rotation[2]};
        part.pivot = new float[]{this.pivot[0], this.pivot[1], this.pivot[2]};
        part.customized = this.customized;
        part.speed = this.speed;
        part.smooth = this.smooth;
        return part;
    }

    @SideOnly(Side.CLIENT)
    public void interpolateAngles() {
        if (parent != null && parent.paused)
            return;

        float pi = (float) Math.PI / 180;
        float[] target = {this.rotation[0] * pi, this.rotation[1] * pi, this.rotation[2] * pi};
        if (this.smooth >= CURVE_FIRST) {
            if (this.rotationEntry != entry()) {
                this.rotationEntry = entry();
                System.arraycopy(this.prevRotations, 0, this.startRotations, 0, 3);
            }
            curve(this.prevRotations, this.startRotations, target);
        } else if (this.smooth == 2) {
            System.arraycopy(target, 0, this.prevRotations, 0, 3);
        } else if (this.rotationFrame != ClientEventHandler.renderFrame) {
            this.rotationFrame = ClientEventHandler.renderFrame;
            this.partialRotationTick = ClientEventHandler.partialRenderTick;
            chase(this.prevRotations, target);
        }
    }

    @SideOnly(Side.CLIENT)
    public void interpolateOffset() {
        if (parent != null && parent.paused)
            return;

        if (this.smooth >= CURVE_FIRST) {
            if (this.pivotEntry != entry()) {
                this.pivotEntry = entry();
                System.arraycopy(this.prevPivots, 0, this.startPivots, 0, 3);
            }
            curve(this.prevPivots, this.startPivots, this.pivot);
        } else if (this.smooth == 2) {
            System.arraycopy(this.pivot, 0, this.prevPivots, 0, 3);
        } else if (this.pivotFrame != ClientEventHandler.renderFrame) {
            this.pivotFrame = ClientEventHandler.renderFrame;
            this.partialPivotTick = ClientEventHandler.partialRenderTick;
            chase(this.prevPivots, this.pivot);
        }
    }

    /**
     * Fork DBR: the old modes moved a fixed amount per rendered frame, so an animation ran five
     * times faster at 144 FPS than at 30. The step is now scaled by the real time of the frame,
     * calibrated so 60 FPS looks like it always did.
     */
    @SideOnly(Side.CLIENT)
    private void chase(float[] current, float[] target) {
        float frames = ClientEventHandler.renderFrameScale;
        float rate = Math.abs(this.speed) / 10f;
        if (this.smooth == 0) {
            float f = rate >= 1f ? 1f : 1f - (float) Math.pow(1f - rate, frames);
            for (int i = 0; i < 3; i++) {
                current[i] += (target[i] - current[i]) * f;
            }
        } else {
            float step = this.speed / 10f * frames;
            for (int i = 0; i < 3; i++) {
                int direction = Float.compare(target[i], current[i]);
                current[i] += direction * step;
                current[i] = direction == 1 ? Math.min(target[i], current[i]) : Math.max(target[i], current[i]);
            }
        }
    }

    /**
     * Fork DBR: keyframe modes. The part goes from where it was when the frame started to this
     * frame's value in exactly the frame's duration, following the curve. Same result at any FPS,
     * so a hit lands on the tick it was made for.
     */
    @SideOnly(Side.CLIENT)
    private void curve(float[] current, float[] start, float[] target) {
        Frame owner = this.frame;
        if (parent == null || owner == null || owner.duration <= 0) {
            System.arraycopy(target, 0, current, 0, 3);
            return;
        }
        float partial = parent.frozenPartial >= 0 ? parent.frozenPartial : ClientEventHandler.partialRenderTick;
        float t = (parent.currentFrameTime + partial) / owner.duration;
        float e = ease(this.smooth - CURVE_FIRST, t < 0 ? 0 : (t > 1 ? 1 : t));
        for (int i = 0; i < 3; i++) {
            current[i] = start[i] + (target[i] - start[i]) * e;
        }
    }

    private int entry() {
        return parent != null ? parent.frameEntry : 0;
    }

    /** Curves of the keyframe modes, in the order of {@link #CURVE_NAMES}. */
    public static float ease(int curve, float t) {
        switch (curve) {
            case 1: // ease in
                return t * t * t;
            case 2: // ease out
                return 1 - (1 - t) * (1 - t) * (1 - t);
            case 3: // ease in-out
                return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            case 4: { // back out: passes the target and settles
                float c1 = 1.70158f, c3 = c1 + 1, u = t - 1;
                return 1 + c3 * u * u * u + c1 * u * u;
            }
            case 5: // sharp: almost all the way in the first ticks
                return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
            default:
                return t;
        }
    }

    @SideOnly(Side.CLIENT)
    public void jumpToCurrentFrame() {
        this.partialRotationTick = ClientEventHandler.partialRenderTick;
        this.partialPivotTick = ClientEventHandler.partialRenderTick;

        this.prevPivots[0] = this.pivot[0];
        this.prevPivots[1] = this.pivot[1];
        this.prevPivots[2] = this.pivot[2];

        float pi = (float) Math.PI / 180;
        this.prevRotations[0] = this.rotation[0] * pi;
        this.prevRotations[1] = this.rotation[1] * pi;
        this.prevRotations[2] = this.rotation[2] * pi;
    }
}
