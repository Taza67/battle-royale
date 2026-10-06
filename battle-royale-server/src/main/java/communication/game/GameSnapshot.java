package communication.game;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * État de la partie transmis par le jeu à chaque échange : en-tête de 23 octets
 * puis 9 octets par joueur, entiers en big-endian
 * @param phase Phase de la manche
 * @param alive Nombre de joueurs vivants
 * @param total Nombre total de joueurs
 * @param winnerId Identifiant du vainqueur, -1 si aucun
 * @param maxLife Points de vie maximum
 * @param zone Zone sûre actuelle
 * @param nextZone Prochaine zone sûre
 * @param secondsLeft Secondes avant la prochaine étape
 * @param players États des joueurs, dans l'ordre reçu
 * @author mourtaza
 */
public record GameSnapshot(Phase phase, int alive, int total, int winnerId, int maxLife,
		Zone zone, Zone nextZone, int secondsLeft, List<PlayerSnapshot> players) {
	/**
	 * Taille de l'en-tête, en octets
	 */
	public static final int HEADER_SIZE = 23;
	/**
	 * Taille de l'état d'un joueur, en octets
	 */
	public static final int PLAYER_SIZE = 9;
	/**
	 * Nombre maximal de joueurs décrits dans un état (identifiants sur un octet)
	 */
	public static final int MAX_PLAYERS = 128;
	/**
	 * Taille maximale d'un état valide, en octets
	 */
	public static final int MAX_SIZE = HEADER_SIZE + MAX_PLAYERS * PLAYER_SIZE;

	/**
	 * Phase d'une manche
	 */
	public enum Phase {
		/** Échauffement, avant le premier rétrécissement */
		WARMUP(0, "warmup"),
		/** Combat */
		BATTLE(1, "battle"),
		/** Manche terminée */
		OVER(2, "over");

		private final int code;
		private final String wireName;

		Phase(int code, String wireName) {
			this.code = code;
			this.wireName = wireName;
		}

		/**
		 * Retourne le code binaire de la phase
		 * @return Code envoyé par le jeu
		 */
		public int code() { return code; }

		/**
		 * Retourne le nom de la phase dans les messages JSON
		 * @return Nom JSON
		 */
		public String wireName() { return wireName; }

		/**
		 * Retrouve une phase à partir de son code binaire
		 * @param code Code reçu
		 * @return Phase correspondante
		 * @throws ProtocolException si le code est inconnu
		 */
		public static Phase fromCode(int code) throws ProtocolException {
			for (Phase p : values())
				if (p.code == code)
					return p;
			throw new ProtocolException("Phase inconnue : " + code);
		}
	}

	/**
	 * Copie défensive de la liste des joueurs
	 */
	public GameSnapshot {
		players = List.copyOf(players);
	}

	/**
	 * Décode un état reçu du jeu
	 * @param data Octets de l'état (exactement S octets)
	 * @return État décodé
	 * @throws ProtocolException si la taille ou une valeur ne respecte pas le protocole
	 */
	public static GameSnapshot decode(byte[] data) throws ProtocolException {
		if (data.length < HEADER_SIZE)
			throw new ProtocolException("État trop court : " + data.length + " octets");
		if ((data.length - HEADER_SIZE) % PLAYER_SIZE != 0)
			throw new ProtocolException("Taille d'état incohérente : " + data.length + " octets");
		if (data.length > MAX_SIZE)
			throw new ProtocolException("État trop long : " + data.length + " octets");

		ByteBuffer buffer = ByteBuffer.wrap(data);
		try {
			Phase phase = Phase.fromCode(buffer.get());
			int alive = Byte.toUnsignedInt(buffer.get());
			int total = Byte.toUnsignedInt(buffer.get());
			int winnerId = buffer.get();
			int maxLife = Byte.toUnsignedInt(buffer.get());
			Zone zone = readZone(buffer);
			Zone nextZone = readZone(buffer);
			int secondsLeft = Math.max(0, buffer.getShort());

			int count = (data.length - HEADER_SIZE) / PLAYER_SIZE;
			List<PlayerSnapshot> players = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				int id = Byte.toUnsignedInt(buffer.get());
				PlayerSnapshot.Status status = PlayerSnapshot.Status.fromCode(buffer.get());
				int life = Byte.toUnsignedInt(buffer.get());
				int x = buffer.getShort();
				int y = buffer.getShort();
				int kills = Byte.toUnsignedInt(buffer.get());
				int rank = Byte.toUnsignedInt(buffer.get());
				players.add(new PlayerSnapshot(id, status, life, x, y, kills, rank));
			}
			return new GameSnapshot(phase, alive, total, winnerId, maxLife, zone, nextZone, secondsLeft,
				Collections.unmodifiableList(players));
		} catch (BufferUnderflowException e) {
			throw new ProtocolException("État tronqué");
		}
	}

	/**
	 * Lit une zone (4 entiers courts)
	 * @param buffer Tampon positionné sur la zone
	 * @return Zone lue
	 */
	private static Zone readZone(ByteBuffer buffer) {
		return new Zone(buffer.getShort(), buffer.getShort(), buffer.getShort(), buffer.getShort());
	}

	/**
	 * Recherche l'état d'un joueur
	 * @param id Identifiant du joueur
	 * @return État du joueur, ou null s'il n'apparaît pas dans cet état
	 */
	public PlayerSnapshot player(int id) {
		for (PlayerSnapshot p : players)
			if (p.id() == id)
				return p;
		return null;
	}
}
