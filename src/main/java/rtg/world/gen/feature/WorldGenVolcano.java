package rtg.world.gen.feature;

import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import rtg.config.rtg.ConfigRTG;
import rtg.util.CellNoise;
import rtg.util.OpenSimplexNoise;
import rtg.util.TerrainMath;

import java.util.Random;

public class WorldGenVolcano 
{
    protected static Block volcanoBlock = GameData.getBlockRegistry().getObject("minecraft:obsidian");
    protected static byte volcanoBlockMeta = (byte) 6;
    protected static Block volcanoMixBlock = GameData.getBlockRegistry().getObject("minecraft:cobblestone");
    protected static byte volcanoMixBlockMeta = (byte) ConfigRTG.volcanoMixBlockMeta;
    protected static Block volcanoMixBlock2 = GameData.getBlockRegistry().getObject("minecraft:dirt");
    protected static byte volcanoMixBlockMeta2 = (byte) ConfigRTG.volcanoMixBlockMeta2;
    protected static Block lavaBlock = ConfigRTG.enableVolcanoEruptions ? Blocks.flowing_lava : Blocks.lava;

	// How much stretched the vent/mouth is
	private static final float ventEccentricity = 8f;
	private static final float ventRadius = 7f;

	private static final int lavaHeight = 138 + 3 + (ConfigRTG.enableVolcanoEruptions ? 5 : 0);	// + 3 to account for lava cone tip
	private static final int baseVolcanoHeight = 142 + 8;


	public static void build(Block[] blocks, byte[] metadata, World world, Random mapRand, int baseX, int baseY, int chunkX, int chunkY, OpenSimplexNoise simplex, CellNoise cell, float[] noise)
	{
		int i, j;
		float distanceEll, height, terrainHeight, obsidian;
		Block b;
		byte meta;

		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				i = (chunkX * 16) + x;
				j = (chunkY * 16) + z;

				// Distance in blocks from the center of the volcano
				distanceEll = (float)TerrainMath.dis2Elliptic(i, j, baseX * 16, baseY * 16,
						simplex.noise2(i / 250f, j / 250f) * ventEccentricity,
						simplex.octave(1).noise2(i / 250f, j / 250f) * ventEccentricity);

				// Height above which obsidian is placed
				obsidian = -5f + distanceEll;
				obsidian += simplex.octave(1).noise2(i / 55f, j / 55f) * 12f;
				obsidian += simplex.octave(2).noise2(i / 25f, j / 25f) * 5f;
				obsidian += simplex.octave(3).noise2(i / 9f, j / 9f) * 3f;

				// Make the volcanoes "mouth" more interesting
				float ventNoise = simplex.noise2(i / 12f, j / 12f) * 3f;
				ventNoise += simplex.octave(1).noise2(i / 4f, j / 4f) * 1.5f;

				// Are we in the volcano's throat/conduit?
				if(distanceEll < ventRadius + ventNoise)
				{
					height = simplex.noise2(i / 5f, j / 5f) * 2f;
					for (int y = 255; y > -1; y--)
					{
						// Above lava
						if(y > lavaHeight)
						{
							if(blocks[cta(x, y, z)] != Blocks.air)
							{
								blocks[cta(x, y, z)] = Blocks.air;
							}
						}
						// Below lava and above obsidian
						else if(y > obsidian && y < (lavaHeight - 9) + height)
						{
						    blocks[cta(x, y, z)] = volcanoBlock;
						    metadata[cta(x, y, z)] = volcanoBlockMeta;
						}
						// In lava
						else if(y < lavaHeight + 1)
						{
							if(distanceEll + y < lavaHeight + 3) // + 3 to cut the tip of the lava
							blocks[cta(x, y, z)] = lavaBlock;
						}
						// Below obsidian
						else if(y < obsidian + 1)
						{
							if(blocks[cta(x, y, z)] == Blocks.air)
							{
							    blocks[cta(x, y, z)] = Blocks.stone;
							    metadata[cta(x, y, z)] = (byte)0;
							}
							else
							{
								break;
							}
						}
					}
				}
				else
				{
					terrainHeight = baseVolcanoHeight - (float)Math.pow(distanceEll, 0.89f);
					terrainHeight += simplex.octave(1).noise2(i / 112f, j / 112f) * 5.5f;
					terrainHeight += simplex.octave(2).noise2(i / 46f, j / 46f) * 4.5f;
					terrainHeight += simplex.octave(3).noise2(i / 16f, j / 16f) * 2.5f;
					terrainHeight += simplex.octave(4).noise2(i / 5f, j / 5f) * 1f;

					if(terrainHeight > noise[x * 16 + z])
					{
						noise[x * 16 + z] = terrainHeight;
					}

					for (int y = 255; y > -1; y--)
					{
						if(y <= terrainHeight)
						{
							b = blocks[cta(x, y, z)];
							meta = metadata[cta(x, y, z)];

							if(b == Blocks.air)
							{
                                /*************************************
                                 * WARNING: Spaghetti surfacing code *
                                 *************************************/

								if(y > obsidian)
								{
                                    // Patches of Netherrack
                                    if(distanceEll < 70 && isOnSurface(x, y, z, blocks))
                                    {
                                        float patchNoise = simplex.noise2(i / 10f, j / 10f) * 1.2f;
										patchNoise += simplex.octave(2).noise2(i / 30f, j / 30f) * .3;
										patchNoise += simplex.octave(2).noise2(i / 5f, j / 5f) * .5;
                                        if(patchNoise > .9)
                                        {
                                            blocks[cta(x, y, z)] = Blocks.coal_block;
                                            metadata[cta(x, y, z)] = meta;
                                            continue;
                                        }
                                    }

                                    // Surfacing
                                    if(distanceEll < 25)
                                    {
                                        if(mapRand.nextInt(40) == 0)
                                        {
                                            b = Blocks.coal_block;
                                            meta = (byte)0;
                                        }
                                        else {
                                            b = Blocks.obsidian;
                                            meta = (byte)0;
                                        }
                                    }
                                    else if(distanceEll < 60)
                                    {
                                        float jitterNoise = simplex.noise2(i / 40, j / 40f) * 10f;
                                        float powerNoise = simplex.octave(1).noise2(i / 30, j / 30f) * 2;
                                        if(mapRand.nextInt(10+(int)Math.pow(Math.abs((distanceEll+jitterNoise)-55),1.5+powerNoise)+1) == 0)
                                        {
                                            b = mapRand.nextInt(7) == 0 ? Blocks.netherrack : mapRand.nextInt(5) == 0 ? Blocks.stone : volcanoMixBlock;
                                            meta = volcanoMixBlockMeta;
                                        }
                                        else {
                                            b = volcanoBlock;
                                            meta = volcanoBlockMeta;
                                        }
                                    }
                                    else if(distanceEll < 80)
                                    {
                                        float jitterNoise = simplex.octave(2).noise2(i / 40, j / 40f) * 10f;
                                        float powerNoise = simplex.octave(3).noise2(i / 40, j / 40f) * 2;
                                        if(mapRand.nextInt(5+(int)Math.pow(Math.abs(distanceEll+jitterNoise-75),1.5+powerNoise)+1) == 0)
                                        {
                                            b = mapRand.nextInt(4) == 0 ? Blocks.gravel : volcanoMixBlock2;
                                            meta = volcanoMixBlockMeta2;
                                        }
                                        else
                                        {
											b = volcanoBlock;
											meta = volcanoBlockMeta;
                                        }
                                    }
                                    else
                                    {
                                        b = Blocks.grass;
                                        meta = (byte)0;
                                    }
								}
								else
								{
									b = Blocks.stone;
									meta = (byte)0;
								}
							}
							else
							{
								break;
							}

							blocks[cta(x, y, z)] = b;
							metadata[cta(x, y, z)] = meta;
						}
					}
				}
			}
		}
	}

    private static boolean isOnSurface(int x, int y, int z, Block[] blocks) {
        return (blocks[cta(x, y+1, z)] == Blocks.air);
    }

    public static int cta(int x, int y, int z)
    {
    	return (x * 16 + z) * 256 + y;
    }
}