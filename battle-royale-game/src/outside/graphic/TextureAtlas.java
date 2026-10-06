package outside.graphic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class TextureAtlas {
	private BufferedImage atlas;
    private int width;
    private int height;

    
    // Constructeurs
    public TextureAtlas(String path) {
    	try {
			atlas = ImageIO.read(new File(path));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
    }

    
    // Accesseurs
    public BufferedImage getAtlas() { return atlas; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
