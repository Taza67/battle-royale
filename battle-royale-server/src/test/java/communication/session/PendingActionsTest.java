package communication.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PendingActionsTest {
	private static final byte[] NOTHING = new byte[0];

	@Test
	void sendsNothingWithoutIntent() {
		PendingActions actions = new PendingActions(List.of(0, 1));
		assertArrayEquals(NOTHING, actions.drainActions(0));
		assertArrayEquals(NOTHING, actions.drainActions(1000));
	}

	@Test
	void sendsMoveIntentsWhenTheyChangeAndRefreshesThem() {
		PendingActions actions = new PendingActions(List.of(0, 1));
		actions.move(1, 6, 3);
		assertArrayEquals(new byte[] { 1, 0, 6, 3 }, actions.drainActions(1000));
		assertArrayEquals(NOTHING, actions.drainActions(1050));
		assertArrayEquals(NOTHING, actions.drainActions(1100));
		assertArrayEquals(new byte[] { 1, 0, 6, 3 }, actions.drainActions(1000 + PendingActions.MOVE_REFRESH_MILLIS));

		actions.move(1, 6, 3);
		assertArrayEquals(NOTHING, actions.drainActions(1200));
		actions.move(1, 7, 3);
		assertArrayEquals(new byte[] { 1, 0, 7, 3 }, actions.drainActions(1250));
	}

	@Test
	void sendsExplicitStopOnceAndNeverRefreshesIt() {
		PendingActions actions = new PendingActions(List.of(0));
		actions.move(0, 2, 4);
		actions.drainActions(0);
		actions.move(0, 5, 0);
		assertArrayEquals(new byte[] { 0, 0, 0, 0 }, actions.drainActions(50));
		assertArrayEquals(NOTHING, actions.drainActions(10_000));
	}

	@Test
	void keepsOnlyTheLatestIntentBetweenExchanges() {
		PendingActions actions = new PendingActions(List.of(0));
		actions.move(0, 1, 1);
		actions.move(0, 2, 2);
		actions.move(0, 3, 4);
		assertArrayEquals(new byte[] { 0, 0, 3, 4 }, actions.drainActions(0));
	}

	@Test
	void queuesAttacksUpToALimit() {
		PendingActions actions = new PendingActions(List.of(0, 4));
		for (int i = 0; i < PendingActions.MAX_QUEUED_ATTACKS; i++)
			assertTrue(actions.attack(4, 1 + i % 2));
		assertFalse(actions.attack(4, 1));
		actions.move(0, 0, 1);

		assertArrayEquals(new byte[] { 0, 0, 0, 1, 4, 1, 1, 4, 1, 2, 4, 1, 1, 4, 1, 2 }, actions.drainActions(0));
		assertArrayEquals(NOTHING, actions.drainActions(10));
	}

	@Test
	void ignoresPlayersOutsideTheRound() {
		PendingActions actions = new PendingActions(List.of(0));
		assertFalse(actions.move(7, 1, 1));
		assertFalse(actions.attack(7, 1));
		assertArrayEquals(NOTHING, actions.drainActions(0));
	}

	@Test
	void clearStopsEveryoneAndDropsAttacks() {
		PendingActions actions = new PendingActions(List.of(0, 1));
		actions.move(0, 1, 4);
		actions.move(1, 2, 4);
		actions.drainActions(0);
		actions.attack(0, 2);
		actions.clear();
		assertArrayEquals(new byte[] { 0, 0, 0, 0, 1, 0, 0, 0 }, actions.drainActions(10));
	}
}
