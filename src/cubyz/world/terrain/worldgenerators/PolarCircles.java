package cubyz.world.terrain.worldgenerators;

import cubyz.api.CurrentWorldRegistries;
import cubyz.api.Resource;
import cubyz.utils.json.JsonObject;
import cubyz.world.terrain.BiomePoint;
import cubyz.world.terrain.ClimateMapFragment;
import cubyz.world.terrain.ClimateMapGenerator;
import cubyz.world.terrain.MapFragment;
import cubyz.world.terrain.biomes.Biome;
import cubyz.world.terrain.noise.FractalNoise;

import static cubyz.world.terrain.ClimateMapFragment.*;

import java.util.Random;

/**
 * Generates the climate map using a fluidynamics simulation, with a circular heat distribution.
 */

public class PolarCircles implements ClimateMapGenerator {
	/**Constants that define how the climate map is generated. TODO: Change these depending on the world.*/
	public static final float OCEAN_THRESHOLD = 0.5f,
	                          MOUNTAIN_RATIO = 0.8f,
	                          MOUNTAIN_POWER = 3f,
	                          ICE_POINT = -0.5f,
	                          FROST_POINT = -0.35f,
	                          HOT_POINT = 0.5f,
	                          DRY_POINT = 0.35f,
	                          WET_POINT = 0.65f;
	
	static final float RING_SIZE = 64;
	static final float WIND_SPEED = 1;
	static final float WIND_INFLUENCE = 0.1f;
	
	@Override
	public void init(JsonObject parameters, CurrentWorldRegistries registries) {
		
	}

	@Override
	public Resource getRegistryID() {
		return new Resource("cubyz:polar_circles");
	}

	@Override
	public void generateMapFragment(ClimateMapFragment map) {
		// Create the surrounding height and wind maps needed for wind propagation:
		float[][] heightMap = new float[3 * MAP_SIZE / MapFragment.BIOME_SIZE][3 * MAP_SIZE / MapFragment.BIOME_SIZE];
		FractalNoise.generateSparseFractalTerrain(map.wx - MAP_SIZE, map.wz - MAP_SIZE, 3 * MAP_SIZE, 3 * MAP_SIZE,
				MAP_SIZE / 16, map.world.getSeed() ^ 92786504683290654L, heightMap, MapFragment.BIOME_SIZE);
		
		float[][] windXMap = new float[3 * MAP_SIZE / MapFragment.BIOME_SIZE][3 * MAP_SIZE / MapFragment.BIOME_SIZE];
		FractalNoise.generateSparseFractalTerrain(map.wx - MAP_SIZE, map.wz - MAP_SIZE, 3 * MAP_SIZE, 3 * MAP_SIZE,
				MAP_SIZE / 8, map.world.getSeed() ^ 4382905640235972L, windXMap, MapFragment.BIOME_SIZE);
		
		float[][] windZMap = new float[3 * MAP_SIZE / MapFragment.BIOME_SIZE][3 * MAP_SIZE / MapFragment.BIOME_SIZE];
		FractalNoise.generateSparseFractalTerrain(map.wx - MAP_SIZE, map.wz - MAP_SIZE, 3 * MAP_SIZE, 3 * MAP_SIZE,
				MAP_SIZE / 8, map.world.getSeed() ^ 532985472894530L, windZMap, MapFragment.BIOME_SIZE);
		
		// Make non-ocean regions more flat:
		for (int x = 0; x < heightMap.length; x++) {
			for (int y = 0; y < heightMap[0].length; y++) {
				if (heightMap[x][y] >= OCEAN_THRESHOLD)
					heightMap[x][y] = (float) (OCEAN_THRESHOLD + (1 - OCEAN_THRESHOLD) * Math.pow((heightMap[x][y] - OCEAN_THRESHOLD) / (1 - OCEAN_THRESHOLD), MOUNTAIN_POWER));
			}
		}

		// Calculate the temperature and humidty for each point on the map. This is done by backtracing along the wind.
		// On mountains the water will often rain down, so wind that goes through a mountain will carry less.
		// Oceans carry water, so if the wind went through an ocean it picks up water.
		
		// Alongside that there is also an initial temperature and humidity distribution that mimics the earth.
		// How is that possible? Isn't Cubyz flat?
		// On the earth there are just two arctic poles. Cubyz takes the north pole and places it at (0, 0).
		// Then there are infinite poles with ring shapes and each ring will have an equal distance to the previous one.
		// That's not perfectly realistic, but it's ok in the sense that following a compass will lead to one arctic
		// and away from another.

		Random rand = new Random();
		
		for (int x = 0; x < map.map.length; x++) {
			for (int z = 0; z < map.map.length; z++) {
				float humid = getInitialHumidity(map, x, z, heightMap[x + map.map.length][z + map.map.length]);
				float temp = getInitialTemperature(map, x, z, heightMap[x + map.map.length][z + map.map.length]);
				float humidInfluence = WIND_INFLUENCE;
				float tempInfluence = WIND_INFLUENCE;
				float nextX = x;
				float nextZ = z;
				for (int i = 0; i < 50; i++) {
					float windX = windXMap[(int) nextX + map.map.length][(int) nextZ + map.map.length];
					float windZ = windXMap[(int) nextX + map.map.length][(int) nextZ + map.map.length];
					nextX += windX*WIND_SPEED;
					nextZ += windZ*WIND_SPEED;
					// Make sure the bounds are ok:
					if (nextX < -map.map.length || nextX > 2*map.map.length - 1) {
						break;
					}
					if (nextZ < -map.map.length || nextZ > 2*map.map.length - 1) {
						break;
					}
					// Find the local temperature and humidity:
					float localHeight = heightMap[(int) nextX + map.map.length][(int) nextZ + map.map.length];
					float localTemp = getInitialTemperature(map, nextX, nextZ, localHeight);
					float localHumid = getInitialHumidity(map, nextX, nextZ, localHeight);
					humid = (1 - humidInfluence) * humid + humidInfluence * localHumid;
					temp = (1 - tempInfluence) * temp + tempInfluence * localTemp;
					tempInfluence *= 0.9f; // Distance reduction
					humidInfluence *= 0.9f; // Distance reduction
					// Reduction from mountains:
					humidInfluence *= Math.pow(1 - heightMap[(int) nextX + map.map.length][(int) nextZ + map.map.length], 0.05);
				}
				// Insert the biome type:
				rand.setSeed((x + map.wx)*65784967549L + (z + map.wz)*6758934659L + map.world.getSeed());
				int wx = x*MapFragment.BIOME_SIZE + map.wx;
				int wz = z*MapFragment.BIOME_SIZE + map.wz;
				Biome.Type type = findClimate(heightMap[x + map.map.length][z + map.map.length], humid, temp);
				Biome biome = map.world.getCurrentRegistries().biomeRegistry.byTypeBiomes.get(type).getRandomly(rand);
				map.map[x][z] = new BiomePoint(biome, wx + rand.nextInt(MapFragment.BIOME_SIZE) - MapFragment.BIOME_SIZE/2,
				                                      wz + rand.nextInt(MapFragment.BIOME_SIZE) - MapFragment.BIOME_SIZE/2,
				                                      rand.nextFloat()*(biome.maxHeight - biome.minHeight) + biome.minHeight, rand.nextLong());
			}
		}
	}
	
	private float getInitialHumidity(ClimateMapFragment map, double x, double z, float height) {
		if (height < OCEAN_THRESHOLD) return 1;
		x = x + (map.wx >> MapFragment.BIOME_SHIFT);
		z = z + (map.wz >> MapFragment.BIOME_SHIFT);
		double distance = Math.sqrt(x*x + z*z)/RING_SIZE;
		distance %= 1;
		// On earth there is high humidity at the equator and the poles and low humidty at around 30°.
		// Interpolating through this data resulted in this function:
		// 1 - 2916/125*x² - 16038/125*x⁴ + 268272/125*x⁶ - 629856/125*x⁸
		
		if (distance >= 0.5f) distance -= 1;
		// Now calculate the function:
		x = distance*distance;
		x = 1 - 2916.0f/125.0f*x - 16038.0f/125.0f*x*x + 268272.0f/125.0f*x*x*x - 629856.0f/125.0f*x*x*x*x;
		// Put the output in scale:
		return (float) (x*0.5f + 0.5f);
	}
	
	private float getInitialTemperature(ClimateMapFragment map, double x, double z, float height) {
		x = x + (map.wx >> MapFragment.BIOME_SHIFT);
		z = z + (map.wz >> MapFragment.BIOME_SHIFT);
		double temp = Math.sqrt(x*x + z*z)/RING_SIZE%1;
		// Uses a simple triangle function:
		if (temp > 0.5f) {
			temp = 1 - temp;
		}
		temp = 4*temp - 1;
		return heightDependantTemperature((float) temp, height);
	}
	
	/**
	 * Adds height dependent temperature changes.
	 * @param temperature
	 * @param height
	 * @return
	 */
	private static float heightDependantTemperature(float temperature, float height) {
		// On earth temperature changes by `6.5K/km`.
		// On a cubyz world the highest mountain will be `(1 - OCEAN_THRESHOLD)` units high.
		// If the highest possible height of a mountain is assumed to be 10km, the temperature change gets: `65K/(1 - OCEAN_THRESHOLD)*(height - OCEAN_THRESHOLD)`
		// Annual average temperature on earth range between -50°C and +30°C giving a difference of 80K
		// On cubyz average temperature range from -1 to 1 giving a difference of 2.
		// Therefor the total equation gets: `65K*2/80K/(1 - OCEAN_THRESHOLD)*(height - OCEAN_THRESHOLD)` = `1.625/(1 - OCEAN_THRESHOLD)*(height - OCEAN_THRESHOLD)`
		
		// Furthermore I assume that the average temperature is at 1km of height.
		return temperature - 1.625f*(Math.max(0, height - OCEAN_THRESHOLD)/(1 - OCEAN_THRESHOLD) - 0.1f);
	}
	
	private static Biome.Type findClimate(float height, float humid, float temp) {
		if (height < OCEAN_THRESHOLD) {
			if (temp <= FROST_POINT) {
				return Biome.Type.ARCTIC_OCEAN;
			} else if (temp < HOT_POINT) {
				return Biome.Type.OCEAN;
			} else {
				return Biome.Type.WARM_OCEAN;
			}
		} else if (height < (1.0f - OCEAN_THRESHOLD)*(1 - MOUNTAIN_RATIO) + OCEAN_THRESHOLD) {
			if (temp <= FROST_POINT) {
				if (temp <= ICE_POINT) {
					return Biome.Type.GLACIER;
				} else if (humid < WET_POINT) {
					return Biome.Type.TAIGA;
				} else {
					return Biome.Type.TUNDRA;
				}
			} else if (temp < HOT_POINT) {
				if (humid <= DRY_POINT) {
					return Biome.Type.GRASSLAND;
				} else if (humid < WET_POINT) {
					return Biome.Type.FOREST;
				} else {
					return Biome.Type.SWAMP;
				}
			} else {
				if (humid <= DRY_POINT) {
					return Biome.Type.DESERT;
				} else if (humid < WET_POINT) {
					return Biome.Type.SHRUBLAND;
				} else {
					return Biome.Type.RAINFOREST;
				}
			}
		} else {
			if (temp <= FROST_POINT) {
				return Biome.Type.PEAK;
			} else {
				if (humid <= WET_POINT) {
					return Biome.Type.MOUNTAIN_GRASSLAND;
				} else {
					return Biome.Type.MOUNTAIN_FOREST;
				}
			}
		}
	}
	
}
