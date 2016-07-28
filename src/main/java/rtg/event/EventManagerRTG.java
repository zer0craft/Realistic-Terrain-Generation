package rtg.event;

import java.util.ArrayList;
import java.util.Random;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.SaplingGrowTreeEvent;
import net.minecraftforge.event.terraingen.WorldTypeEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import rtg.RTG;
import rtg.config.rtg.ConfigRTG;
import rtg.util.Acceptor;
import rtg.util.Logger;
import rtg.util.RandomUtil;
import rtg.world.WorldTypeRTG;
import rtg.world.biome.WorldChunkManagerRTG;
import rtg.world.biome.realistic.RealisticBiomeBase;
import rtg.world.gen.MapGenCavesRTG;
import rtg.world.gen.MapGenRavineRTG;
import rtg.world.gen.feature.tree.rtg.TreeRTG;
import rtg.world.gen.genlayer.RiverRemover;
import rtg.world.gen.structure.MapGenScatteredFeatureRTG;
import rtg.world.gen.structure.MapGenVillageRTG;


public class EventManagerRTG
{
    // Event handlers.
    private final WorldEventRTG WORLD_EVENT_HANDLER = new WorldEventRTG();
    private final LoadChunkRTG LOAD_CHUNK_EVENT_HANDLER = new LoadChunkRTG();
    private final GenerateMinableRTG GENERATE_MINABLE_EVENT_HANDLER = new GenerateMinableRTG();
    private final InitBiomeGensRTG INIT_BIOME_GENS_EVENT_HANDLER = new InitBiomeGensRTG();
    private final InitMapGenRTG INIT_MAP_GEN_EVENT_HANDLER = new InitMapGenRTG();
    private final SaplingGrowTreeRTG SAPLING_GROW_TREE_EVENT_HANDLER = new SaplingGrowTreeRTG();

    private WeakHashMap<Integer, Acceptor<ChunkEvent.Load>> chunkLoadEvents = new WeakHashMap<>();
    private long worldSeed;

    public EventManagerRTG() {

    }

    public class LoadChunkRTG
    {
        LoadChunkRTG() {
            logEventMessage("Initialising LoadChunkRTG...");
        }

        @SubscribeEvent
        public void loadChunkRTG(ChunkEvent.Load event) {
            Acceptor<ChunkEvent.Load> acceptor = chunkLoadEvents.get(event.world.provider.dimensionId);
            if (acceptor != null) {
                acceptor.accept(event);
            }
        }
    }

    public class GenerateMinableRTG
    {
        GenerateMinableRTG() {
            logEventMessage("Initialising GenerateMinableRTG...");
        }

        @SubscribeEvent
        public void generateMinableRTG(OreGenEvent.GenerateMinable event) {

            switch (event.type) {

                case COAL:
                    if (!ConfigRTG.generateOreCoal) { event.setResult(Result.DENY); }
                    return;

                case IRON:
                    if (!ConfigRTG.generateOreIron) { event.setResult(Result.DENY); }
                    return;

                case REDSTONE:
                    if (!ConfigRTG.generateOreRedstone) { event.setResult(Result.DENY); }
                    return;

                case GOLD:
                    if (!ConfigRTG.generateOreGold) { event.setResult(Result.DENY); }
                    return;

                case LAPIS:
                    if (!ConfigRTG.generateOreLapis) { event.setResult(Result.DENY); }
                    return;

                case DIAMOND:
                    if (!ConfigRTG.generateOreDiamond) { event.setResult(Result.DENY); }
                    return;

                default:
                	break;
            }
        }
    }

    public class InitBiomeGensRTG
    {
        InitBiomeGensRTG() {
            logEventMessage("Initialising InitBiomeGensRTG...");
        }

        @SubscribeEvent
        public void initBiomeGensRTG(WorldTypeEvent.InitBiomeGens event) {

            if (!(event.worldType instanceof WorldTypeRTG)) {

                /*
                 * None of RTG's other event handlers need to be unregistered this early since they'll all
                 * get unregistered before a non-RTG world loads when WorldEvent.Load is fired, but it's
                 * better to be safe than sorry, so let's unregister them here to be safe.
                 */
                unRegisterEventHandlers();

                return;
            }

            if (event.newBiomeGens[0].getClass().getName().contains("GenLayerEB")) {
                return;
            }

            try {
                event.newBiomeGens = new RiverRemover().riverLess(event.originalBiomeGens);
            } catch (ClassCastException ex) {
                //throw ex;
                // failed attempt because the GenLayers don't end with GenLayerRiverMix
            }
        }
    }

    public class InitMapGenRTG
    {
        InitMapGenRTG() {
            logEventMessage("Initialising InitMapGenRTG...");
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public void initMapGenRTG(InitMapGenEvent event) {

            Logger.debug("event type = %s", event.type.toString());
            Logger.debug("event originalGen = %s", event.originalGen.toString());

            switch (event.type) {
                case SCATTERED_FEATURE:
                    event.newGen = new MapGenScatteredFeatureRTG();
                    break;

                case VILLAGE:
                    if (ConfigRTG.enableVillageModifications) {
                        event.newGen = new MapGenVillageRTG();
                    }
                    break;

                case CAVE:
                    if (ConfigRTG.enableCaveModifications) {
                        event.newGen = new MapGenCavesRTG();
                    }
                    break;

                case RAVINE:
                    if (ConfigRTG.enableRavineModifications) {
                        event.newGen = new MapGenRavineRTG();
                    }
                    break;

                default:
                	break;
            }
            
            Logger.debug("event newGen = %s", event.newGen.toString());
        }
    }

    public class SaplingGrowTreeRTG
    {
        SaplingGrowTreeRTG() {
            logEventMessage("Initialising SaplingGrowTreeRTG...");
        }

        @SubscribeEvent
        public void saplingGrowTreeRTG(SaplingGrowTreeEvent event) {

            // Are RTG saplings enabled?
            if (!ConfigRTG.enableRTGSaplings) {
                return;
            }

            // Are we in an RTG world? Do we have RTG's chunk manager?
            if (!(event.world.getWorldInfo().getTerrainType() instanceof WorldTypeRTG) || !(event.world.getWorldChunkManager() instanceof WorldChunkManagerRTG)) {
                return;
            }

            Random rand = event.rand;

            // Should we generate a vanilla tree instead?
            if (rand.nextInt(ConfigRTG.rtgTreeChance) != 0) {

                Logger.debug("Skipping RTG tree generation.");
                return;
            }

            World world = event.world;
            int x = event.x;
            int y = event.y;
            int z = event.z;

            Block saplingBlock = world.getBlock(x, y, z);
            byte saplingMeta = (byte) saplingBlock.getDamageValue(world, x, y, z);

            WorldChunkManagerRTG cmr = (WorldChunkManagerRTG) world.getWorldChunkManager();
            //BiomeGenBase bgg = cmr.getBiomeGenAt(x, z);
            BiomeGenBase bgg = world.getBiomeGenForCoords(x, z);
            RealisticBiomeBase rb = RealisticBiomeBase.getBiome(bgg.biomeID);
            ArrayList<TreeRTG> biomeTrees = rb.rtgTrees;

            Logger.debug("Biome = %s", rb.baseBiome.biomeName);
            Logger.debug("Ground Sapling Block = %s", saplingBlock.getLocalizedName());
            Logger.debug("Ground Sapling Meta = %d", saplingMeta);

            if (biomeTrees.size() > 0) {

                // First, let's get all of the trees in this biome that match the sapling on the ground.
                ArrayList<TreeRTG> validTrees = new ArrayList<>();

                for (int i = 0; i < biomeTrees.size(); i++) {

                    Logger.debug("Biome Tree #%d = %s", i, biomeTrees.get(i).getClass().getName());
                    Logger.debug("Biome Tree #%d Sapling Block = %s", i, biomeTrees.get(i).saplingBlock.getClass().getName());
                    Logger.debug("Biome Tree #%d Sapling Meta = %d", i, biomeTrees.get(i).saplingMeta);

                    if (saplingBlock == biomeTrees.get(i).saplingBlock && saplingMeta == biomeTrees.get(i).saplingMeta) {

                        validTrees.add(biomeTrees.get(i));
                        Logger.debug("Valid tree found!");
                    }
                }

                // If there are valid trees, then proceed; otherwise, let's get out here.
                if (validTrees.size() > 0) {

                    // Get a random tree from the list of valid trees.
                    TreeRTG tree = validTrees.get(rand.nextInt(validTrees.size()));

                    Logger.debug("Tree = %s", tree.getClass().getName());

                    // Set the trunk size if min/max values have been set.
                    if (tree.minTrunkSize > 0 && tree.maxTrunkSize > tree.minTrunkSize) {

                        tree.trunkSize = RandomUtil.getRandomInt(rand, tree.minTrunkSize, tree.maxTrunkSize);
                    }

                    // Set the crown size if min/max values have been set.
                    if (tree.minCrownSize > 0 && tree.maxCrownSize > tree.minCrownSize) {

                        tree.crownSize = RandomUtil.getRandomInt(rand, tree.minCrownSize, tree.maxCrownSize);
                    }

                    /*
                     * Set the generateFlag to what it needs to be for growing trees from saplings,
                     * generate the tree, and then set it back to what it was before.
                     *
                     * TODO: Does this affect the generation of normal RTG trees? - Pink
                     */
                    int oldFlag = tree.generateFlag;
                    tree.generateFlag = 3;
                    boolean generated = tree.generate(world, rand, x, y, z);
                    tree.generateFlag = oldFlag;

                    if (generated) {

                        // Prevent the original tree from generating.
                        event.setResult(Result.DENY);

                        // Sometimes we have to remove the sapling manually because some trees grow around it, leaving the original sapling.
                        if (world.getBlock(x, y, z) == saplingBlock) {
                            world.setBlock(x, y, z, Blocks.air, (byte)0, 2);
                        }
                    }
                }
                else {

                    Logger.debug("There are no RTG trees associated with the sapling on the ground." +
                            " Generating a vanilla tree instead.");
                }
            }
        }
    }

    public class WorldEventRTG
    {
        WorldEventRTG() {
            logEventMessage("Initialising WorldEventRTG...");
        }

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load event) {

            // This event fires for each dimension loaded (and then one last time in which it returns 0?),
            // so initialise a field to 0 and set it to the world seed and only display it in the log once.
            if (worldSeed != event.world.getSeed() && event.world.getSeed() != 0) {

                worldSeed = event.world.getSeed();
                Logger.info("World Seed: " + worldSeed);
            }

            /*
             * When loading a non-RTG world, we need to make sure that RTG's event handlers are unregistered.
             *
             * If we're loading an RTG world, RTG's event handlers should already be registered at this point,
             * but it doesn't hurt to call registerEventHandlers() here because Forge only registers event
             * handlers that aren't already registered, so better to be safe than sorry.
             */
            if (!(event.world.getWorldInfo().getTerrainType() instanceof WorldTypeRTG)) {

                RTG.eventMgr.unRegisterEventHandlers();
            }
            else {

                RTG.eventMgr.registerEventHandlers();
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
        	
            // Reset the world seed so that it logs on the next server start if the seed is the same as the last load.
            worldSeed = 0;

            /*
             * When loading an RTG world, WorldTypeEvent.InitBiomeGens needs to be registered before
             * FMLServerAboutToStartEvent is fired, so the only way to ensure that it gets registered
             * between unloading a non-RTG world and loading an RTG world is to register RTG's event handlers
             * when a non-RTG world unloads.
             */
            if (!(event.world.getWorldInfo().getTerrainType() instanceof WorldTypeRTG)) {

                RTG.eventMgr.registerEventHandlers();
            }
        }
    }

    /*
     * This method registers most of RTG's event handlers.
     *
     * We don't need to check if the event handlers are unregistered before registering them
     * because Forge already performs those checks. This means that we could execute this method a
     * million times, and each event handler would still only be registered once.
     */
    public void registerEventHandlers() {

        logEventMessage("Registering RTG's event handlers...");

        MinecraftForge.EVENT_BUS.register(WORLD_EVENT_HANDLER);
        MinecraftForge.EVENT_BUS.register(LOAD_CHUNK_EVENT_HANDLER);
        MinecraftForge.ORE_GEN_BUS.register(GENERATE_MINABLE_EVENT_HANDLER);
        MinecraftForge.TERRAIN_GEN_BUS.register(INIT_BIOME_GENS_EVENT_HANDLER);
        MinecraftForge.TERRAIN_GEN_BUS.register(INIT_MAP_GEN_EVENT_HANDLER);
        MinecraftForge.TERRAIN_GEN_BUS.register(SAPLING_GROW_TREE_EVENT_HANDLER);

        logEventMessage("RTG's event handlers have been registered successfully.");
    }

    /*
     * This method unregisters most of RTG's event handlers.
     *
     * We don't need to check if the event handlers are registered before unregistering them
     * because Forge already performs those checks. If this method gets executed when none
     * of RTG's event handlers are registered, nothing bad will happen.
     */
    public void unRegisterEventHandlers() {

        logEventMessage("Unregistering RTG's event handlers...");

        /*
         * The world event handler must always be registered.
         *
         * MinecraftForge.EVENT_BUS.unregister(WORLD_EVENT_HANDLER);
         */

        MinecraftForge.EVENT_BUS.unregister(LOAD_CHUNK_EVENT_HANDLER);
        MinecraftForge.ORE_GEN_BUS.unregister(GENERATE_MINABLE_EVENT_HANDLER);
        MinecraftForge.TERRAIN_GEN_BUS.unregister(INIT_BIOME_GENS_EVENT_HANDLER);
        MinecraftForge.TERRAIN_GEN_BUS.unregister(INIT_MAP_GEN_EVENT_HANDLER);
        MinecraftForge.TERRAIN_GEN_BUS.unregister(SAPLING_GROW_TREE_EVENT_HANDLER);

        logEventMessage("RTG's event handlers have been unregistered successfully.");
    }

    public void setDimensionChunkLoadEvent(int dimension, Acceptor<ChunkEvent.Load> action) {
        chunkLoadEvents.put(dimension, action);
    }

    private static void logEventMessage(String message) {
        Logger.info("RTG Event System: " + message);
    }
}