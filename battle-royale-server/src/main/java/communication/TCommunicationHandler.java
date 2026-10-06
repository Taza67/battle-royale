package communication;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Classe représentant un thread réalisant la communication entre le jeu et le serveur web(socket)
 * @author mourtaza
 *
 */
public class TCommunicationHandler extends Thread {
	/**
	 * Temps d'attente entre chaque envoi de données au jeu
	 */
	private final static int TIME_BETWEEN_SENDINGS = 10;
	/**
	 * Code représentant le type d'une action
	 */
	private final static int MOVE_ACTION = 0, ATTACK_ACTION = 1;
	/**
	 * Variable contenant un tableau de données représentant les déplacements des joueurs
	 */
	private final byte[][] MOVES;
	/**
	 * Variable contenant un tableau de données représentant les attaques des joueurs
	 */
	private final byte[] ATTACKS;
	/**
	 * Variable contenant une liste des identifiants des joueurs
	 */
	private final List<Integer> PLAYERS_IDS;
	/**
	 * Variable indiquant si le jeu a démarré
	 */
	private boolean gameHasStarted = false;
	/**
	 * Socket associé à la communication TCP entre le serveur web (client) et le jeu (serveur)
	 */
	private Socket socket;
	/**
	 * Flux d'entrée associé à la communication TCP (jeu - web)
	 */
	private DataInputStream input;
	/**
	 * Flux de sortie associé à la communication TCP (jeu - web)
	 */
	private DataOutputStream output;
	/**
	 * Verrou permettant de gérer de façon la lecture/écriture dans les tableaux d'actions
	 * @see TCommunicationHandler#MOVES
	 * @see TCommunicationHandler#ATTACKS
	 */
	private final ReentrantReadWriteLock lockActions;
	/**
	 * Verrou permettant de mettre en pause le jeu
	 */
	private Object pauseLock;
	
	
	/**
	 * Construit une instance du thread de communication
	 * @param ms Tableau représentant les déplacements
	 * @param as Tableau représentant les attaques
	 * @param ps Tableau représentant les joueurs
	 * @param pids Liste réprésentant les identifiants des joueurs
	 * @param la Verrou (lecture/écriture) pour les actions
	 * @param pl Verrou pour la pause
	 * 
	 * @see TCommunicationHandler#MOVES
	 * @see TCommunicationHandler#ATTACKS
	 * @see TCommunicationHandler#PLAYERS_IDS
	 * @see TCommunicationHandler#lockActions
	 * @see TCommunicationHandler#pauseLock
	 */
	public TCommunicationHandler(byte[][] ms, byte[] as, Player[] ps, List<Integer> pids, ReentrantReadWriteLock la, Object pl){
		System.out.println("- Initialisation du gestionnaire de communication");
		MOVES = ms;
		ATTACKS = as;
		PLAYERS_IDS = pids;
		lockActions = la;
		pauseLock = pl;
	}
	
	
	/**
	 * Retourne le nombre de joueurs
	 * @return Nombre de joueurs
	 * 
	 * @see TCommunicationHandler#PLAYERS_IDS
	 */
	public int getPlayersNumber() { return PLAYERS_IDS.size(); }
	
	
	/**
	 * Établis la connexion avec le jeu
	 * 
	 * @see TCommunicationHandler#socket
	 * @see TCommunicationHandler#input
	 * @see TCommunicationHandler#output
	 */
	public void connectToGame() {
		try {
			socket = new Socket("localhost", 8000);
			input = new DataInputStream(socket.getInputStream());
			output = new DataOutputStream(socket.getOutputStream());
			
			System.err.println("- Connexion au jeu établie");
		} catch (Exception e) {
			System.err.println("- Connexion impossible au jeu");
			System.exit(-1);
		}
	}
	
	/**
	 * Demande à débuter le jeu
	 * @return true si tout se passe correctement, false sinon
	 * 
	 * @see TCommunicationHandler#output
	 * @see TCommunicationHandler#getPlayersNumber()
	 * @see TCommunicationHandler#gameHasStarted
	 */
	public boolean startGame() {
		int playersNumber;
		
		try {
			// Envoi de confirmation de démarrage
			output.writeInt(0);
			gameHasStarted = input.readBoolean();
			
			playersNumber = getPlayersNumber();
			output.writeInt(playersNumber);
		} catch(IOException e) {
			e.printStackTrace();
			System.err.println("- Problème dans TCommunicationHandler.startGame");
			return false;
		}
		return true;
	}
	
	/**
	 * Traite les données reçues depuis le jeu
	 */
	public void treatData(byte data[]) {
		int dataSize = data.length,
			it = 0;
		
		while (it < dataSize) {
			// Récupération des informations
			int playerId = data[it++];
			int isAlive = data[it++];
			int lifePoints = data[it++];
			int posX = ((data[it++] & 0xFF) << 8) | (data[it++] & 0xFF);
			int posY = ((data[it++] & 0xFF) << 8) | (data[it++] & 0xFF);

			// Conversion des informations en objet JSon
			String json = "{"
				+ "\"isAlive\": " + isAlive + ","
				+ "\"lifepoints\": " + lifePoints + ","
				+ "\"posX\": " + posX + ","
				+ "\"posY\": " + posY +
			"}";
			
			// Envoi des informations au joueur
			Admin.sendMessage(playerId, json);
		}
	}
	
	/**
	 * Méthode principale du thread
	 * Réalise l'envoi et la réception des données
	 */
	public void run() {
		int playersNumber = getPlayersNumber();
		
		// Connexion au jeu
		connectToGame();
		
		int counter = 0;
		while (!Thread.currentThread().isInterrupted()) {
			// Il s'agit d'une demande de pause
			if (Admin.getGameIsPaused()) {
				// Met en pause la communication
				try {
					// Informe le jeu de la pause
					output.writeInt(-1);
					// Se met en attente d'une notification de reprise
					synchronized (pauseLock) {
						pauseLock.wait();
						continue;
					}
				} catch (InterruptedException e) {
					System.err.println("- Arrêt à cause d'une interruption du thread");
					break;
				} catch (IOException e) {
					System.err.println("- Arrêt à cause d'une erreur de lecture/écriture 1");
					break;
				}
			}
			
			// Si le jeu n'a pas démarré
			if (!gameHasStarted) {
				if (startGame()) continue;
				else break;
			}
			
			// On envoie un tableau d'actions proportionnel
			// au nombre de joueurs
			byte actions[] = new byte[playersNumber * 8];
			
			// On convertit les actions de tous les joueurs
			// en un tableau de bytes qu'on pourra envoyer au jeu
			int it = 0;
			for (Integer id : PLAYERS_IDS) {
				// Déplacement
				lockActions.readLock().lock();
				boolean hasMoved = false;
				try {
					if (MOVES[id][0] == 1) {
						// Identifiant du joueur
						actions[it++] = id.byteValue();
						
						// Type de l'action
						actions[it++] = MOVE_ACTION;
						
						// Direction du déplacement
						actions[it++] = MOVES[id][1];
						
						// Vitesse de déplacement
						actions[it++] = MOVES[id][2];
						
						hasMoved = true;
					}
				} finally {
					lockActions.readLock().unlock();
				}
				if (hasMoved) {
					lockActions.writeLock().lock();
					try {
						// Le joueur peut demander un nouveau déplacement
						MOVES[id][0] = 0;
					} finally {
						lockActions.writeLock().unlock();
					}
				}
				
				// Attaque
				lockActions.readLock().lock();
				boolean hasAttacked = false;
				try {
					if (ATTACKS[id] != 0) {
						// Identifiant du joueur
						actions[it++] = id.byteValue();
						
						// Type de l'action
						actions[it++] = ATTACK_ACTION;
	
						// Type d'attaque
						actions[it++] = ATTACKS[id];
						
						hasAttacked = true;
					}
				} finally {
					lockActions.readLock().unlock();
				}
				if (hasAttacked) {
					lockActions.writeLock().lock();
					try {
						// Le joueur peut demander une nouvelle attaque
						ATTACKS[id] = 0;
					} finally {
						lockActions.writeLock().unlock();
					}
				}
			}
			
			if (it > 0) {
				byte[] data = new byte[it];
					
				for (int i = 0; i < it; i++)
					data[i] = actions[i];

				// Envoi des données
				try {
					output.writeInt(it);
					output.write(data);
					System.err.println("- Données envoyées (N°" + counter + ")");
					
					// États des joueurs
					int gameDataSize = input.readInt();
					byte gameData[] = new byte[gameDataSize];
					input.read(gameData);
					System.err.println("- Données du jeu récupérées");
					
					// Traitement des données des joueurs
					treatData(gameData);
					System.err.println("- Données du jeu traitées");
					
					// Si réponse négative
					if(!input.readBoolean()) {
						System.err.println("- Arrêt à la demande du jeu");
						// Envoi de l'arrêt au jeu
						Admin.sendMessageToAll("end");
						break;
					}
					
					System.err.println("- Confirmation reçue (N°" + counter + ")");
				} catch (IOException e) {
					System.err.println("- Arrêt à cause d'une erreur de lecture/écriture 2");
					break;
				}
				
				counter++;
			}
			
			// Attente entre chaque envoi de données
			try {
				Thread.sleep(TIME_BETWEEN_SENDINGS);
			} catch (InterruptedException e) {
				System.err.println("- Arrêt à cause d'une interruption du thread");
				break;
			}
		}
		
		// Fin du jeu, fermeture
		try {
			input.close();
			output.close();
			socket.close();
		} catch (IOException e) {
			return;
		}
		
		System.err.println("- Fin du jeu !");
	}
}
