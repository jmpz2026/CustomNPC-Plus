package noppes.npcs.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extends {@link ThreadDownloadImageData} only for its type: every method is overridden.
 * Resource reloads flush any {@code SimpleTexture} whose location is not a file in a resource
 * pack (MCPatcher, bundled with Angelica) and spare downloaded textures by checking for this
 * class. Extending {@code SimpleTexture} got NPC skins dropped on every language or resource
 * pack change; the NPC keeps its {@code textureLocation}, so it rendered with the missing
 * texture until restart.
 */
@SideOnly(Side.CLIENT)
public class ImageDownloadAlt extends ThreadDownloadImageData {
    private static final Logger logger = LogManager.getLogger();
    private static final AtomicInteger threadDownloadCounter = new AtomicInteger(0);
    private final File cacheFile;
    private final String imageUrl;
    private final IImageBuffer imageBuffer;
    // Freed once it is on the GPU: only the size is kept
    private volatile BufferedImage bufferedImage;
    private int imageWidth;
    private int imageHeight;
    private Thread imageThread;
    private boolean textureUploaded;

    public ImageDownloadAlt(File file, String url, ResourceLocation resource, IImageBuffer buffer) {
        super(file, url, resource, buffer);
        this.cacheFile = file;
        this.imageUrl = url;
        this.imageBuffer = buffer;
    }

    private void checkTextureUploaded() {
        if (!this.textureUploaded) {
            if (this.bufferedImage != null) {
                if (this.textureLocation != null) {
                    this.deleteGlTexture();
                }

                TextureUtil.uploadTextureImage(super.getGlTextureId(), this.bufferedImage);
                this.textureUploaded = true;
                this.bufferedImage = null;
            }
        }
    }

    public int getGlTextureId() {
        this.checkTextureUploaded();
        return super.getGlTextureId();
    }

    public void setBufferedImage(BufferedImage p_147641_1_) {
        if (p_147641_1_ != null) {
            this.imageWidth = p_147641_1_.getWidth();
            this.imageHeight = p_147641_1_.getHeight();
        }
        this.bufferedImage = p_147641_1_;

        if (this.imageBuffer != null) {
            this.imageBuffer.func_152634_a();
        }
    }

    // The image arrived, whether it is still waiting for upload or already on the GPU
    public boolean isImageReady() {
        return this.textureUploaded || this.bufferedImage != null;
    }

    public int getImageWidth() {
        return this.imageWidth;
    }

    public int getImageHeight() {
        return this.imageHeight;
    }

    public void loadTexture(IResourceManager resourceManager) throws IOException {
        // A resource reload would otherwise paint the fallback texture over the uploaded image
        if (this.textureUploaded) {
            return;
        }

        if (this.bufferedImage == null && this.textureLocation != null) {
            super.loadTexture(resourceManager);
        }

        // An image given by setBufferedImage has nothing to download
        if (this.imageThread == null && this.bufferedImage == null) {
            if (this.cacheFile != null && this.cacheFile.isFile()) {
                logger.debug("Loading http texture from local cache ({})", new Object[]{this.cacheFile});

                try {
                    BufferedImage cached = ImageIO.read(this.cacheFile);
                    this.setBufferedImage(this.imageBuffer != null ? this.imageBuffer.parseUserSkin(cached) : cached);
                } catch (IOException ioexception) {
                    logger.error("Couldn\'t load skin " + this.cacheFile, ioexception);
                    this.loadTextureFromServer();
                }
            } else {
                this.loadTextureFromServer();
            }
        }
    }

    protected void loadTextureFromServer() {
        if (this.imageUrl == null || !this.imageUrl.startsWith("http"))
            return;

        this.imageThread = new Thread("Texture Downloader #" + threadDownloadCounter.incrementAndGet()) {
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(ImageDownloadAlt.this.imageUrl);
                    connection = (HttpURLConnection) (url).openConnection(Minecraft.getMinecraft().getProxy());
                    connection.setDoInput(true);
                    connection.setDoOutput(false);
                    connection.setRequestProperty("Content-Type", "image/png");
                    connection.setRequestProperty("Expect", "100-continue");
                    //connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 5.1; rv:19.0) Gecko/20100101 Firefox/19.0");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                    // Modify Accept Redirect
                    if (isImgurLink(url)) {
                        connection.setRequestProperty("Accept", "*/*");
                    }

                    connection.connect();

                    if (connection.getResponseCode() / 100 != 2) {
                        return;
                    }

                    BufferedImage bufferedimage;

                    if (ImageDownloadAlt.this.cacheFile != null) {
                        try (InputStream in = connection.getInputStream()) {
                            FileUtils.copyInputStreamToFile(in, ImageDownloadAlt.this.cacheFile);
                        }
                        bufferedimage = ImageIO.read(ImageDownloadAlt.this.cacheFile);
                    } else {
                        try (InputStream in = connection.getInputStream()) {
                            bufferedimage = ImageIO.read(in);
                        }
                    }

                    if (ImageDownloadAlt.this.imageBuffer != null) {
                        bufferedimage = ImageDownloadAlt.this.imageBuffer.parseUserSkin(bufferedimage);
                    }

                    ImageDownloadAlt.this.setBufferedImage(bufferedimage);
                } catch (MalformedURLException ignored) {
                } catch (Exception exception) {
                    ImageDownloadAlt.logger.error("Couldn\'t download http texture", exception);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        };
        this.imageThread.setDaemon(true);
        this.imageThread.start();
    }


    // Check if the URL is an Imgur link
    private static boolean isImgurLink(URL url) {
        return url.getHost().endsWith("imgur.com");
    }
}
