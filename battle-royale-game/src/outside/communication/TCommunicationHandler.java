package outside.communication;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import inside.Board;
import inside.IConfig;
import outside.Game;

public class TCommunicationHandler extends Thread implements IConfig {
	private ServerSocket servSocket;
	private Socket cliSocket;
	private DataInputStream input;
	private DataOutputStream output;
	
	private Board BOARD;
	
	
	// Constructeurs
	public TCommunicationHandler(Board b) {
		BOARD = b;
	}
	
	
	// Méthodes
	// Établis la connection avec le serveur web
	public void connectToWebServer() {
		try {
			servSocket = new ServerSocket(8000);
			System.out.println("- Serveur TCP du jeu prêt !");
			
			cliSocket = servSocket.accept();
			input = new DataInputStream(cliSocket.getInputStream());
			output = new DataOutputStream(cliSocket.getOutputStream());
		} catch (Exception e) {
			System.out.println("- Impossible de démarrer le serveur !");
			disconnect();
		}
		
		System.out.println("- Connexion établie avec le serveur web !");
	}
	
	// Attend la confirmation de démarrage du jeu
	public boolean waitConfirmation() {
		try {
			input.readInt();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
		System.out.println("- Le serveur web est prêt !");
		System.out.println("  Attente de démarrage... (Oui : 'o', Non : 'n') : ");
		
		confirmationWait : while(true) {
			switch(Game.keyboard.nextLine()) {
			case "O":
			case "o":
				break confirmationWait;
			case "N":
			case "n":
				System.out.println("- Vous ne voulez pas démarrer");
				disconnect();
				return false;
			default:
				System.out.println("- Mauvaise réponse ! Réessayez... (Oui : 'o', Non : 'n') : ");
			}
		}
		
		return true;
	}
	
	// Mets fin au jeu
	private void endGame() {
		System.out.println("- Fin du jeu");
		disconnect();
	}
	
	// Déconnecte le jeu
	private void disconnect() {
		try {
			if (input != null) input.close();
			if (output != null) output.close();
			if (cliSocket != null) cliSocket.close();
			if (servSocket != null) servSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	public void run() {
		int playersNumber;
		
		// Connexion au serveur web
		connectToWebServer();
		
		// Attende de confirmation de démarrage du jeu
		waitConfirmation();
		
		try {
			// Indication au serveur web du démarrage
			output.writeBoolean(true);
			
			// Récupération des joueurs
			System.out.println("- Attente du nombre de joueurs...");
			playersNumber = input.readInt();
			System.out.println("- Il y a " + playersNumber + " joueurs");
			
			BOARD.generatePlayers(playersNumber);
			System.out.println("- Génération des joueurs terminée");
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("- Problème dans TCommunicationHandler.run()");
			System.exit(0);
		}
		
		// Démarrage du jeu
		BOARD.startGame();
		
		// Attente active de données
		int counter = 0;
		while (!Thread.currentThread().isInterrupted()) {
			try {
				// Récupération des données
				int dataSize = input.readInt();
				
				// Jeu en pause ?
				if (dataSize == -1) {
					System.err.println("- L'administrateur a mis en pause le jeu");
					continue;
				} else if (dataSize == -2) {
					System.err.println("- L'administrateur a arrêté le jeu");
					break;
				}
				
				byte[] data = input.readNBytes(dataSize);
				System.err.println("- Données récupérées");
				
				// Traitement des données
				BOARD.treatData(data);
				System.err.println("- Données traitées");
				
				// Récupération des états des joueurs
				byte[] playersData = BOARD.getPlayersStates();
				System.err.println("- États des joueurs récupérés");
				
				// Envoi des états des joueurs
				output.writeInt(playersData.length);
				output.write(playersData);
				System.err.println("- États des joueurs envoyés");
				
				// Fin du jeu
				if(BOARD.getIsDone()) {
					output.writeBoolean(false);
					System.err.println("- Indication de fin de jeu envoyée");
					System.err.println("- Confirmation de traitement envoyée (N°" + counter + ")");
					break;
				}
				
				// Indication au serveur web
				output.writeBoolean(true);
				System.err.println("- Confirmation de traitement envoyée (N°" + counter + ")");
				
				counter++;
			} catch (Exception e) {
				System.exit(0);
			}
		}
		
		System.err.println("Fin du jeu");
		
		// Fin du jeu
		endGame();
	}

}
