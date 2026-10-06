package outside.graphic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import inside.Board;
import inside.Board.PlayerSpec;
import inside.BoardSnapshot;
import inside.DamageCause;
import inside.GameEvent;
import inside.GameSettings;

class EffectsTest {
	private static final BoardSnapshot SNAPSHOT = new Board(GameSettings.defaults(3),
		List.of(new PlayerSpec(0, "Alice", false), new PlayerSpec(1, "Bob", false))).getSnapshot();

	private static GameEvent elimination(int killer, int victim, DamageCause cause) {
		return new GameEvent(GameEvent.Type.ELIMINATION, 10, killer, victim, 2, 100, 100, cause);
	}

	@Test
	void eliminationParLaLaveSansJoueurLocalSansBandeau() {
		Effects effects = new Effects();
		effects.consume(List.of(elimination(-1, 1, DamageCause.LAVA)), SNAPSHOT, -1, 0);
		assertNull(effects.getBanner(), "pas de bandeau sur le grand écran");
	}

	@Test
	void eliminationParLeJoueurLocalCelebree() {
		Effects effects = new Effects();
		effects.consume(List.of(elimination(0, 1, DamageCause.MELEE)), SNAPSHOT, 0, 0);
		assertEquals("Élimination !", effects.getBanner().title());
		assertEquals("Bob", effects.getBanner().subtitle());
	}

	@Test
	void eliminationParUnAutreJoueurSansBandeau() {
		Effects effects = new Effects();
		effects.consume(List.of(elimination(1, 0, DamageCause.BULLET)), SNAPSHOT, -1, 0);
		assertNull(effects.getBanner());
		effects.consume(List.of(elimination(-1, 1, DamageCause.LAVA)), SNAPSHOT, 0, 0);
		assertNull(effects.getBanner(), "mort dans la lave d'un autre joueur");
	}

	@Test
	void eliminationDuJoueurLocalAnnoncee() {
		Effects effects = new Effects();
		effects.consume(List.of(elimination(-1, 0, DamageCause.LAVA)), SNAPSHOT, 0, 0);
		assertEquals("Vous êtes éliminé", effects.getBanner().title());
	}
}
