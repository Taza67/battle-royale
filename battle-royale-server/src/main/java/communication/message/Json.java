package communication.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Point d'accès unique à la sérialisation JSON des messages WebSocket
 * @author mourtaza
 *
 */
public final class Json {
	/**
	 * Instance partagée de Gson (thread-safe), les champs nuls sont conservés
	 * pour respecter le protocole (`"error": null`, `"winner": null`)
	 */
	public static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private Json() {}

	/**
	 * Convertit un message sortant en texte JSON
	 * @param message Message à convertir
	 * @return Texte JSON du message
	 */
	public static String write(ServerMessage message) {
		return GSON.toJson(message);
	}
}
