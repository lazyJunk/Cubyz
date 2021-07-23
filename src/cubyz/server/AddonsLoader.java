package cubyz.server;

import java.io.File;

import cubyz.utils.datastructures.DataOrientedRegistry;
import cubyz.utils.json.JsonObject;
import cubyz.utils.json.JsonParser;
import cubyz.world.Registries;
import cubyz.world.terrain.Biome;

public class AddonsLoader {
	public static void loadBlocks(String directory) {
		File dir = new File(directory).getAbsoluteFile();
		if(!dir.exists()) dir.mkdirs();
		File[] addons = dir.listFiles();
		for(File addon : addons) {
			if(!addon.isDirectory()) continue;
			String addonName = addon.getName();
			File blocksFolder = new File(addon.getAbsolutePath()+"/blocks");
			if(!blocksFolder.exists()) blocksFolder.mkdirs();
			File[] blocks = blocksFolder.listFiles();
			for(File block : blocks) {
				if(block.isFile() && block.getName().endsWith(".json")) {
					String blockName = block.getName().replace(".json", "");
					JsonObject blockData = JsonParser.parseObjectFromFile(block.getAbsolutePath());
					for(DataOrientedRegistry registry : Registries.BLOCK_REGISTRIES.toArray(new DataOrientedRegistry[0])) {
						registry.register(addonName+":"+blockName, blockData);
					}
				}
			}
		}
	}
	public static void loadBiomes(String directory) {
		File dir = new File(directory).getAbsoluteFile();
		if(!dir.exists()) dir.mkdirs();
		File[] addons = dir.listFiles();
		for(File addon : addons) {
			if(!addon.isDirectory()) continue;
			String addonName = addon.getName();
			File biomesFolder = new File(addon.getAbsolutePath()+"/biomes");
			if(!biomesFolder.exists()) biomesFolder.mkdirs();
			File[] biomes = biomesFolder.listFiles();
			for(File biome : biomes) {
				if(biome.isFile()) {
					Registries.BIOMES.add(new Biome(addonName+":"+biome.getName(), biome));
				}
			}
		}
	}
}
