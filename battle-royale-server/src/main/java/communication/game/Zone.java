package communication.game;

/**
 * Zone rectangulaire de la carte, en pixels
 * @param x1 Abscisse du coin supérieur gauche
 * @param y1 Ordonnée du coin supérieur gauche
 * @param x2 Abscisse du coin inférieur droit
 * @param y2 Ordonnée du coin inférieur droit
 * @author mourtaza
 */
public record Zone(int x1, int y1, int x2, int y2) {}
