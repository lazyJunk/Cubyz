package io.cubyz.base;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import io.cubyz.api.EventHandler;
import io.cubyz.api.Mod;
import io.cubyz.api.Proxy;
import io.cubyz.api.Registry;
import io.cubyz.api.Resource;
import io.cubyz.blocks.Block;
import io.cubyz.blocks.Block.BlockClass;
import io.cubyz.items.Item;
import io.cubyz.items.ItemBlock;

/**
 * Mod used to support add-ons: simple mods without any sort of coding required
 */
@Mod(id = "addons-loader", name = "Addons Loader")
public class AddonsMod {
	
	@Proxy(clientProxy = "io.cubyz.base.AddonsClientProxy", serverProxy = "io.cubyz.base.AddonsCommonProxy")
	private AddonsCommonProxy proxy;
	
	public ArrayList<File> addons = new ArrayList<>();
	private ArrayList<Item> items = new ArrayList<>();
	
	@EventHandler(type = "init")
	public void init() {
		proxy.init(this);
	}
	
	@EventHandler(type = "preInit")
	public void preInit() {
		File dir = new File("addons");
		if (!dir.exists()) {
			dir.mkdir();
		}
		for (File addonDir : dir.listFiles()) {
			if (addonDir.isDirectory()) {
				addons.add(addonDir);
			}
		}
	}
	
	@EventHandler(type = "item/register")
	public void registerItems(Registry<Item> registry) {
		registry.registerAll(items);
	}
	
	@EventHandler(type = "block/register")
	public void registerBlocks(Registry<Block> registry) {
		for (File addon : addons) {
			File blocks = new File(addon, "blocks");
			if (blocks.exists()) {
				for (File descriptor : blocks.listFiles()) {
					Properties props = new Properties();
					try {
						FileReader reader = new FileReader(descriptor);
						props.load(reader);
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					Block block = new Block();
					String id = descriptor.getName();
					id = id.substring(0, id.indexOf('.'));
					block.setID(new Resource(addon.getName(), id));
					block.setHardness(Float.parseFloat(props.getProperty("hardness", "1")));
					block.setBlockClass(BlockClass.valueOf(props.getProperty("class", "STONE").toUpperCase()));
					block.setLight(Integer.parseUnsignedInt(props.getProperty("emittedLight", "0")));
					block.setAbsorption(Integer.parseUnsignedInt(props.getProperty("absorbedLight", "0")));
					block.setTransparent(props.getProperty("transparent", "no").equalsIgnoreCase("yes"));
					block.setSolid(props.getProperty("solid", "yes").equalsIgnoreCase("yes"));
					ItemBlock itemBlock = new ItemBlock(block);
					block.setBlockDrop(itemBlock);
					items.add(itemBlock);
					registry.register(block);
				}
			}
		}
	}
	
}
