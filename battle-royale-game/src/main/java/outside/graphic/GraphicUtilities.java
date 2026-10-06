package outside.graphic;

import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glVertex2f;

import org.lwjgl.opengl.GL11;

import inside.geometry.Polygon;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;
import outside.Game;

public class GraphicUtilities {

	// Dessine le polygone avec la couleur donnée
	public static void drawPolygon(Polygon p, Color c) {
		int verticesNumber = p.getVerticesNumber();

		GL11.glDisable(GL_TEXTURE_2D);
		
		// Couleur de remplissage
		setColor(c);

	    // Dessin du polygone
	    GL11.glBegin(GL11.GL_POLYGON);
		for (int i = 0; i < verticesNumber; i++) {
		    Vertice v = p.getVertice(i);

		    GL11.glVertex2f(v.getX(), v.getY());
		}
		GL11.glEnd();
		
		GL11.glEnable(GL_TEXTURE_2D);
	}
	
	// Dessine 
	public static void drawRectangleTexture(Rectangle r, int textureNumber) {
		float rectangleWidth = r.getWidth();
		float rectangleHeight = r.getHeight();

        SubTexture st = Game.TEXTURE_MANAGER.getSubTexture(textureNumber);
        
        GL11.glBindTexture(GL_TEXTURE_2D, st.getSubTextureID());
        
		float scaleX = rectangleWidth / st.getWIDTH();
		float scaleY = rectangleHeight / st.getHEIGHT();
		
        glBegin(GL_QUADS);
        glTexCoord2f(
            	st.getX(), 
            	st.getY()
        );
        glVertex2f(r.getTopLeftCorner().getX(), r.getTopLeftCorner().getY());

        glTexCoord2f(
            	st.getX() + scaleX, 
            	st.getY()
        );
        glVertex2f(r.getVertice(1).getX(), r.getVertice(1).getY());

        glTexCoord2f(
            	st.getX() + scaleX, 
            	st.getY() + scaleY
        );
        glVertex2f(r.getVertice(2).getX(), r.getVertice(2).getY());

        glTexCoord2f(
            	st.getX(), 
            	st.getY() + scaleY
        );
        glVertex2f(r.getVertice(3).getX(), r.getVertice(3).getY());
        
        glEnd();
	}
	
	public static void drawPlayerRectangleTexture(Rectangle r, int textureNumber) {
	    SubTexture st = Game.TEXTURE_MANAGER.getSubTexture(textureNumber);

	    GL11.glBindTexture(GL_TEXTURE_2D, st.getSubTextureID());

	    // Utilisez les coordonnées de texture normalisées (entre 0 et 1)
//	    float texCoordX1 = st.getX() / st.getWIDTH();
//	    float texCoordY1 = st.getY() / st.getHEIGHT();
//	    float texCoordX2 = (st.getX() + st.getWIDTH()) / st.getWIDTH();
//	    float texCoordY2 = (st.getY() + st.getHEIGHT()) / st.getHEIGHT();

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		GL11.glColor3f(1.0f, 1.0f, 1.0f);
	    
	    glBegin(GL_QUADS);
	    glTexCoord2f(0, 0);
	    glVertex2f(r.getTopLeftCorner().getX(), r.getTopLeftCorner().getY());

	    glTexCoord2f(1, 0);
	    glVertex2f(r.getVertice(1).getX(), r.getVertice(1).getY());

	    glTexCoord2f(1, 1);
	    glVertex2f(r.getVertice(2).getX(), r.getVertice(2).getY());

	    glTexCoord2f(0, 1);
	    glVertex2f(r.getVertice(3).getX(), r.getVertice(3).getY());

	    glEnd();
	}


	// Réinitialise la zone de rendu
	public static void reinitViewPort() {
		GL11.glViewport(
			0, 0, outside.Game.WINDOW_WIDTH, outside.Game.WINDOW_HEIGHT
		);
	}

	// Efface l'écran avec la couleur de fond donnée
	public static void cleanScreen(Color c) {
		GL11.glClearColor(c.getR() / 255.0f, c.getG()/ 255.0f, c.getB() / 255.0f, 1.0f);
	    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
	}

	// Applique la couleur
	public static void setColor(Color c) {
	    GL11.glColor3f(c.getR() / 255.0f, c.getG()/ 255.0f, c.getB() / 255.0f);
	}
}
