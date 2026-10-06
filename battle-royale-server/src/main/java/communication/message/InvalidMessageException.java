package communication.message;

/**
 * Exception levée lorsqu'un message reçu d'un client web est invalide
 * @author mourtaza
 *
 */
public class InvalidMessageException extends Exception {
	/**
	 * Construit l'exception
	 * @param reason Raison du refus
	 */
	public InvalidMessageException(String reason) {
		super(reason);
	}

	/**
	 * Construit l'exception à partir d'une erreur sous-jacente
	 * @param reason Raison du refus
	 * @param cause Erreur d'origine
	 */
	public InvalidMessageException(String reason, Throwable cause) {
		super(reason, cause);
	}
}
