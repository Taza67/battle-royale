package outside.graphic.image;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryStack;

import inside.IConfig;

public class ImageUtilities implements IConfig {
	// Retourne l'image contenue dans un buffer
	public static void flipImage(ByteBuffer imageData, int width, int height) {
	    int channels = 4;
	    int lineSize = width * channels;
	    ByteBuffer tempBuffer = BufferUtils.createByteBuffer(lineSize);

	    for (int y = 0; y < height / 2; y++) {
	        int oppositeLine = height - y - 1;

	        // Copie la ligne y vers tempBuffer
	        int yPos = y * lineSize;
	        imageData.position(yPos);
	        tempBuffer.put(imageData);

	        // Copie la ligne oppositeLine vers la ligne y
	        int oppositePos = oppositeLine * lineSize;
	        imageData.position(oppositePos);
	        tempBuffer.position(0);
	        imageData.put(tempBuffer);

	        // Copie tempBuffer vers la ligne oppositeLine
	        imageData.position(oppositePos);
	        tempBuffer.position(0);
	        imageData.put(tempBuffer);
	    }

	    imageData.position(0);
	    tempBuffer.position(0);
	}
	
    // Retourne un buffer qui contiendra les données d'une capture d'écran
    public static ByteBuffer captureWindow(int windowWidth, int windowHeight) {
        // Alloue un tampon pour stocker les données de l'image
        ByteBuffer buffer = BufferUtils.createByteBuffer(windowWidth * windowHeight * 4);

        // Capture les données de l'image à partir du tampon de trame en cours
        GL11.glReadPixels(0, 0, windowWidth, windowHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        flipImage(buffer, windowWidth, windowHeight);
        
        return buffer;
    }
   
    // Retourne un tableau d'octets représentant l'image stockée dans le buffer donné
    public static byte[] convertToPNG(ByteBuffer imageData, int width, int height) throws IOException {
        Path tempFile = Files.createTempFile("lwjgl_screenshot", ".png");
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Écrit les données d'image au format PNG dans un fichier temporaire
            STBImageWrite.stbi_write_png(tempFile.toAbsolutePath().toString(), width, height, 4, imageData, 0);

            // Lit les données du fichier temporaire et les retourne sous forme de tableau d'octets
            try (InputStream inputStream = Files.newInputStream(tempFile)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                return outputStream.toByteArray();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    // Écris le tableau d'octets représentant une image sur le flux de sortie donné
    public static void sendImage(byte[] imageData, DataOutputStream out) throws IOException {
        // Envoie la taille de l'image en octets
        out.writeInt(imageData.length);

        // Envoie les données de l'image
        out.write(imageData);
        out.flush();
    }
}
