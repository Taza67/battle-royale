package inside;

import static inside.Boards.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import inside.Obstacle.TypeObstacle;
import inside.geometry.Rectangle;

class CombatTest implements IConfig {

	/**
	 * Deux joueurs face à face, combat commencé
	 */
	private static Board duel(Map map, float x0, float x1) {
		Board b = map == null ? empty(2, 0) : on(map, 2, 0);
		b.tick();
		b.teleport(0, x0, 360);
		b.teleport(1, x1, 360);
		face(b, 0, EAST);
		face(b, 1, WEST);
		assertEquals(Phase.BATTLE, b.getPhase());
		return b;
	}

	@Test
	void premierCoupNeTuePas() {
		Board b = duel(null, 400, 430);
		b.enqueue(new Command.Attack(0, ATTACK_MELEE));
		run(b, MELEE_ACTIVE_TICKS + 2);

		assertEquals(MAX_LIFE_POINTS - MELEE_DAMAGE, b.getPlayer(1).getLifePoints());
		assertTrue(b.getPlayer(1).getIsAlive());
		assertEquals(0, b.getPlayer(1).getLastAttacker());
	}

	@Test
	void uneCibleTouchéeUneSeuleFoisParCoupEtRecharge() {
		Board b = duel(null, 400, 430);
		int ticks = MELEE_COOLDOWN_TICKS * 3;
		List<GameEvent> events = new java.util.ArrayList<>();
		for (int i = 0; i < ticks; i++) {
			b.enqueue(new Command.Attack(0, ATTACK_MELEE));
			b.tick();
			events.addAll(b.drainEvents());
		}

		long swings = count(events, GameEvent.Type.SWING), hits = count(events, GameEvent.Type.HIT);
		assertEquals(3, swings, "un coup toutes les " + MELEE_COOLDOWN_TICKS + " pas malgré les demandes répétées");
		assertEquals(swings, hits);
		assertEquals(MAX_LIFE_POINTS - 3 * MELEE_DAMAGE, b.getPlayer(1).getLifePoints());
	}

	@Test
	void epeeSansEffetHorsDePorteeOuDansLeDos() {
		Board far = duel(null, 400, 400 + MELEE_RANGE + 15);
		far.enqueue(new Command.Attack(0, ATTACK_MELEE));
		run(far, MELEE_ACTIVE_TICKS + 2);
		assertEquals(MAX_LIFE_POINTS, far.getPlayer(1).getLifePoints());

		Board back = duel(null, 400, 430);
		face(back, 0, WEST);
		back.enqueue(new Command.Attack(0, ATTACK_MELEE));
		run(back, MELEE_ACTIVE_TICKS + 2);
		assertEquals(MAX_LIFE_POINTS, back.getPlayer(1).getLifePoints());
	}

	@Test
	void tirAvecVitesseEtRecharge() {
		Board b = duel(null, 200, 400);
		b.enqueue(new Command.Attack(0, ATTACK_SHOOT));
		b.enqueue(new Command.Attack(0, ATTACK_SHOOT));
		b.tick();
		assertEquals(1, b.getBullets().size(), "la recharge empêche un deuxième tir immédiat");

		float travel = 200 - 2 * PLAYER_RADIUS_X;
		int ticksToHit = (int)Math.ceil(travel / (BULLET_SPEED / TICKS_PER_SECOND));
		run(b, ticksToHit - 3);
		assertEquals(MAX_LIFE_POINTS, b.getPlayer(1).getLifePoints(), "le projectile n'est pas instantané");
		run(b, 6);
		assertEquals(MAX_LIFE_POINTS - BULLET_DAMAGE, b.getPlayer(1).getLifePoints());
		assertTrue(b.getBullets().isEmpty());

		run(b, SHOOT_COOLDOWN_TICKS);
		b.enqueue(new Command.Attack(0, ATTACK_SHOOT));
		b.tick();
		assertEquals(1, b.getBullets().size(), "nouveau tir après la recharge");
	}

	@Test
	void tireurImmuniseContreSonProjectile() {
		Board b = duel(null, 200, 700);
		b.enqueue(new Command.Attack(0, ATTACK_SHOOT));
		b.tick();
		Bullet bullet = b.getBullets().get(0);
		// Le tireur se place devant son propre projectile
		b.teleport(0, bullet.getX() + 30, 360);
		run(b, 30);
		assertEquals(MAX_LIFE_POINTS, b.getPlayer(0).getLifePoints());
	}

	@Test
	void porteeLimitee() {
		Board b = duel(null, 200, 200 + BULLET_RANGE + 60);
		b.enqueue(new Command.Attack(0, ATTACK_SHOOT));
		run(b, 3 * TICKS_PER_SECOND);
		assertEquals(MAX_LIFE_POINTS, b.getPlayer(1).getLifePoints());
		assertTrue(b.getBullets().isEmpty());
	}

	@Test
	void rochersEtForetsArretentLesProjectilesMaisPasLEau() {
		Rectangle between = new Rectangle(290, 300, 310, 420);
		for (TypeObstacle t : TypeObstacle.values()) {
			Board b = duel(new Map(List.of(new Obstacle(t, between))), 200, 400);
			b.enqueue(new Command.Attack(0, ATTACK_SHOOT));
			List<GameEvent> events = run(b, TICKS_PER_SECOND);

			int expected = t.blocksBullets() ? MAX_LIFE_POINTS : MAX_LIFE_POINTS - BULLET_DAMAGE;
			assertEquals(expected, b.getPlayer(1).getLifePoints(), t.name());
			assertEquals(t.blocksBullets() ? 1 : 0, count(events, GameEvent.Type.BULLET_BLOCKED), t.name());
		}
		assertFalse(TypeObstacle.EAU.blocksBullets());
		assertTrue(TypeObstacle.ROCHER.blocksBullets());
		assertTrue(TypeObstacle.FORET.blocksBullets());
	}

	@Test
	void bordDeCarteArreteLesProjectiles() {
		Board b = duel(null, MAP_WIDTH - 40, 300);
		b.enqueue(new Command.Attack(0, ATTACK_SHOOT));
		List<GameEvent> events = run(b, 20);
		assertTrue(b.getBullets().isEmpty());
		assertEquals(1, count(events, GameEvent.Type.BULLET_BLOCKED));
	}

	@Test
	void echauffementSansDegats() {
		Board b = empty(2, 5);
		b.teleport(0, 400, 360);
		b.teleport(1, 430, 360);
		face(b, 0, EAST);
		b.enqueue(new Command.Attack(0, ATTACK_MELEE));
		b.enqueue(new Command.Attack(1, ATTACK_SHOOT));
		run(b, TICKS_PER_SECOND);

		assertEquals(Phase.WARMUP, b.getPhase());
		assertEquals(MAX_LIFE_POINTS, b.getPlayer(0).getLifePoints());
		assertEquals(MAX_LIFE_POINTS, b.getPlayer(1).getLifePoints());

		run(b, 5 * TICKS_PER_SECOND);
		assertEquals(Phase.BATTLE, b.getPhase());
	}

	@Test
	void eliminationCompteLesEliminationsEtLeClassement() {
		Board b = duel(null, 200, 300);
		for (int i = 0; i < 40 && !b.isOver(); i++) {
			b.enqueue(new Command.Attack(0, ATTACK_SHOOT));
			run(b, SHOOT_COOLDOWN_TICKS);
		}

		assertTrue(b.isOver());
		Player loser = b.getPlayer(1), winner = b.getPlayer(0);
		assertFalse(loser.getIsAlive());
		assertEquals(0, loser.getLifePoints());
		assertEquals(2, loser.getRank());
		assertEquals(1, loser.getEliminationOrder());
		assertEquals(1, winner.getKills());
		assertEquals(1, winner.getRank());
		assertEquals(0, b.getWinnerId());

		BoardSnapshot s = b.getSnapshot();
		assertEquals(Phase.ENDED, s.phase());
		assertEquals(2, s.player(0).status());
		assertEquals(0, s.player(1).status());
		assertEquals(1, s.killFeed().size());
		assertEquals(DamageCause.BULLET, s.killFeed().get(0).cause());
	}

	@Test
	void eliminationsSimultaneesPartagentLeClassement() {
		Board b = empty(3, 0);
		b.tick();
		b.getPlayer(1).reduceLifePoints(MAX_LIFE_POINTS, 0, DamageCause.BULLET, b.getTick());
		b.getPlayer(2).reduceLifePoints(MAX_LIFE_POINTS, 0, DamageCause.MELEE, b.getTick());
		b.tick();

		assertTrue(b.isOver());
		assertEquals(0, b.getWinnerId());
		assertEquals(2, b.getPlayer(1).getRank());
		assertEquals(2, b.getPlayer(2).getRank());
		assertEquals(2, b.getPlayer(0).getKills());
	}

	@Test
	void mortsSimultaneesDesDerniersJoueursDonnentUneEgalite() {
		Board b = empty(2, 0);
		b.tick();
		b.getPlayer(0).reduceLifePoints(MAX_LIFE_POINTS, 1, DamageCause.BULLET, b.getTick());
		b.getPlayer(1).reduceLifePoints(MAX_LIFE_POINTS, 0, DamageCause.BULLET, b.getTick());
		b.tick();

		assertTrue(b.isOver());
		assertEquals(-1, b.getWinnerId());
		assertEquals(1, b.getPlayer(0).getRank());
		assertEquals(1, b.getPlayer(1).getRank());
		assertEquals(0, b.getSnapshot().alive());
	}

	@Test
	void unJoueurEliminePeutPlusAgirNiEtreTouche() {
		Board b = empty(3, 0);
		b.tick();
		b.getPlayer(2).reduceLifePoints(MAX_LIFE_POINTS, 0, DamageCause.BULLET, b.getTick());
		b.tick();
		assertFalse(b.getPlayer(2).getIsAlive());

		float x = b.getPlayer(2).getX();
		b.enqueue(new Command.Move(2, EAST, 4));
		b.enqueue(new Command.Attack(2, ATTACK_SHOOT));
		run(b, 10);
		assertEquals(x, b.getPlayer(2).getX());
		assertTrue(b.getBullets().isEmpty());
		assertEquals(0, b.getPlayer(2).reduceLifePoints(10, 1, DamageCause.BULLET, b.getTick()));
	}
}
