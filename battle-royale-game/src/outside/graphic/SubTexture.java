package outside.graphic;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

import org.lwjgl.BufferUtils;

public class SubTexture {
    private float X, Y, WIDTH, HEIGHT;
	private final BufferedImage subtexture;
	private int subTextureId;

    
    // Constructeurs
    public SubTexture(TextureAtlas ta, int x, int y, int w, int h) {
    	X = x;
    	Y = y;
    	WIDTH = w;
    	HEIGHT = h;
    	subtexture = ta.getAtlas().getSubimage(x, y, w, h);
    	loadTexture(subtexture);
    }

    
    // Accesseurs
    public float getX() { return X; }
    public float getY() { return Y; }
    public float getWIDTH() { return WIDTH; }
    public float getHEIGHT() { return HEIGHT; }
    public int getSubTextureID() { return subTextureId; }
    
    
    // Méthodes
    // Charge l'image en tant que texture
    private void loadTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        buffer.flip();

        int ti = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, ti);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        subTextureId = ti;
    }

    
}
