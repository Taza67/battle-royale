package communication;

/**
 * Classe représentant les informations sur une attaque (message)
 * @author mourtaza
 *
 */
public class AttackInformations {
	/**
	 * Type du message ("attaque")
	 */
	private String type;
	/**
	 * Nature de l'attaque (1 : épee, 2 : arme à feu)
	 */
	private int form;
	
	
	/**
	 * Retourne le type du message
	 * @return Type du message
	 * 
	 * @see AttackInformations#type
	 */
	public String getType() { return type; }
	
	/**
	 * Retourne la forme de l'attaque
	 * @return Forme de l'attaque
	 * 
	 * @see AttackInformations#form
	 */
	public int getForm() { return form; }
}
