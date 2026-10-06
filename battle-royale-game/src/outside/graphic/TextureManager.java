package outside.graphic;

import java.util.ArrayList;
import java.util.List;

import inside.IConfig;

public class TextureManager implements IConfig {
	private final static String TEXTURE_ATLAS_PATH = "textures/tileset.png";
	private final static String TEXTURE_PLAYERS_ATLAS_PATH = "textures/emojisset.png";
	private final TextureAtlas TEXTURE_ATLAS;
	private final TextureAtlas TEXTURE_PLAYERS_ATLAS;
	private final List<SubTexture> SUBTEXTURES;
	
	// Constructeurs
	public TextureManager() {
		TEXTURE_ATLAS = new TextureAtlas(TEXTURE_ATLAS_PATH);
		TEXTURE_PLAYERS_ATLAS = new TextureAtlas(TEXTURE_PLAYERS_ATLAS_PATH);
		SUBTEXTURES = new ArrayList<SubTexture>();
		
		// Sous-textures
		SubTexture subTexture0 = new SubTexture(TEXTURE_ATLAS, 82, 120, TREE_WIDTH, TREE_HEIGHT);			// Forêt
		SubTexture subTexture1 = new SubTexture(TEXTURE_ATLAS, 92, 67, STONE_WIDTH, STONE_HEIGHT);			// Rocher
		SubTexture subTexture2 = new SubTexture(TEXTURE_ATLAS, 394, 445, WATER_WIDTH, WATER_HEIGHT);		    // Eau
		SubTexture subTexture3 = new SubTexture(TEXTURE_ATLAS, 0, 0, 10, 10);			// Champ de bataille
		
		SUBTEXTURES.add(subTexture0);
		SUBTEXTURES.add(subTexture1);
		SUBTEXTURES.add(subTexture2);
		SUBTEXTURES.add(subTexture3);
		
		SubTexture subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 2, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 88, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 170, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 255, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 333, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 421, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 506, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 585, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 670, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 755, 3, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 835, 3, 61, 61);
		
		int y = 98;
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 2, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 88, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 170, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 255, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 333, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 421, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 506, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 585, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 670, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 755, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 835, y, 61, 61);
		
		y = 195;
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 2, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 88, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 170, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 255, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 333, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 421, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 506, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 585, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 670, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 755, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 835, y, 61, 61);
		
		y = 290;
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 2, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 88, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 170, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 255, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 333, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 421, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 506, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 585, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 670, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 755, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 835, y, 61, 61);
		
		y = 385;
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 2, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 88, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 170, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 255, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 333, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 421, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 506, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 585, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 670, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 755, y, 61, 61);
		SUBTEXTURES.add(subTexture);
		subTexture = new SubTexture(TEXTURE_PLAYERS_ATLAS, 835, y, 61, 61);
	}
	
	
	// Accesseurs
	public TextureAtlas getTEXTURE_ATLAS() { return TEXTURE_ATLAS; }
	public List<SubTexture> getSUBTEXTURES() { return SUBTEXTURES; }
	public SubTexture getSubTexture(int id) { return SUBTEXTURES.get(id); }
}
