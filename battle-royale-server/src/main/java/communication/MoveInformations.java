package communication;

/**
 * Classe représentant les informations sur un déplacement
 * @author mourtaza
 *
 */
public class MoveInformations {
	/**
	 * Type de l'action ("déplacement")
	 */
	private String type;
	/**
	 * Direction du déplacement (Est : 0, Sud : 6, ...)
	 */
	private int direction;
	/**
	 * Vitesse du déplacement
	 */
	private int speed;
	
	
	/**
	 * Retourne le type de l'action ("déplacement")
	 * @return Type de l'action
	 * 
	 * @see MoveInformations#type
	 */
	public String getType() { return type; }
	
	/**
	 * Retourne la direction du déplacement
	 * @return Direction du déplacement
	 * 
	 * @see MoveInformations#direction
	 */
	public int getDirection() { return direction; }
	
	/**
	 * Retourne la vitesse de déplacement
	 * @return Vitesse de déplacement
	 * 
	 * @see MoveInformations#speed
	 */
	public int getSpeed() { return speed; }
}
