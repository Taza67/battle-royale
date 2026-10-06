package communication.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class GameSnapshotTest {
	@Test
	void decodesHeaderByteByByte() throws Exception {
		byte[] data = {
			1,                      // phase : combat
			5,                      // vivants
			8,                      // total
			-1,                     // aucun vainqueur
			100,                    // vie maximum
			0, 100, 0, 50, 4, 76, 2, -118,   // zone 100, 50, 1100, 650
			1, 44, 0, 120, 3, -124, 2, 48,   // zone suivante 300, 120, 900, 560
			0, 12                   // secondes
		};
		GameSnapshot snapshot = GameSnapshot.decode(data);

		assertEquals(GameSnapshot.Phase.BATTLE, snapshot.phase());
		assertEquals(5, snapshot.alive());
		assertEquals(8, snapshot.total());
		assertEquals(-1, snapshot.winnerId());
		assertEquals(100, snapshot.maxLife());
		assertEquals(new Zone(100, 50, 1100, 650), snapshot.zone());
		assertEquals(new Zone(300, 120, 900, 560), snapshot.nextZone());
		assertEquals(12, snapshot.secondsLeft());
		assertEquals(List.of(), snapshot.players());
	}

	@Test
	void decodesPlayers() throws Exception {
		byte[] data = SnapshotBytes.battle().phase(2).counts(1, 3).winner(4)
			.player(4, 2, 87, 640, 360, 2, 1)
			.player(0, 0, 0, 1279, 719, 0, 3)
			.player(9, 0, 0, 0, 0, 255, 2)
			.build();
		assertEquals(GameSnapshot.HEADER_SIZE + 3 * GameSnapshot.PLAYER_SIZE, data.length);

		GameSnapshot snapshot = GameSnapshot.decode(data);
		assertEquals(GameSnapshot.Phase.OVER, snapshot.phase());
		assertEquals(4, snapshot.winnerId());
		assertEquals(List.of(
			new PlayerSnapshot(4, PlayerSnapshot.Status.WINNER, 87, 640, 360, 2, 1),
			new PlayerSnapshot(0, PlayerSnapshot.Status.ELIMINATED, 0, 1279, 719, 0, 3),
			new PlayerSnapshot(9, PlayerSnapshot.Status.ELIMINATED, 0, 0, 0, 255, 2)), snapshot.players());
		assertEquals(87, snapshot.player(4).life());
		assertNull(snapshot.player(5));
	}

	@Test
	void keepsSignedCoordinates() throws Exception {
		GameSnapshot snapshot = GameSnapshot.decode(SnapshotBytes.battle().player(1, 1, 50, -12, -3, 0, 0).build());
		assertEquals(-12, snapshot.player(1).x());
		assertEquals(-3, snapshot.player(1).y());
	}

	@Test
	void rejectsInconsistentSizes() {
		byte[] valid = SnapshotBytes.battle().player(1, 1, 50, 10, 10, 0, 0).build();
		assertThrows(ProtocolException.class, () -> GameSnapshot.decode(new byte[0]));
		assertThrows(ProtocolException.class, () -> GameSnapshot.decode(new byte[GameSnapshot.HEADER_SIZE - 1]));
		assertThrows(ProtocolException.class, () -> GameSnapshot.decode(Arrays.copyOf(valid, valid.length - 1)));
		assertThrows(ProtocolException.class, () -> GameSnapshot.decode(Arrays.copyOf(valid, valid.length + 4)));
		assertThrows(ProtocolException.class, () -> GameSnapshot.decode(new byte[GameSnapshot.MAX_SIZE + GameSnapshot.PLAYER_SIZE]));
	}

	@Test
	void rejectsUnknownCodes() {
		assertThrows(ProtocolException.class, () -> GameSnapshot.decode(SnapshotBytes.battle().phase(3).build()));
		assertThrows(ProtocolException.class,
			() -> GameSnapshot.decode(SnapshotBytes.battle().player(1, 3, 50, 10, 10, 0, 0).build()));
	}
}
