package inside.geometry;

import java.util.Random;

import inside.IConfig;

public class Vertice implements IConfig {
	private volatile float x, y;


	// Constructeurs
	public Vertice(float x, float y) {
		this.x = x;
		this.y = y;
	}


	// Accesseurs
	public float getX() { return x; }
	public float getY() { return y; }

	// Mutateurs
	public void setX(float x) { this.x = x; }
	public void setY(float y) { this.y = y; }


	// Méthodes
	@Override
	public String toString() {
		return "( " + x + ", " + y + " )";
	}

	// Avance le point, des coordonnées du point donné
	public Vertice add(Vertice v) {
		return new Vertice(x + v.x, y + v.y);
	}

	// Recule le point, des coordonnées du point donné
	public Vertice substract(Vertice v) {
		return new Vertice(x - v.x, y - v.y);
	}

	// Retourne un point aléatoire
	public static Vertice random(float xLow, float xUp, float yLow, float yUp) {
		Random random = new Random();
        float x = xLow + (xUp - xLow) * random.nextFloat();
        float y = yLow + (yUp - yLow) * random.nextFloat();

        return new Vertice(x, y);
	}

	// Vérifie si le point est à l'intérieur d'un rectangle fictif formé des deux points donnés
	public boolean isInside(Vertice topLeftCorner, Vertice bottomRightCorner) {
		return x >= topLeftCorner.x && x <= bottomRightCorner.x &&
			   y >= topLeftCorner.y && y <= bottomRightCorner.y;
	}

	// Applique une rotation au point
	public void rotate(float angle) {
		x = (float)(x * Math.cos(angle) - y * Math.sin(angle));
        y = (float)(x * Math.sin(angle) + y * Math.cos(angle));
	}

	// Applique une translation au point dans une certaine direction
	public Vertice translate(int direction, float distance) {
		switch (direction) {
		case EAST:
			return this.add(new Vertice(distance, 0));
		case NORTH_EAST:
			return this.add(new Vertice(distance, -distance));
		case NORTH:
			return this.substract(new Vertice(0, distance));
		case NORTH_WEST:
			return this.substract(new Vertice(distance, distance));
		case WEST:
			return this.substract(new Vertice(distance, 0));
		case SOUTH_WEST:
			return this.add(new Vertice(-distance, distance));
		case SOUTH:
			return this.add(new Vertice(0, distance));
		case SOUTH_EAST:
			return this.add(new Vertice(distance, distance));
		}

		return this;
	}
}

