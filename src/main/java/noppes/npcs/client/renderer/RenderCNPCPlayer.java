package noppes.npcs.client.renderer;

import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderPlayerEvent;
import noppes.npcs.LogWriter;
import noppes.npcs.client.ClientCacheHandler;
import noppes.npcs.controllers.data.SkinOverlay;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

public class RenderCNPCPlayer extends RenderPlayer {
    public static boolean hasMPM = false;
    public Minecraft mc = Minecraft.getMinecraft();
    public RenderCNPCHand itemRenderer = new RenderCNPCHand(mc);
    public float tempRenderPartialTicks;
    private float debugCamFOV;
    private float prevDebugCamFOV;
    private float fovModifierHand;
    private float fovModifierHandPrev;
    private float fovMultiplierTemp;

    public RenderCNPCPlayer() {
        super();
        this.modelBipedMain = (ModelBiped) this.mainModel;
        this.modelArmorChestplate = new ModelBiped(1.0F);
        this.modelArmor = new ModelBiped(0.5F);
        this.setRenderManager(RenderManager.instance);

        if (Loader.isModLoaded("moreplayermodels"))
            hasMPM = true;
    }

    private boolean preRenderOverlay(SkinOverlay overlayData, EntityPlayer player) {
        if (overlayData.texture.isEmpty())
            return false;

        ImageData imageData = ClientCacheHandler.getImageData(((SkinOverlay) overlayData).texture);
        if (!imageData.imageLoaded())
            return false;

        try {
            imageData.bindTexture();
        } catch (Exception e) {
            return false;
        }

        // Overlay & Glow
        GL11.glEnable(GL11.GL_BLEND);
        if (overlayData.blend) {
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569F);

        if (overlayData.glow) {
            GL11.glDisable(GL11.GL_LIGHTING);
            Minecraft.getMinecraft().entityRenderer.disableLightmap((double) 0);
            RenderHelper.disableStandardItemLighting();
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, overlayData.alpha);

        GL11.glDepthMask(!player.isInvisible());

        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glTranslatef(overlayData.ticks * 0.001F * overlayData.speedX, overlayData.ticks * 0.001F * overlayData.speedY, 0.0F);
        GL11.glScalef(overlayData.scaleX, overlayData.scaleY, 1.0F);
        overlayData.ticks++;

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glTranslatef(overlayData.offsetX, overlayData.offsetY, overlayData.offsetZ);
        GL11.glScalef(overlayData.size, overlayData.size, overlayData.size);

        return true;
    }

    public void postRenderOverlay() {
        GL11.glPopMatrix();

        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        Minecraft.getMinecraft().entityRenderer.enableLightmap((double) 0);
        RenderHelper.enableStandardItemLighting();
    }

    @Override
    protected void renderModel(EntityLivingBase entity, float par2, float par3, float par4, float par5, float par6, float par7) {
        EntityPlayer player = (EntityPlayer) entity;
        if (!entity.isInvisible()) {
            if (ClientCacheHandler.skinOverlays.containsKey(player.getUniqueID())) {
                for (SkinOverlay overlayData : ClientCacheHandler.skinOverlays.get(player.getUniqueID()).values()) {
                    if (((SkinOverlay) overlayData).texture.isEmpty())
                        continue;

                    ImageData imageData = ClientCacheHandler.getImageData(((SkinOverlay) overlayData).texture);
                    if (!imageData.imageLoaded())
                        continue;

                    if (!this.preRenderOverlay(overlayData, player))
                        continue;

                    if (hasMPM) {
                        renderMorePlayerModel(entity, par2, par3, par4, par5, par6, par7);
                    } else {
                        this.modelBipedMain.render(entity, par2, par3, par4, par5, par6, par7);
                    }
                    postRenderOverlay();
                }
            }
        }
    }

    protected int shouldRenderPass(AbstractClientPlayer p_77032_1_, int p_77032_2_, float p_77032_3_) {
        return -1;
    }

    public void passSpecialRender(EntityLivingBase p_77033_1_, double p_77033_2_, double p_77033_4_, double p_77033_6_) {
        Render render = RenderManager.instance.getEntityRenderObject(p_77033_1_);
        if (render instanceof RenderPlayer) {
            RenderPlayer renderPlayer = (RenderPlayer) render;
            renderPlayer.passSpecialRender(p_77033_1_, p_77033_2_, p_77033_4_, p_77033_6_);
        }
    }

    @Override
    public void rotateCorpse(EntityLivingBase p_77043_1_, float p_77043_2_, float p_77043_3_, float p_77043_4_) {
        Render render = RenderManager.instance.getEntityRenderObject(p_77043_1_);
        if (render instanceof RenderPlayer) {
            RenderPlayer renderPlayer = (RenderPlayer) render;
            renderPlayer.rotateCorpse(p_77043_1_, p_77043_2_, p_77043_3_, p_77043_4_);
        }
    }

    protected void func_96449_a(AbstractClientPlayer p_96449_1_, double p_96449_2_, double p_96449_4_, double p_96449_6_, String p_96449_8_, float p_96449_9_, double p_96449_10_) {
    }

    public void renderHand(float partialTicks, int renderPass) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityRenderer entityRenderer = mc.entityRenderer;

        if (entityRenderer.debugViewDirection <= 0) {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            float f1 = 0.07F;

            if (mc.gameSettings.anaglyph) {
                GL11.glTranslatef((float) (-(renderPass * 2 - 1)) * f1, 0.0F, 0.0F);
            }

            Project.gluPerspective(this.getFOVModifier(partialTicks, false), (float) mc.displayWidth / (float) mc.displayHeight, 0.05F, mc.gameSettings.renderDistanceChunks * 16 * 2.0F);

            if (mc.playerController.enableEverythingIsScrewedUpMode()) {
                float f2 = 0.6666667F;
                GL11.glScalef(1.0F, f2, 1.0F);
            }

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();

            if (mc.gameSettings.anaglyph) {
                GL11.glTranslatef((float) (renderPass * 2 - 1) * 0.1F, 0.0F, 0.0F);
            }

            GL11.glPushMatrix();
            hurtCameraEffect(partialTicks);

            if (mc.gameSettings.viewBobbing) {
                setupViewBobbing(partialTicks);
            }

            if (mc.gameSettings.thirdPersonView == 0 && !mc.renderViewEntity.isPlayerSleeping() && !mc.gameSettings.hideGUI && !mc.playerController.enableEverythingIsScrewedUpMode()) {
                entityRenderer.enableLightmap((double) partialTicks);
                itemRenderer.renderOverlayInFirstPerson(partialTicks);
                entityRenderer.disableLightmap((double) partialTicks);
            }

            GL11.glPopMatrix();

            if (mc.gameSettings.viewBobbing) {
                setupViewBobbing(partialTicks);
            }
        }
    }

    private float getFOVModifier(float p_78481_1_, boolean p_78481_2_) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase entityplayer = (EntityLivingBase) mc.renderViewEntity;
        float f1 = 70.0F;

        if (p_78481_2_) {
            f1 = mc.gameSettings.fovSetting;
            f1 *= this.fovModifierHandPrev + (this.fovModifierHand - this.fovModifierHandPrev) * p_78481_1_;
        }

        if (entityplayer.getHealth() <= 0.0F) {
            float f2 = (float) entityplayer.deathTime + p_78481_1_;
            f1 /= (1.0F - 500.0F / (f2 + 500.0F)) * 2.0F + 1.0F;
        }

        Block block = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, entityplayer, p_78481_1_);

        if (block.getMaterial() == Material.water) {
            f1 = f1 * 60.0F / 70.0F;
        }

        return f1 + this.prevDebugCamFOV + (this.debugCamFOV - this.prevDebugCamFOV) * p_78481_1_;
    }

    private void hurtCameraEffect(float p_78482_1_) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase entitylivingbase = mc.renderViewEntity;
        float f1 = (float) entitylivingbase.hurtTime - p_78482_1_;
        float f2;

        if (entitylivingbase.getHealth() <= 0.0F) {
            f2 = (float) entitylivingbase.deathTime + p_78482_1_;
            GL11.glRotatef(40.0F - 8000.0F / (f2 + 200.0F), 0.0F, 0.0F, 1.0F);
        }

        if (f1 >= 0.0F) {
            f1 /= (float) entitylivingbase.maxHurtTime;
            f1 = MathHelper.sin(f1 * f1 * f1 * f1 * (float) Math.PI);
            f2 = entitylivingbase.attackedAtYaw;
            GL11.glRotatef(-f2, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-f1 * 14.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(f2, 0.0F, 1.0F, 0.0F);
        }
    }

    private void setupViewBobbing(float p_78475_1_) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity instanceof EntityPlayer) {
            EntityPlayer entityplayer = (EntityPlayer) mc.renderViewEntity;
            float f1 = entityplayer.distanceWalkedModified - entityplayer.prevDistanceWalkedModified;
            float f2 = -(entityplayer.distanceWalkedModified + f1 * p_78475_1_);
            float f3 = entityplayer.prevCameraYaw + (entityplayer.cameraYaw - entityplayer.prevCameraYaw) * p_78475_1_;
            float f4 = entityplayer.prevCameraPitch + (entityplayer.cameraPitch - entityplayer.prevCameraPitch) * p_78475_1_;
            GL11.glTranslatef(MathHelper.sin(f2 * (float) Math.PI) * f3 * 0.5F, -Math.abs(MathHelper.cos(f2 * (float) Math.PI) * f3), 0.0F);
            GL11.glRotatef(MathHelper.sin(f2 * (float) Math.PI) * f3 * 3.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(Math.abs(MathHelper.cos(f2 * (float) Math.PI - 0.2F) * f3) * 5.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(f4, 1.0F, 0.0F, 0.0F);
        }
    }

    public void renderFirstPersonArmOverlay(EntityPlayer player) {
        // Check if player or model is null
        if (player == null || this.modelBipedMain == null) {
            return;
        }

        float gender = -1F;
        if (JBRA.genGet() != null) {
            try {
                gender = (float) JBRA.genGet().invoke(null);
            } catch (Exception ignored) {
            }
        }

        // Check if the skinOverlays map exists for this player's UUID
        UUID uuid = player.getUniqueID();
        if (uuid == null || !ClientCacheHandler.skinOverlays.containsKey(uuid)) {
            return;
        }

        Map<Integer, SkinOverlay> overlayMap = ClientCacheHandler.skinOverlays.get(uuid);
        if (overlayMap == null || overlayMap.isEmpty()) {
            return;
        }
        // Iterate through each SkinOverlay safely
        for (SkinOverlay overlayData : overlayMap.values()) {
            if (overlayData == null || overlayData.texture == null || overlayData.texture.isEmpty()) {
                continue;
            }

            ImageData imageData = ClientCacheHandler.getImageData(overlayData.texture);
            if (imageData == null || !imageData.imageLoaded()) {
                continue;
            }

            // Check your preRenderOverlay return
            if (!this.preRenderOverlay(overlayData, player)) {
                continue;
            }

            // Adjust arm rendering for gender if needed
            if (gender >= 2.0F) {
                GL11.glRotatef(7F, 0, 0, 1);
                GL11.glTranslatef(0.015F, 0.0375F, -0.0025F);
            }

            // Render the arm
            this.modelBipedMain.onGround = 0.0F;
            this.modelBipedMain.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, player);

            if (this.modelBipedMain.bipedRightArm != null)
                this.modelBipedMain.bipedRightArm.render(0.0625F);

            postRenderOverlay();
        }
    }

    public void renderMorePlayerModel(EntityLivingBase entity, float par2, float par3, float par4, float par5, float par6, float par7) {
        Field isArmor = MPM.isArmor();

        // Assuming this.modelBipedMain is an instance of ModelMPM
        if (isArmor != null && isArmor.getDeclaringClass().isInstance(this.modelBipedMain)) {
            try {
                isArmor.setBoolean(this.modelBipedMain, true);
                this.modelBipedMain.render(entity, par2, par3, par4, par5, par6, par7);
                isArmor.setBoolean(this.modelBipedMain, false);
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    public void renderDBCModel(RenderPlayerEvent.Specials.Post event) {
        if (!JBRA.available())
            return;

        EntityPlayer player = event.entityPlayer;
        Map<Integer, SkinOverlay> overlays = ClientCacheHandler.skinOverlays.get(player.getUniqueID());
        if (overlays == null)
            return;

        try {
            ModelBiped m = (ModelBiped) JBRA.modelMain.get(event.renderer);
            m.isRiding = player.isRiding();
            m.isChild = player.isChild();
            m.isSneak = player.isSneaking();
            JBRA.bodyY.set(null, JBRA.dbcY.get(null));

            ModelRenderer[] limbs = {m.bipedBody, m.bipedRightArm, m.bipedLeftArm, m.bipedRightLeg, m.bipedLeftLeg};
            ModelRenderer[] femaleParts = new ModelRenderer[JBRA.femaleParts.length];
            for (int i = 0; i < femaleParts.length; i++) {
                femaleParts[i] = (ModelRenderer) JBRA.femaleParts[i].get(m);
            }
            float childScl = (float) JBRA.childSclGet.invoke(null);

            for (SkinOverlay overlayData : overlays.values()) {
                if (overlayData.texture.isEmpty())
                    continue;

                ImageData imageData = ClientCacheHandler.getImageData(overlayData.texture);
                if (!imageData.imageLoaded())
                    continue;

                if (!this.preRenderOverlay(overlayData, player))
                    continue;

                m.bipedHead.isHidden = true;
                renderJBRA(m, player);
                m.bipedHead.isHidden = false;

                setHidden(limbs, true);
                //Female render
                setHidden(femaleParts, true);
                if (player.isSneaking()) {
                    GL11.glTranslatef(0, 0.06F, 0);
                }
                if (childScl > 1.5F) {
                    GL11.glTranslatef(0, -0.015F, 0);
                    GL11.glScalef(1.025F, 1.025F, 1.025F);
                } else {
                    if (childScl > 1) {
                        GL11.glTranslatef(0, -0.01F, 0);
                        GL11.glScalef(1.025F, 1.025F, 1.025F);
                    } else {
                        GL11.glTranslatef(0, 0.0025F, 0);
                        GL11.glScalef(1.02F, 1.02F, 1.02F);
                    }
                }
                renderJBRA(m, player);
                setHidden(limbs, false);
                setHidden(femaleParts, false);

                postRenderOverlay();
            }
        } catch (Exception ignored) {
        }
    }

    private static void renderJBRA(ModelBiped m, EntityPlayer player) throws IllegalAccessException {
        Field[] rot = JBRA.rot;
        m.render(player, rot[0].getFloat(m), rot[1].getFloat(m), rot[2].getFloat(m), rot[3].getFloat(m), rot[4].getFloat(m), rot[5].getFloat(m));
    }

    private static void setHidden(ModelRenderer[] parts, boolean hidden) {
        for (ModelRenderer part : parts) {
            if (part != null)
                part.isHidden = hidden;
        }
    }

    // MorePlayerModels, looked up once instead of on every overlay render
    private static final class MPM {
        private static boolean resolved;
        private static Field isArmor;

        static Field isArmor() {
            if (!resolved) {
                resolved = true;
                try {
                    isArmor = Class.forName("noppes.mpm.client.model.ModelMPM").getDeclaredField("isArmor");
                    isArmor.setAccessible(true);
                } catch (Exception ignored) {
                    isArmor = null;
                }
            }
            return isArmor;
        }
    }

    // JBRA through reflection, looked up once: doing it on every frame threw an exception per player
    private static final class JBRA {
        private static final String[] FEMALE_PARTS = {"Brightarm", "Bleftarm", "rightleg", "leftleg", "body", "hip",
            "waist", "bottom", "Bbreast", "Bbreast2", "breast", "breast2"};

        private static boolean resolved, available, genResolved;
        static Field modelMain, bodyY, dbcY;
        static Field[] rot, femaleParts;
        static Method childSclGet;
        private static Method genGet;

        static boolean available() {
            if (!resolved) {
                resolved = true;
                Class<?> render;
                try {
                    render = Class.forName("JinRyuu.JBRA.RenderPlayerJBRA");
                } catch (Exception e) {
                    return false;
                }
                try {
                    Class<?> modelDBC = Class.forName("JinRyuu.JBRA.ModelBipedDBC");
                    Class<?> modelBody = Class.forName("JinRyuu.JRMCore.entity.ModelBipedBody");
                    modelMain = render.getField("modelMain");
                    childSclGet = render.getMethod("childSclGet");
                    rot = new Field[6];
                    for (int i = 0; i < rot.length; i++) {
                        rot[i] = modelDBC.getField("rot" + (i + 1));
                    }
                    dbcY = modelDBC.getField("y");
                    bodyY = modelBody.getField("y");
                    femaleParts = new Field[FEMALE_PARTS.length];
                    for (int i = 0; i < femaleParts.length; i++) {
                        femaleParts[i] = modelBody.getField(FEMALE_PARTS[i]);
                    }
                    available = true;
                } catch (Exception e) {
                    LogWriter.error("JBRA found but its model could not be read, skin overlays will not draw on DBC bodies", e);
                }
            }
            return available;
        }

        static Method genGet() {
            if (!genResolved) {
                genResolved = true;
                try {
                    genGet = Class.forName("JinRyuu.JBRA.RenderPlayerJBRA").getMethod("genGet");
                } catch (Exception ignored) {
                    genGet = null;
                }
            }
            return genGet;
        }
    }
}
