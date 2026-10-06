package inside;

import static inside.IConfig.*;
import java.util.ArrayList;
import java.util.List;

import inside.Board.PlayerSpec;
import inside.GameSettings.ZoneWave;

/**
 * Outils de préparation des plateaux pour les tests
 */
final class Boards {
	/**
	 * Zone qui ne bouge pas pendant les tests (attente très longue)
	 */
	static final List<ZoneWave> STILL_ZONE = List.of(new ZoneWave(10_000, 10, 0.5f, 3));

	/**
	 * Échauffement qui ne finit jamais : les plateaux à un seul joueur destinés à tester
	 * les mécaniques restent en phase WARMUP (une partie solo est gagnée dès le combat)
	 */
	static final float NO_BATTLE = 3_600;

	private Boards() {}

	/**
	 * Construit un plateau sans obstacle
	 * @param players Nombre de joueurs
	 * @param warmupSeconds Durée de l'échauffement
	 * @return Plateau
	 */
	static Board empty(int players, float warmupSeconds) {
		return on(new GameMap(List.of()), players, warmupSeconds);
	}

	/**
	 * Construit un plateau sur une carte donnée
	 * @param map Carte
	 * @param players Nombre de joueurs
	 * @param warmupSeconds Durée de l'échauffement
	 * @return Plateau
	 */
	static Board on(GameMap map, int players, float warmupSeconds) {
		return new Board(new GameSettings(warmupSeconds, 42, 0, STILL_ZONE), map, specs(players));
	}

	/**
	 * Construit les joueurs 0 à n - 1
	 * @param n Nombre de joueurs
	 * @return Joueurs
	 */
	static List<PlayerSpec> specs(int n) {
		List<PlayerSpec> specs = new ArrayList<>();
		for (int i = 0; i < n; i++) specs.add(new PlayerSpec(i, "J" + i, false));
		return specs;
	}

	/**
	 * Fait avancer un plateau
	 * @param b Plateau
	 * @param ticks Nombre de pas
	 * @return Événements produits
	 */
	static List<GameEvent> run(Board b, int ticks) {
		List<GameEvent> events = new ArrayList<>();
		for (int i = 0; i < ticks; i++) {
			b.tick();
			events.addAll(b.drainEvents());
		}
		return events;
	}

	/**
	 * Fait avancer un plateau en maintenant une direction (commande renouvelée à chaque pas)
	 * @param b Plateau
	 * @param id Joueur
	 * @param direction Direction
	 * @param speed Vitesse
	 * @param ticks Nombre de pas
	 */
	static void hold(Board b, int id, int direction, int speed, int ticks) {
		for (int i = 0; i < ticks; i++) {
			b.enqueue(new Command.Move(id, direction, speed));
			b.tick();
		}
		b.drainEvents();
	}

	/**
	 * Oriente un joueur sans le déplacer
	 * @param b Plateau
	 * @param id Joueur
	 * @param direction Direction
	 */
	static void face(Board b, int id, int direction) {
		b.enqueue(new Command.Move(id, direction, 1));
		b.enqueue(new Command.Move(id, direction, 0));
		b.tick();
		b.drainEvents();
	}

	/**
	 * Compte les événements d'un type
	 * @param events Événements
	 * @param type Type
	 * @return Nombre
	 */
	static long count(List<GameEvent> events, GameEvent.Type type) {
		return events.stream().filter(e -> e.type() == type).count();
	}
}
