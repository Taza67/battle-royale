package inside.geometry;

public class Rectangle extends Polygon {
	private volatile Vertice topLeftCorner;
	private volatile float width, height;


	// Constructeurs
	public Rectangle(Vertice c, Vertice tlc, float w, float h) {
		super(c);
		addVertice(tlc);
		topLeftCorner = getVertice(0);
		width = w;
		height = h;
		generateCorners();
	}
	public Rectangle(Vertice c, Vertice tlc, Vertice brc) {
		this(c, tlc, brc.getX() - tlc.getX(), brc.getY() - tlc.getY());
	}


	// Accesseurs
	public Vertice getTopLeftCorner() { return topLeftCorner; }
	public float getWidth() { return width; }
	public float getHeight() { return height; }


	// Mutateurs
	public void setTopLeftCorner(Vertice tlc) {
		topLeftCorner = tlc;
		updateCorners();
	}
	public void setWidth(float w) {
		float diff = width - w;

		topLeftCorner = topLeftCorner.add(new Vertice(diff / 2f, 0));
		width = w;
		updateCorners();
	}
	public void setHeight(float h) {
		float diff = height - h;

		topLeftCorner = topLeftCorner.add(new Vertice(0, diff / 2f));
		height = h;
		updateCorners();
	}


	// Méthodes
	// Génère les autres extrémités du rectangle
	private void generateCorners() {
		addVertice(new Vertice(topLeftCorner.getX() + width, topLeftCorner.getY()));
		addVertice(new Vertice(topLeftCorner.getX() + width, topLeftCorner.getY() + height));
		addVertice(new Vertice(topLeftCorner.getX(), topLeftCorner.getY() + height));
	}

	// Mets à jour les extrémités du rectangle
	private void updateCorners() {
		setVertice(0, topLeftCorner);
		setVertice(1, new Vertice(topLeftCorner.getX() + width, topLeftCorner.getY()));
		setVertice(2, new Vertice(topLeftCorner.getX() + width, topLeftCorner.getY() + height));
		setVertice(3, new Vertice(topLeftCorner.getX(), topLeftCorner.getY() + height));
	}

//	// Multiplie les dimensions du rectangle par le coefficient
//	public void scale(float coef) {
//		float newHeight = height * coef,
//			  newWidth = width * coef,
//			  diffX = width - newWidth,
//			  diffY = height - newHeight;
//
//		topLeftCorner = topLeftCorner.add(new Vertice(diffX / 2f, diffY / 2f));
//		height = newHeight;
//		width = newWidth;
//		updateCorners();
//	}

	// Zoom le rectangle selon une certaine valeur
	public void scale(float s) {
		float newHeight = height + s,
			  newWidth = width + s,
			  diffX = width - newWidth,
			  diffY = height - newHeight;

		topLeftCorner = topLeftCorner.add(new Vertice(diffX / 2f, diffY / 2f));
		height = newHeight;
		width = newWidth;
		updateCorners();
	}

	// Vérifie si le rectangle intersecte un autre
	public boolean intersect(Rectangle r) {
	    float r1x1 = Math.min(this.topLeftCorner.getX(), this.getVertice(2).getX());
	    float r1y1 = Math.min(this.topLeftCorner.getY(), this.getVertice(2).getY());
	    float r1x2 = Math.max(this.topLeftCorner.getX(), this.getVertice(2).getX());
	    float r1y2 = Math.max(this.topLeftCorner.getY(), this.getVertice(2).getY());

	    float r2x1 = Math.min(r.topLeftCorner.getX(), r.getVertice(2).getX());
	    float r2y1 = Math.min(r.topLeftCorner.getY(), r.getVertice(2).getY());
	    float r2x2 = Math.max(r.topLeftCorner.getX(), r.getVertice(2).getX());
	    float r2y2 = Math.max(r.topLeftCorner.getY(), r.getVertice(2).getY());

	    return (r1x1 <= r2x2 && r1x2 >= r2x1 && r1y1 <= r2y2 && r1y2 >= r2y1);
	}

	// Vérifie si le rectangle contient celui donné en paramètre
	public boolean contain(Rectangle r) {
	    return r.topLeftCorner.getX() >= topLeftCorner.getX() && r.topLeftCorner.getY() >= topLeftCorner.getY()
            && r.getVertice(2).getX() <= getVertice(2).getX()
            && r.getVertice(2).getY() <= getVertice(2).getY();
	}
	
	// Effectue une rotation
	public void rotate(float angle) {
		setVertice(0, 
			new Vertice(
				(float)(center.getX() + (width / 2f * Math.cos(angle) - height / 2f * Math.sin(angle))),
				(float)(center.getY() + (width / 2f * Math.sin(angle) + height / 2f * Math.cos(angle)))
			)
		);
		
		setVertice(1,
			new Vertice(
				(float)(center.getX() + (width / 2f * Math.cos(angle) + height / 2f * Math.sin(angle))),
				(float)(center.getY() + (width / 2f * Math.sin(angle) - height / 2f * Math.cos(angle)))
			)
		);
		
		setVertice(2,
			new Vertice(
				(float)(center.getX() + (- width / 2f * Math.cos(angle) + height / 2f * Math.sin(angle))),
				(float)(center.getY() + (- width / 2f * Math.sin(angle) - height / 2f * Math.cos(angle)))
			)
		);
		
		setVertice(3,
			new Vertice(
				(float)(center.getX() + (- width / 2f * Math.cos(angle) - height / 2f * Math.sin(angle))),
				(float)(center.getY() + (- width / 2f * Math.sin(angle) + height / 2f * Math.cos(angle)))
			)
		);
	}
}
