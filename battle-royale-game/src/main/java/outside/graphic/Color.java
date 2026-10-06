package outside.graphic;

import java.util.Random;

public class Color {
	private float r, g, b;

	// Construteurs
	public Color(float r, float g, float b) {
		this.r = r;
		this.g = g;
		this.b = b;
	}


	// Accesseurs
	public float getR() { return r; }
	public float getG() { return g; }
	public float getB() { return b; }

	// Mutateurs
	public void setR(float r) { this.r = r; }
	public void setG(float g) { this.g = g; }
	public void setB(float b) { this.b = b; }


	// Méthodes
	// Retourne une couleur générée alétoirement
	public static Color randomColor(int seed) {
		Random rand = new Random(seed);
		return new Color(
			rand.nextFloat() * 255f,
			rand.nextFloat() * 255f,
			rand.nextFloat() * 255f
		);
	}
}
