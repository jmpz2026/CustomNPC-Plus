package noppes.npcs.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.ClientCacheHandler;
import noppes.npcs.client.ImageDownloadAlt;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class ImageData {
    private final ResourceLocation location;
    private final boolean isUrl;

    private ImageDownloadAlt imageDownloadAlt = null;

    private int totalWidth, totalHeight;
    private boolean gotWidthHeight;
    private boolean invalid;

    public ImageData(String directory) {
        this.location = new ResourceLocation(directory);
        if (directory.startsWith("https://")) {
            this.isUrl = true;
            TextureManager texturemanager = Minecraft.getMinecraft().getTextureManager();
            this.imageDownloadAlt = new ImageDownloadAlt(null, directory, new ResourceLocation("customnpcs:textures/gui/invisible.png"), new ImageBufferDownloadAlt(true, false));
            texturemanager.loadTexture(this.location, this.imageDownloadAlt);
        } else {
            this.isUrl = false;
        }
    }

    // For NPC Skins Only
    public ImageData(String directory, boolean x64, ResourceLocation resource) {
        this.location = resource;
        this.isUrl = true;
        TextureManager texturemanager = Minecraft.getMinecraft().getTextureManager();
        this.imageDownloadAlt = new ImageDownloadAlt(null, directory, SkinManager.field_152793_a, new ImageBufferDownloadAlt(x64));
        texturemanager.loadTexture(this.location, this.imageDownloadAlt);
    }

    public boolean imageLoaded() {
        if (!this.gotWidthHeight) {
            try {
                if (!this.isUrl) {
                    this.getWidthHeight();
                } else {
                    this.getURLWidthHeight();
                }
            } catch (Exception ignored) {
            }
        }
        return !this.invalid && this.location != null && this.gotWidthHeight;
    }

    // Downloaded textures belong to this cache; resource pack textures are shared, so they stay
    public void release() {
        if (this.isUrl) {
            ClientCacheHandler.releaseTexture(this.location);
        }
    }

    public boolean invalid() {
        return this.invalid;
    }

    public void bindTexture() {
        ResourceLocation location = this.getLocation();
        if (location != null && !this.invalid) {
            try {
                Minecraft.getMinecraft().getTextureManager().bindTexture(location);
            } catch (Exception exception) {
                this.invalid = true;
            }
        }
    }

    public void renderEngineBind() {
        ResourceLocation location = this.getLocation();
        if (location != null && !this.invalid) {
            try {
                RenderNPCInterface.staticRenderManager.renderEngine.bindTexture(location);
            } catch (Exception exception) {
                this.invalid = true;
            }
        }
    }

    public ResourceLocation getLocation() {
        return this.isUrl && !this.imageDownloadAlt.isImageReady() ? null : this.location;
    }

    private void getWidthHeight() throws IOException {
        if (this.invalid) {
            return;
        }

        InputStream inputstream = null;

        try {
            IResource iresource = Minecraft.getMinecraft().getResourceManager().getResource(this.location);
            inputstream = iresource.getInputStream();
            BufferedImage bufferedimage = ImageIO.read(inputstream);
            this.gotWidthHeight = true;
            this.totalWidth = bufferedimage.getWidth();
            this.totalHeight = bufferedimage.getHeight();
            correctWidthHeight();
        } catch (Exception e) {
            this.invalid = true;
        } finally {
            if (inputstream != null) {
                inputstream.close();
            }
        }
    }

    private void getURLWidthHeight() {
        if (this.imageDownloadAlt.isImageReady() && !this.invalid) {
            this.gotWidthHeight = true;
            this.totalWidth = this.imageDownloadAlt.getImageWidth();
            this.totalHeight = this.imageDownloadAlt.getImageHeight();
            correctWidthHeight();
        }
    }

    private void correctWidthHeight() {
        this.totalWidth = Math.max(this.totalWidth, 1);
        this.totalHeight = Math.max(this.totalHeight, 1);
    }

    public int getTotalWidth() {
        return this.gotWidthHeight ? this.totalWidth : -1;
    }

    public int getTotalHeight() {
        return this.gotWidthHeight ? this.totalHeight : -1;
    }
}
