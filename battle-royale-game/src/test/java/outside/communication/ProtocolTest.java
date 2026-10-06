package outside.communication;

import static inside.IConfig.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import inside.Board;
import inside.Board.PlayerSpec;
import inside.BoardSnapshot;
import inside.BoardSnapshot.PlayerState;
import inside.Command;
import inside.DamageCause;
import inside.GameSettings;
import inside.GameMap;
import inside.Phase;
import inside.SafeZone;
import inside.geometry.Rectangle;

import protocol.GameProtocol;

class ProtocolTest {

	private static DataInputStream input(byte[] bytes) {
		return new DataInputStream(new ByteArrayInputStream(bytes));
	}

	private static byte[] handshake(int start, Object... players) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(start);
		out.writeInt(players.length / 2);
		for (int i = 0; i < players.length; i += 2) {
			out.writeByte((Integer)players[i]);
			out.writeUTF((String)players[i + 1]);
		}
		return bytes.toByteArray();
	}

	@Test
	void lecturePoigneeDeMain() throws IOException {
		List<PlayerSpec> players = Protocol.readHandshake(input(handshake(0, 3, "Alice", 7, "Bérénice")));
		assertEquals(List.of(new PlayerSpec(3, "Alice", false), new PlayerSpec(7, "Bérénice", false)), players);
		assertNull(Protocol.validatePlayers(players));
	}

	@Test
	void poigneeDeMainInvalide() throws IOException {
		assertThrows(Protocol.ProtocolException.class, () -> Protocol.readHandshake(input(handshake(5, 1, "A"))));
		assertNotNull(Protocol.validatePlayers(List.of()));
		assertNotNull(Protocol.validatePlayers(List.of(new PlayerSpec(1, "A", false), new PlayerSpec(1, "B", false))));
		assertNotNull(Protocol.validatePlayers(List.of(new PlayerSpec(-4, "A", false))));
		assertNotNull(Protocol.validatePlayers(List.of(new PlayerSpec(MAX_PLAYERS, "A", false))));
	}

	@Test
	void pseudosInvalidesRefuses() {
		for (String pseudo : new String[] { null, "", "   ", "\t", "a".repeat(PSEUDO_MAX_LENGTH + 1), "Ali\nce", "Bob\u0000",
				"Zo\u007Fé", "Ève\u200B", "🎮", "" })
			assertNotNull(Protocol.validatePlayers(List.of(new PlayerSpec(1, "A", false), new PlayerSpec(2, pseudo, false))),
				"pseudo « " + pseudo + " » accepté");

		assertNull(Protocol.validatePlayers(List.of(new PlayerSpec(1, "a".repeat(PSEUDO_MAX_LENGTH), false))));
		assertNull(Protocol.validatePlayers(List.of(new PlayerSpec(1, "é".repeat(PSEUDO_MAX_LENGTH), false))),
			"longueur comptée en caractères, Latin-1 affichable");
	}

	@Test
	void decodageDesActions() {
		byte[] data = { 3, 0, 1, 4, 7, 1, 2, 3, 0, 6, 0 };
		assertEquals(List.of(new Command.Move(3, 1, 4), new Command.Attack(7, 2), new Command.Move(3, 6, 0)),
			Protocol.decodeActions(data));

		byte[] encoded = new byte[7];
		System.arraycopy(Protocol.encodeMove(2, 5, 3), 0, encoded, 0, 4);
		System.arraycopy(Protocol.encodeAttack(2, 1), 0, encoded, 4, 3);
		assertEquals(List.of(new Command.Move(2, 5, 3), new Command.Attack(2, 1)), Protocol.decodeActions(encoded));

		assertEquals(List.of(new Command.Move(1, 0, 4)), Protocol.decodeActions(new byte[] { 1, 0, 0, 4, 2, 0, 1 }), "action tronquée ignorée");
		assertEquals(List.of(), Protocol.decodeActions(new byte[] { 1, 9, 0, 0 }), "type inconnu");
		assertEquals(List.of(), Protocol.decodeActions(new byte[0]));
	}

	@Test
	void dispositionDesOctetsDeLEtat() throws IOException {
		List<PlayerSpec> specs = List.of(new PlayerSpec(4, "A", false), new PlayerSpec(9, "B", false), new PlayerSpec(12, "C", false));
		Board b = new Board(new GameSettings(0, 1, 0, GameSettings.DEFAULT_WAVES), new GameMap(List.of()), specs);
		b.tick();
		b.getPlayer(12).reduceLifePoints(MAX_LIFE_POINTS, 4, DamageCause.BULLET, b.getTick());
		b.getPlayer(9).reduceLifePoints(37, 4, DamageCause.MELEE, b.getTick());
		b.tick();

		BoardSnapshot s = b.getSnapshot();
		byte[] state = Protocol.encodeState(s);
		assertEquals(Protocol.HEADER_SIZE + Protocol.PLAYER_SIZE * 3, state.length);
		assertEquals(23, Protocol.HEADER_SIZE);
		assertEquals(9, Protocol.PLAYER_SIZE);

		DataInputStream in = input(state);
		assertEquals(1, in.readByte(), "phase combat");
		assertEquals(2, in.readByte(), "vivants");
		assertEquals(3, in.readByte(), "total");
		assertEquals(-1, in.readByte(), "pas de vainqueur");
		assertEquals(MAX_LIFE_POINTS, in.readByte());
		Rectangle zone = s.zone(), next = s.nextZone();
		assertEquals(Math.round(zone.getX1()), in.readShort());
		assertEquals(Math.round(zone.getY1()), in.readShort());
		assertEquals(Math.round(zone.getX2()), in.readShort());
		assertEquals(Math.round(zone.getY2()), in.readShort());
		assertEquals(Math.round(next.getX1()), in.readShort());
		assertEquals(Math.round(next.getY1()), in.readShort());
		assertEquals(Math.round(next.getX2()), in.readShort());
		assertEquals(Math.round(next.getY2()), in.readShort());
		assertEquals(s.secondsLeft(), in.readShort());
		assertTrue(s.secondsLeft() > 0);

		int[][] expected = { { 4, 1, 100, 1, 0 }, { 9, 1, 63, 0, 0 }, { 12, 0, 0, 0, 3 } };
		for (int[] e : expected) {
			PlayerState p = s.player(e[0]);
			assertEquals(e[0], in.readByte(), "identifiant");
			assertEquals(e[1], in.readByte(), "statut");
			assertEquals(e[2], in.readByte(), "points de vie");
			assertEquals(Math.round(p.x()), in.readShort());
			assertEquals(Math.round(p.y()), in.readShort());
			assertEquals(e[3], in.readByte(), "éliminations");
			assertEquals(e[4], in.readByte(), "classement");
		}
		assertEquals(0, in.available());
	}

	@Test
	void etatFinalEtReponse() throws IOException {
		Board b = new Board(new GameSettings(0, 1, 0, GameSettings.DEFAULT_WAVES), new GameMap(List.of()),
			List.of(new PlayerSpec(0, "A", false), new PlayerSpec(1, "B", false)));
		b.tick();
		b.getPlayer(1).reduceLifePoints(MAX_LIFE_POINTS, 0, DamageCause.BULLET, b.getTick());
		b.tick();

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Protocol.writeStateResponse(new DataOutputStream(bytes), b.getSnapshot());
		DataInputStream in = input(bytes.toByteArray());
		int size = in.readInt();
		assertEquals(Protocol.HEADER_SIZE + 2 * Protocol.PLAYER_SIZE, size);
		byte[] state = new byte[size];
		in.readFully(state);
		assertFalse(in.readBoolean(), "enCours vaut false à la fin");
		assertEquals(0, in.available());

		assertEquals(2, state[0], "phase terminée");
		assertEquals(1, state[1]);
		assertEquals(0, state[3], "vainqueur");
		assertEquals(0, state[Protocol.HEADER_SIZE], "identifiant du vainqueur");
		assertEquals(2, state[Protocol.HEADER_SIZE + 1], "statut vainqueur");
		assertEquals(1, state[Protocol.HEADER_SIZE + 8], "classement du vainqueur");
		assertEquals(2, state[Protocol.HEADER_SIZE + Protocol.PLAYER_SIZE + 8], "classement du perdant");
	}

	@Test
	void constantesDuContratPartage() {
		assertEquals(GameProtocol.START, Protocol.START);
		assertEquals(GameProtocol.PAUSE, Protocol.PAUSE);
		assertEquals(GameProtocol.STOP, Protocol.STOP);
		assertEquals(GameProtocol.RESUME, Protocol.RESUME);
		assertEquals(GameProtocol.ACTION_MOVE, Protocol.ACTION_MOVE);
		assertEquals(GameProtocol.ACTION_ATTACK, Protocol.ACTION_ATTACK);
		assertEquals(GameProtocol.HEADER_SIZE, Protocol.HEADER_SIZE);
		assertEquals(GameProtocol.PLAYER_SIZE, Protocol.PLAYER_SIZE);
		assertEquals(GameProtocol.MAX_ACTIONS_SIZE, Protocol.MAX_ACTIONS_SIZE);
	}

	@Test
	void borneLesChampsDeLEtatAUnOctetNonSigne() {
		// Les champs d'un octet sont lus en non signé (0-255) par le serveur :
		// l'encodeur borne donc à 255, pas à 127
		BoardSnapshot s = new BoardSnapshot(0, Phase.BATTLE, false, false, 300, 400, -1,
			new Rectangle(0, 0, 1280, 720), new Rectangle(0, 0, 100, 100), SafeZone.Stage.WAITING, 30, 0, 0, 0,
			List.of(new PlayerState(1, "A", false, 10f, 20f, 400, true, false, 300, 0, 0, false, -1f, -1, false, 0, 0, -1, 0),
				new PlayerState(2, "B", false, 30f, 40f, -5, true, false, -3, 0, 0, false, -1f, -1, false, 0, 0, -1, 0)),
			List.of(), List.of());
		byte[] state = Protocol.encodeState(s);

		assertEquals(255, state[1] & 0xFF, "vivants bornés à 255");
		assertEquals(255, state[2] & 0xFF, "total borné à 255");
		int first = Protocol.HEADER_SIZE, second = first + Protocol.PLAYER_SIZE;
		assertEquals(255, state[first + 2] & 0xFF, "points de vie bornés à 255");
		assertEquals(255, state[first + 7] & 0xFF, "éliminations bornées à 255");
		assertEquals(0, state[second + 2], "points de vie négatifs bornés à 0");
		assertEquals(0, state[second + 7], "éliminations négatives bornées à 0");
	}

	@Test
	void echauffementEtCompteARebours() {
		Board b = new Board(GameSettings.defaults(1).withWarmup(12), List.of(new PlayerSpec(0, "A", false)));
		byte[] state = Protocol.encodeState(b.getSnapshot());
		assertEquals(0, state[0], "phase échauffement");
		assertEquals(12, ((state[21] & 0xFF) << 8) | (state[22] & 0xFF));
		assertEquals(0, state[5]);
		assertEquals(0, state[6]);
		assertEquals(1280, ((state[9] & 0xFF) << 8) | (state[10] & 0xFF));
		assertEquals(720, ((state[11] & 0xFF) << 8) | (state[12] & 0xFF));
	}
}
