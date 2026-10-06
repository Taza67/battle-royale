package communication.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import protocol.GameProtocol;

/**
 * Symétrie du contrat binaire : les constantes exposées par le serveur sont celles
 * du module partagé {@code battle-royale-protocol}, et le décodage lit les champs
 * d'un octet en non signé (0-255), comme le jeu les écrit
 */
class GameProtocolTest {
	@Test
	void exposesTheSharedProtocolConstants() {
		assertEquals(GameProtocol.START, GameLink.CODE_START);
		assertEquals(GameProtocol.PAUSE, GameLink.CODE_PAUSE);
		assertEquals(GameProtocol.STOP, GameLink.CODE_STOP);
		assertEquals(GameProtocol.RESUME, GameLink.CODE_RESUME);
		assertEquals(GameProtocol.ACTION_MOVE, GameLink.ACTION_MOVE);
		assertEquals(GameProtocol.ACTION_ATTACK, GameLink.ACTION_ATTACK);
		assertEquals(GameProtocol.HEADER_SIZE, GameSnapshot.HEADER_SIZE);
		assertEquals(GameProtocol.PLAYER_SIZE, GameSnapshot.PLAYER_SIZE);
		assertEquals(GameProtocol.MAX_PLAYERS, GameSnapshot.MAX_PLAYERS);
	}

	@Test
	void decodesByteFieldsAsUnsigned() throws Exception {
		GameSnapshot snapshot = GameSnapshot.decode(SnapshotBytes.battle()
			.counts(255, 255).maxLife(255)
			.player(200, 1, 255, 300, 400, 255, 255)
			.build());
		assertEquals(255, snapshot.alive());
		assertEquals(255, snapshot.total());
		assertEquals(255, snapshot.maxLife());
		PlayerSnapshot p = snapshot.player(200);
		assertEquals(200, p.id());
		assertEquals(255, p.life());
		assertEquals(255, p.kills());
		assertEquals(255, p.rank());
	}
}
