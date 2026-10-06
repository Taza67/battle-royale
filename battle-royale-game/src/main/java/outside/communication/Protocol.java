package outside.communication;

import static inside.IConfig.*;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import protocol.GameProtocol;

import inside.Board.PlayerSpec;
import inside.BoardSnapshot;
import inside.BoardSnapshot.PlayerState;
import inside.Command;
import inside.IConfig;
import inside.geometry.Rectangle;

/**
 * Codage et décodage des messages binaires échangés entre le jeu et le serveur web
 * (voir docs/PROTOCOLE.md). Tous les entiers sont en big-endian.
 * @author mourtaza
 */
public final class Protocol {
	/**
	 * Code envoyé par le serveur au début de la poignée de main
	 * @see GameProtocol#START
	 */
	public static final int START = GameProtocol.START;
	/**
	 * Code de pause
	 * @see GameProtocol#PAUSE
	 */
	public static final int PAUSE = GameProtocol.PAUSE;
	/**
	 * Code d'arrêt demandé par l'administrateur
	 * @see GameProtocol#STOP
	 */
	public static final int STOP = GameProtocol.STOP;
	/**
	 * Code de reprise
	 * @see GameProtocol#RESUME
	 */
	public static final int RESUME = GameProtocol.RESUME;
	/**
	 * Type d'action : déplacement
	 * @see GameProtocol#ACTION_MOVE
	 */
	public static final int ACTION_MOVE = GameProtocol.ACTION_MOVE;
	/**
	 * Type d'action : attaque
	 * @see GameProtocol#ACTION_ATTACK
	 */
	public static final int ACTION_ATTACK = GameProtocol.ACTION_ATTACK;
	/**
	 * Taille de l'en-tête de l'état, en octets
	 * @see GameProtocol#HEADER_SIZE
	 */
	public static final int HEADER_SIZE = GameProtocol.HEADER_SIZE;
	/**
	 * Taille de l'état d'un joueur, en octets
	 * @see GameProtocol#PLAYER_SIZE
	 */
	public static final int PLAYER_SIZE = GameProtocol.PLAYER_SIZE;
	/**
	 * Taille maximale acceptée pour un bloc d'actions
	 * @see GameProtocol#MAX_ACTIONS_SIZE
	 */
	public static final int MAX_ACTIONS_SIZE = GameProtocol.MAX_ACTIONS_SIZE;

	private static final Logger LOGGER = Logger.getLogger(Protocol.class.getName());

	private Protocol() {}

	/**
	 * Erreur de protocole (message inattendu ou mal formé)
	 */
	public static class ProtocolException extends IOException {
		private static final long serialVersionUID = 1L;

		/**
		 * Construit une erreur de protocole
		 * @param message Description
		 */
		public ProtocolException(String message) {
			super(message);
		}
	}

	/**
	 * Lit la poignée de main du serveur : int 0, int n, puis n fois byte id + UTF pseudo
	 * @param in Flux d'entrée
	 * @return Joueurs annoncés
	 * @throws IOException Erreur de lecture ou message invalide
	 */
	public static List<PlayerSpec> readHandshake(DataInputStream in) throws IOException {
		int start = in.readInt();
		if (start != START) throw new ProtocolException("Code de démarrage inattendu : " + start);

		int n = in.readInt();
		if (n < 0 || n > GameProtocol.MAX_PLAYERS) throw new ProtocolException("Nombre de joueurs invalide : " + n);

		List<PlayerSpec> players = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int id = in.readByte();
			String pseudo = in.readUTF();
			players.add(new PlayerSpec(id, pseudo, false));
		}
		return players;
	}

	/**
	 * Vérifie que la liste des joueurs annoncés permet de lancer une partie
	 * @param players Joueurs annoncés
	 * @return null si la liste est valide, sinon la raison du refus
	 */
	public static String validatePlayers(List<PlayerSpec> players) {
		if (players.isEmpty()) return "aucun joueur";
		Set<Integer> ids = new HashSet<>();
		for (PlayerSpec p : players) {
			if (p.id() < 0 || p.id() >= GameProtocol.MAX_PLAYERS) return "identifiant invalide : " + p.id();
			if (!ids.add(p.id())) return "identifiant en double : " + p.id();
			String refusal = checkPseudo(p.pseudo());
			if (refusal != null) return refusal + " (joueur " + p.id() + ")";
		}
		return null;
	}

	/**
	 * Vérifie un pseudo annoncé : non vide, au plus {@value IConfig#PSEUDO_MAX_LENGTH} caractères,
	 * sans caractère de contrôle et composé de caractères réellement affichables par le jeu (Latin-1)
	 * @param pseudo Pseudo
	 * @return null si le pseudo est valide, sinon la raison du refus
	 */
	public static String checkPseudo(String pseudo) {
		if (pseudo == null || pseudo.isBlank()) return "pseudo vide";
		if (pseudo.codePointCount(0, pseudo.length()) > PSEUDO_MAX_LENGTH)
			return "pseudo de plus de " + PSEUDO_MAX_LENGTH + " caractères";
		if (pseudo.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
			return "pseudo avec des caractères de contrôle";
		if (pseudo.codePoints().anyMatch(c -> c > 0xFF))
			return "pseudo avec des caractères non affichables";
		return null;
	}

	/**
	 * Décode un bloc d'actions. Un bloc tronqué ou un type d'action inconnu arrête le décodage ;
	 * les octets ignorés sont journalisés.
	 * @param data Octets d'actions
	 * @return Commandes décodées, dans l'ordre
	 */
	public static List<Command> decodeActions(byte[] data) {
		List<Command> commands = new ArrayList<>();
		int i = 0;

		while (i + 1 < data.length) {
			int id = data[i], type = data[i + 1];

			if (type == ACTION_MOVE) {
				if (i + 3 >= data.length) break;
				commands.add(new Command.Move(id, data[i + 2], data[i + 3]));
				i += 4;
			} else if (type == ACTION_ATTACK) {
				if (i + 2 >= data.length) break;
				commands.add(new Command.Attack(id, data[i + 2]));
				i += 3;
			} else {
				break;
			}
		}

		if (i < data.length)
			LOGGER.fine(data.length - i + " octet(s) d'actions ignorés (bloc tronqué ou type inconnu)");

		return commands;
	}

	/**
	 * Code une action de déplacement
	 * @param id Identifiant du joueur
	 * @param direction Direction (0-7)
	 * @param speed Vitesse (0-4)
	 * @return Octets de l'action
	 */
	public static byte[] encodeMove(int id, int direction, int speed) {
		return new byte[] { (byte)id, ACTION_MOVE, (byte)direction, (byte)speed };
	}

	/**
	 * Code une action d'attaque
	 * @param id Identifiant du joueur
	 * @param form Forme (1 corps-à-corps, 2 tir)
	 * @return Octets de l'action
	 */
	public static byte[] encodeAttack(int id, int form) {
		return new byte[] { (byte)id, ACTION_ATTACK, (byte)form };
	}

	/**
	 * Code l'état du plateau : en-tête de 23 octets puis 9 octets par joueur
	 * @param s Image du plateau
	 * @return Octets de l'état
	 */
	public static byte[] encodeState(BoardSnapshot s) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_SIZE + PLAYER_SIZE * s.players().size());
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeByte(s.phase().getCode());
			out.writeByte(clampByte(s.alive()));
			out.writeByte(clampByte(s.total()));
			out.writeByte(s.winnerId());
			out.writeByte(MAX_LIFE_POINTS);
			writeRectangle(out, s.zone());
			writeRectangle(out, s.nextZone());
			out.writeShort(clampShort(s.secondsLeft()));

			for (PlayerState p : s.players()) {
				out.writeByte(p.id());
				out.writeByte(p.status());
				out.writeByte(clampByte(p.life()));
				out.writeShort(clampShort(Math.round(p.x())));
				out.writeShort(clampShort(Math.round(p.y())));
				out.writeByte(clampByte(p.kills()));
				out.writeByte(clampByte(p.rank()));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	/**
	 * Écrit la réponse à un bloc d'actions : int S, S octets d'état, boolean enCours
	 * @param out Flux de sortie
	 * @param s Image du plateau
	 * @throws IOException Erreur d'écriture
	 */
	public static void writeStateResponse(DataOutputStream out, BoardSnapshot s) throws IOException {
		byte[] state = encodeState(s);
		out.writeInt(state.length);
		out.write(state);
		out.writeBoolean(!s.isOver());
		out.flush();
	}

	/**
	 * Écrit un rectangle sous forme de 4 short (x1, y1, x2, y2)
	 * @param out Flux de sortie
	 * @param r Rectangle
	 * @throws IOException Erreur d'écriture
	 */
	private static void writeRectangle(DataOutputStream out, Rectangle r) throws IOException {
		out.writeShort(clampShort(Math.round(r.getX1())));
		out.writeShort(clampShort(Math.round(r.getY1())));
		out.writeShort(clampShort(Math.round(r.getX2())));
		out.writeShort(clampShort(Math.round(r.getY2())));
	}

	/**
	 * Borne une valeur dans l'intervalle d'un octet non signé
	 * @param v Valeur
	 * @return Valeur entre 0 et 255
	 */
	private static int clampByte(int v) {
		return Math.max(0, Math.min(GameProtocol.UNSIGNED_BYTE_MAX, v));
	}

	/**
	 * Borne une valeur dans l'intervalle d'un short
	 * @param v Valeur
	 * @return Valeur bornée
	 */
	private static int clampShort(int v) {
		return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
	}
}
