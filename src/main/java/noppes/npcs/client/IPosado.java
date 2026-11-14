package noppes.npcs.client;

/**
 * Fork DBR: implemented by every {@code ModelRenderer} (MixinModelRenderer). The pose values that the last
 * {@code render} applied to the part, before putting the rest values back, so a mod that reads the part right
 * after rendering it (DbrVfxMod captures each bone with postRender) sees the animated bone. Read it through
 * {@link ClientAnimationApi#posed}.
 */
public interface IPosado {

    /** {pivotX, pivotY, pivotZ, angleX, angleY, angleZ} of the last render, or null if it had no pose. */
    float[] dbr$posado();
}
