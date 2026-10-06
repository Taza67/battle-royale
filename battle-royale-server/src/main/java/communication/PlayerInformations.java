package communication;

/**
 * Classe représentant les informations d'un joueur (message)
 * @author mourtaza
 *
 */
public class PlayerInformations {
	/**
	 * Type du message ('informations')
	 */
	private String type;
	/**
	 * Pseudo du joueur entré
	 */
	private String pseudo;
	
	
	/**
	 * Retourne le type du message
	 * @return Type du message
	 * 
	 * @see PlayerInformations#type
	 */
	public String getType() { return type; }
	/**
	 * Retourne le pseudo entré par le joueur
	 * @return Pseudo
	 * 
	 * @see PlayerInformations#pseudo
	 */
	public String getPseudo() { return pseudo; }
}
