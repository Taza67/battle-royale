package inside;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import inside.geometry.Rectangle;

/**
 * Image immuable de l'état du plateau à la fin d'un pas de simulation.
 * Elle peut être lue sans verrou par l'affichage et par le fil réseau.
 * @param tick Pas de simulation
 * @param phase Phase de la partie
 * @param paused true si la partie est en pause
 * @param stopped true si la partie a été arrêtée par l'administrateur
 * @param alive Nombre de joueurs vivants
 * @param total Nombre total de joueurs
 * @param winnerId Identifiant du vainqueur, -1 si aucun
 * @param zone Zone sûre actuelle
 * @param nextZone Prochaine zone sûre
 * @param zoneStage Étape de la zone sûre
 * @param secondsLeft Secondes avant la prochaine étape (fin de l'échauffement, début ou fin d'un rétrécissement)
 * @param wave Numéro de la vague en cours (à partir de 1, 0 avant le combat)
 * @param waveCount Nombre de vagues
 * @param lavaDamagePerSecond Dégâts par seconde de la lave
 * @param players États des joueurs, triés par identifiant
 * @param bullets États des projectiles actifs
 * @param killFeed Dernières éliminations, de la plus ancienne à la plus récente
 * @author mourtaza
 */
public record BoardSnapshot(
	long tick, Phase phase, boolean paused, boolean stopped,
	int alive, int total, int winnerId,
	Rectangle zone, Rectangle nextZone, SafeZone.Stage zoneStage, int secondsLeft,
	int wave, int waveCount, int lavaDamagePerSecond,
	List<PlayerState> players, List<BulletState> bullets, List<KillFeedEntry> killFeed) {

	/**
	 * État d'un joueur
	 * @param id Identifiant
	 * @param pseudo Pseudo
	 * @param bot true pour un joueur contrôlé par l'ordinateur
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @param life Points de vie
	 * @param alive true si le joueur est en vie
	 * @param winner true si le joueur a gagné la partie
	 * @param kills Nombre d'éliminations
	 * @param rank Classement final (0 tant que le joueur est en vie)
	 * @param viewDirection Direction du regard
	 * @param moving true si le joueur se déplace
	 * @param swingProgress Avancement du coup d'épée (-1 si aucun)
	 * @param lastHitTick Pas des derniers dégâts subis (-1 si aucun)
	 * @param inLava true si le joueur est dans la lave
	 * @param meleeCooldown Recharge restante de l'épée, en pas
	 * @param shootCooldown Recharge restante du tir, en pas
	 * @param lastAttacker Identifiant du dernier attaquant (-1 si aucun)
	 * @param eliminationOrder Ordre d'élimination (0 tant que le joueur est en vie)
	 */
	public record PlayerState(int id, String pseudo, boolean bot, float x, float y, int life, boolean alive, boolean winner,
		int kills, int rank, int viewDirection, boolean moving, float swingProgress, long lastHitTick, boolean inLava,
		int meleeCooldown, int shootCooldown, int lastAttacker, int eliminationOrder) {

		/**
		 * Retourne le statut du joueur dans le protocole
		 * @return 0 éliminé, 1 vivant, 2 vainqueur
		 */
		public int status() {
			return winner ? 2 : alive ? 1 : 0;
		}
	}

	/**
	 * État d'un projectile
	 * @param id Identifiant
	 * @param ownerId Identifiant du tireur
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @param dx Composante horizontale de la direction
	 * @param dy Composante verticale de la direction
	 */
	public record BulletState(int id, int ownerId, float x, float y, float dx, float dy) {}

	/**
	 * Élimination affichée dans le fil des éliminations
	 * @param tick Pas de simulation de l'élimination
	 * @param killerId Identifiant de l'auteur (-1 pour la lave)
	 * @param victimId Identifiant du joueur éliminé
	 * @param cause Origine des derniers dégâts
	 */
	public record KillFeedEntry(long tick, int killerId, int victimId, DamageCause cause) {}

	/**
	 * Construit une image en copiant les listes
	 */
	public BoardSnapshot {
		players = List.copyOf(players);
		bullets = List.copyOf(bullets);
		killFeed = List.copyOf(killFeed);
	}

	/**
	 * Retourne l'état d'un joueur
	 * @param id Identifiant
	 * @return État du joueur ou null
	 */
	public PlayerState player(int id) {
		for (PlayerState p : players)
			if (p.id() == id) return p;
		return null;
	}

	/**
	 * Retourne le pseudo d'un joueur
	 * @param id Identifiant
	 * @return Pseudo, ou "?" si le joueur est inconnu
	 */
	public String pseudoOf(int id) {
		PlayerState p = player(id);
		return p == null ? "?" : p.pseudo();
	}

	/**
	 * Indique si la partie est terminée
	 * @return true si la phase est terminée
	 */
	public boolean isOver() {
		return phase == Phase.ENDED;
	}

	/**
	 * Retourne le classement : vainqueur et survivants d'abord, puis par classement final,
	 * à classement égal par nombre d'éliminations
	 * @return Liste triée des joueurs
	 */
	public List<PlayerState> ranking() {
		List<PlayerState> sorted = new ArrayList<>(players);
		sorted.sort(Comparator
			.comparingInt(PlayerState::rank)
			.thenComparing(Comparator.comparingInt(PlayerState::kills).reversed())
			.thenComparing(Comparator.comparingInt(PlayerState::life).reversed())
			.thenComparingInt(PlayerState::id));
		return sorted;
	}
}
