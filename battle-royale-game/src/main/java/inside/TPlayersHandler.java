package inside;

import java.util.ArrayList;
import java.util.List;

public class TPlayersHandler extends Thread implements IConfig {
	private final Board BOARD;
	private final Player[] PLAYERS;
	private final List<Integer> PLAYERS_IDS;
	private final List<Bullet> BULLETS;


	// Constructeurs
	public TPlayersHandler(Board b) {
		BOARD = b;
		PLAYERS = b.getPLAYERS();
		PLAYERS_IDS = b.getPLAYERS_IDS();
		BULLETS = b.getBULLETS();
	}


	public void run() {
		synchronized (BOARD) {
			try {
				BOARD.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("- Mode dégâts démarré");
		
		do {
			boolean isChanged = false;
			
			List<Bullet> bulletsToDestroy = new ArrayList<>();
			// Déplacement des balles et dommages des balles
			for (Bullet b : BULLETS) {
				if (!b.stepAheadBullet())
					bulletsToDestroy.add(b);
				else if (b.makeContact())
					bulletsToDestroy.add(b);
			}

			isChanged = BULLETS.size() > 0;

			List<Integer> idsPlayersToKill = new ArrayList<>();

			// État des joueurs
			for (Integer id : PLAYERS_IDS) {
				// Si déjà mort (par exemple tué par un autre joueur)
				if (idsPlayersToKill.contains(id))
					continue;

				// Lave ?
				if (PLAYERS[id].isInLava())
					PLAYERS[id].reduceLifePoints(LAVA_DAMAGE);

				// Cible
				Player target = PLAYERS[id].getTarget();
				if (target != null) {
					target.reduceLifePoints(WEAPON_DAMAGE, id);

					// Si mort
					if (!target.getIsAlive())
						idsPlayersToKill.add(target.getID());
				}

				if (!PLAYERS[id].getIsAlive())
					idsPlayersToKill.add(id);

				// Le joueur rengaine son arme
				if (PLAYERS[id].getWeapon().getIsDrawn()) {
					isChanged = true;
					PLAYERS[id].holsterWeapon();
				}
			}

			// On tue les joueurs à tuer
			for (Integer id : idsPlayersToKill)
				BOARD.killPlayer(PLAYERS[id]);
			PLAYERS_IDS.removeAll(idsPlayersToKill);

			if (isChanged || idsPlayersToKill.size() > 0)
				BOARD.setIsWindowDirty(true);

			// On détruit les balles
			for (Bullet b : bulletsToDestroy)
				b.destroy();

			try {
				Thread.sleep(PLAYERS_CHECKING);
			} catch (InterruptedException e) {
				break;
			}
		} while (!isInterrupted());
	}
}
