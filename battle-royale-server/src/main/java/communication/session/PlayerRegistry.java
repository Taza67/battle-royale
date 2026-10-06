package communication.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import communication.message.ServerMessage;

/**
 * Répertoire des joueurs inscrits, indexés par pseudo (insensible à la casse) et par
 * identifiant, et de leurs jetons de reprise conservés pendant toute la vie du serveur.
 * Non thread-safe : toutes les méthodes sont appelées sous le verrou de {@link GameSession}.
 * @author mourtaza
 */
final class PlayerRegistry {
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Map<String, Player> byKey = new HashMap<>();
	private final TreeMap<Integer, Player> byId = new TreeMap<>();
	/**
	 * Jetons de reprise par pseudo (en minuscules), conservés pendant toute la vie du serveur
	 */
	private final Map<String, String> tokens = new HashMap<>();

	/**
	 * Recherche un joueur par son pseudo, sans tenir compte de la casse
	 * @param pseudo Pseudo
	 * @return Joueur, ou null
	 */
	Player get(String pseudo) {
		return byKey.get(key(pseudo));
	}

	/**
	 * Recherche un joueur par son identifiant
	 * @param id Identifiant
	 * @return Joueur, ou null
	 */
	Player get(int id) {
		return byId.get(id);
	}

	/**
	 * Retourne les joueurs inscrits, par identifiant croissant
	 * @return Joueurs
	 */
	Collection<Player> players() {
		return byId.values();
	}

	/**
	 * Indique si aucun joueur n'est inscrit
	 * @return true si le répertoire est vide
	 */
	boolean isEmpty() {
		return byId.isEmpty();
	}

	/**
	 * Inscrit un joueur dans le répertoire
	 * @param player Joueur
	 */
	void add(Player player) {
		byKey.put(key(player.getPseudo()), player);
		byId.put(player.getId(), player);
	}

	/**
	 * Retourne le premier identifiant libre
	 * @return Identifiant, ou -1 si le répertoire est plein
	 */
	int freeId() {
		for (int id = 0; id < GameSession.MAX_PLAYERS; id++)
			if (!byId.containsKey(id))
				return id;
		return -1;
	}

	/**
	 * Prépare le répertoire pour une manche : retire les joueurs absents de la
	 * manche et remet à zéro les statistiques des participants
	 * @param ids Identifiants transmis au jeu
	 * @return Joueurs retirés
	 */
	List<Player> keepOnly(Set<Integer> ids) {
		Set<Integer> inRound = new HashSet<>(ids);
		List<Player> removed = new ArrayList<>();
		for (Iterator<Player> it = byId.values().iterator(); it.hasNext();) {
			Player p = it.next();
			if (!inRound.contains(p.getId())) {
				it.remove();
				byKey.remove(key(p.getPseudo()));
				removed.add(p);
			} else {
				p.resetForRound();
			}
		}
		return removed;
	}

	/**
	 * Retourne la liste des joueurs envoyée à l'administrateur, par identifiant croissant
	 * @return Lignes de la liste des joueurs
	 */
	List<ServerMessage.PlayerEntry> entries() {
		return byId.values().stream().map(Player::toEntry).toList();
	}

	/**
	 * Retourne le jeton de reprise d'un pseudo, créé à la première demande
	 * @param pseudo Pseudo
	 * @return Jeton aléatoire de 128 bits en hexadécimal
	 */
	String tokenFor(String pseudo) {
		return tokens.computeIfAbsent(key(pseudo), k -> {
			byte[] bytes = new byte[16];
			RANDOM.nextBytes(bytes);
			return HexFormat.of().formatHex(bytes);
		});
	}

	/**
	 * Compare en temps constant le jeton fourni à celui du pseudo
	 * @param pseudo Pseudo
	 * @param token Jeton fourni, éventuellement null
	 * @return true si le jeton est celui du pseudo
	 */
	boolean tokenMatches(String pseudo, String token) {
		String expected = tokens.get(key(pseudo));
		return token != null && expected != null
			&& MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
	}

	private static String key(String pseudo) {
		return pseudo.toLowerCase(Locale.ROOT);
	}
}
