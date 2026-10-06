package outside.graphic;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

public class TextureAtlas {
	private BufferedImage atlas;
    private int width;
    private int height;

    
    // Constructeurs
    public TextureAtlas(String path) {
    	try (InputStream in = TextureAtlas.class.getResourceAsStream("/" + path)) {
    		if (in == null)
    			throw new IllegalStateException("- Texture introuvable : " + path);
			atlas = ImageIO.read(in);
		} catch (IOException e) {
			throw new IllegalStateException("- Impossible de lire la texture : " + path, e);
		}
    }

    
    // Accesseurs
    public BufferedImage getAtlas() { return atlas; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
