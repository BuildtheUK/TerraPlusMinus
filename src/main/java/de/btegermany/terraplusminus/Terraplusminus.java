package de.btegermany.terraplusminus;


import de.btegermany.terraplusminus.commands.OffsetCommand;
import de.btegermany.terraplusminus.commands.TpllCommand;
import de.btegermany.terraplusminus.commands.WhereCommand;
import de.btegermany.terraplusminus.events.PlayerJoinEvent;
import de.btegermany.terraplusminus.events.PlayerMoveEvent;
import de.btegermany.terraplusminus.events.PluginMessageEvent;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.FileBuilder;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import de.btegermany.terraplusminus.utils.PlayerHashMapManagement;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.buildtheearth.terraminusminus.TerraConfig;
import net.buildtheearth.terraminusminus.TerraConstants;
import net.buildtheearth.terraminusminus.util.http.Disk;
import net.buildtheearth.terraminusminus.util.http.Http;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static java.lang.String.format;
import static net.daporkchop.lib.common.util.PValidation.checkState;

public final class Terraplusminus extends JavaPlugin implements Listener {
    public static FileConfiguration config;
    public static Terraplusminus instance;

    @Override
    public void onEnable() {
        instance = this;
        PluginDescriptionFile pdf = this.getDescription();
        String pluginVersion = pdf.getVersion();

        this.getComponentLogger().info("\n╭━━━━╮\n" +
                "┃╭╮╭╮┃\n" +
                "╰╯┃┃┣┻━┳━┳━┳━━╮╭╮\n" +
                "╱╱┃┃┃┃━┫╭┫╭┫╭╮┣╯╰┳━━╮\n" +
                "╱╱┃┃┃┃━┫┃┃┃┃╭╮┣╮╭┻━━╯\n" +
                "╱╱╰╯╰━━┻╯╰╯╰╯╰╯╰╯\n" +
                "Version: {}", pluginVersion);

        // Config ------------------]
        ConfigurationSerialization.registerClass(ConfigurationSerializable.class);
        this.saveDefaultConfig();
        config = getConfig();
        this.updateConfig();
        // --------------------------

        // Set-up Terra-- so it looks for its config files in our plugin dir, and then copy its default files there
        this.setupTerraMinusMinus();
        this.extractTerraConfigFileToPluginDir("/net/buildtheearth/terraminusminus/dataset/osm/osm.json5", "osm.json5");
        this.extractTerraConfigFileToPluginDir("config/readme-heights.md", "heights/README.md");
        this.extractTerraConfigFileToPluginDir("config/readme-tree_cover.md", "tree_cover/README.md");

        // Register plugin messaging channel
        PlayerHashMapManagement playerHashMapManagement = new PlayerHashMapManagement();
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:terraplusminus");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "bungeecord:terraplusminus", new PluginMessageEvent(playerHashMapManagement));
        // --------------------------

        // Registering events
        Bukkit.getPluginManager().registerEvents(this, this);
        if (Terraplusminus.config.getBoolean("height_in_actionbar")) {
            Bukkit.getPluginManager().registerEvents(new PlayerMoveEvent(this), this);
        }
        if (Terraplusminus.config.getBoolean("linked_worlds.enabled")) {
            Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(playerHashMapManagement), this);
        }
        // --------------------------

        TerraConfig.reducedConsoleMessages = Terraplusminus.config.getBoolean("reduced_console_messages"); // Disables console log of fetching data

        registerCommands();

        this.getComponentLogger().info("Terraplusminus successfully enabled");
    }

    @Override
    public void onDisable() {
        // Unregister plugin messaging channel
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
        // --------------------------

        this.getComponentLogger().info("Plugin deactivated");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        boolean shouldInstallHeightDatapack = Terraplusminus.config.getBoolean("height_datapack");
        boolean isDefaultWorld = Bukkit.getWorlds().getFirst().getUID().equals(world.getUID());
        if (shouldInstallHeightDatapack && isDefaultWorld) {
            // Datapacks should be installed in the default world and will apply to all of them.
            // Getting the default world this way is reliable according to https://www.spigotmc.org/threads/ask-getting-the-servers-main-world.349626/#post-3238378
            this.enforceDatapackInstallation("world-height-datapack.zip", world);
        }
    }


    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, String id) {
        // Multiverse different y-offset support
        int yOffset = 0;
        if (Terraplusminus.config.getBoolean("linked_worlds.enabled") && Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("MULTIVERSE")) {
            for (LinkedWorld world : ConfigurationHelper.getWorlds()) {
                if (world.getWorldName().equalsIgnoreCase(worldName)) {
                    yOffset = world.getOffset();
                }
            }
        } else {
            yOffset = Terraplusminus.config.getInt("y_offset");
        }
        return new RealWorldGenerator(yOffset);
    }


    public void enforceDatapackInstallation(String datapackResourcePath, World world) {
        String datapackName = Path.of(datapackResourcePath).getFileName().toString();
        File droppedFile = world
                .getWorldFolder().toPath()
                .resolve("datapacks")
                .resolve(datapackName)
                .toFile();
        if (droppedFile.exists()) {
            this.getComponentLogger().debug("Datapack {} was already installed in world {}", datapackName, world.getName());
            return;
        }
        try(InputStream in = this.getResource(datapackResourcePath); OutputStream out = new FileOutputStream(droppedFile)) {
            checkState(in != null, "Missing internal resource: %s", datapackResourcePath);
            in.transferTo(out);
        } catch (IOException io) {
            this.getComponentLogger().error(
                    "Failed to extract datapack from resource '{}' to '{}'",
                    datapackResourcePath, droppedFile.getAbsolutePath()
            );
            throw new RuntimeException(io);
        }
        this.getComponentLogger().error(
                "Datapack {} was missing from world {} and has been automatically installed by Terraplusminus. The server needs to be manually restarted for the change to take effect. Terraplusminus will now shutdown the server so it can be restarted.",
                datapackName, world.getName()
        );
        Bukkit.getServer().shutdown();
    }

    private void updateConfig() {
        new FileBuilder(this);  // Sets FileBuilder#plugin (static field)

        double configVersion = this.config.getDouble("config_version");

        if (configVersion == 0.0) {  // That's the default value if the field was not set at all in the YAML
            this.getComponentLogger().error("Old config detected. Please delete and restart/reload.");
        }
        if (configVersion == 1.0) {
            String passthroughTpll = Terraplusminus.config.getString("passthrough_tpll");
            if (passthroughTpll == null) {
                passthroughTpll = "";
            }
            int y = (int) this.config.getDouble("terrain_offset");
            this.config.set("terrain_offset.x", 0);
            this.config.set("terrain_offset.y", y);
            this.config.set("terrain_offset.z", 0);
            this.config.set("config_version", 1.1);
            this.saveConfig();
            FileBuilder.addLineAbove("terrain_offset", "\n" +
                    "# Generation -------------------------------------------\n" +
                    "# Offset your section which fits into the world.");
            FileBuilder.deleteLine("# Passthrough tpll");
            FileBuilder.deleteLine("passthrough_tpll");
            FileBuilder.addLineAbove("# Generation", "# Passthrough tpll to other bukkit plugins. It will not passthrough when it's empty. Type in the name of your plugin. E.g. Your plugin name is vanillatpll you set passthrough_tpll: 'vanillatpll'\n" +
                    "passthrough_tpll: '" + passthroughTpll + "'\n\n\n"); //Fixes empty config entry from passthrough_tpll

        }
        if (configVersion == 1.1) {
            this.config.set("config_version", 1.2);
            this.saveConfig();
            FileBuilder.addLineAbove("# If disabled, tree generation is turned off.", "" +
                    "# Linked servers ---------------------------------------\n" +
                    "# If the height limit on this server is not enough, other servers can be linked to generate higher or lower sections.\n" +
                    "linked_servers:\n" +
                    "  enabled: false\n" +
                    "  servers:\n" +
                    "    - another_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.\n" +
                    "    - current_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.\n" +
                    "    - another_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032\n");
        }
        if (configVersion == 1.2) {
            this.config.set("config_version", 1.3);
            this.saveConfig();
            FileBuilder.deleteLine("# Linked servers -------------------------------------");
            FileBuilder.deleteLine("# If the height limit on this server is not enough, other servers can be linked to generate higher or lower sections");
            FileBuilder.deleteLine("linked_servers:");
            FileBuilder.deleteLine("  enabled: false");
            FileBuilder.deleteLine("  servers:");
            FileBuilder.deleteLine("- another_server");
            FileBuilder.deleteLine("- current_server");
            FileBuilder.addLineAbove("# If disabled, tree generation is turned off.", "" +
                    "# Linked worlds ---------------------------------------\n" +
                    "# If the height limit in this world/server is not enough, other worlds/servers can be linked to generate higher or lower sections\n" +
                    "linked_worlds:\n" +
                    "  enabled: false\n" +
                    "  method: 'SERVER'                         # 'SERVER' or 'MULTIVERSE'\n" +
                    "  # if method = MULTIVERSE -> world_name, y-offset\n" +
                    "  worlds:\n" +
                    "    - another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.\n" +
                    "    - current_world/server                 # do not change! e.g. this world/server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.\n" +
                    "    - another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032\n\n");
        }
        if (configVersion == 1.3) {
            this.config.set("config_version", 1.4);
            this.saveConfig();
            FileBuilder.addLineAfter("prefix:",
                    "\n# If disabled, the plugin will log every fetched data to the console\n" +
                            "reduced_console_messages: true");
            FileBuilder.deleteLine("- another_world/server");
            FileBuilder.deleteLine("- current_world/server");
            FileBuilder.addLineAbove("# If disabled, tree generation is turned off.",
                    "    - name: another_world/server          # e.g. this world/server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.\n" +
                            "      offset: 2032\n" +
                            "    - name: current_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.\n" +
                            "      offset: 0\n" +
                            "    - name: another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032\n" +
                            "      offset: -2032\n\n");
        }
        if (configVersion == 1.4) {
            this.config.set("config_version", 1.5);
            this.saveConfig();
            boolean differentBiomes = Terraplusminus.config.getBoolean("different_biomes");
            FileBuilder.deleteLine("# The biomes will be generated with https://en.wikipedia.org/wiki/K%C3%B6ppen_climate_classification.");
            FileBuilder.deleteLine("# If turned off, everything will be plains biome.");
            FileBuilder.deleteLine("different_biomes:");
            FileBuilder.addLineAbove("# Customize the material, the blocks will be generated with.",
                    "biomes:\n" +
                    "  # If 'use_dataset' is enabled, biomes will be generated based on: https://en.wikipedia.org/wiki/K%C3%B6ppen_climate_classification.\n" +
                    "  use_dataset: " + differentBiomes + "\n" +
                    "  # If 'use_dataset' is disabled, this biome will be used everywhere instead (if 'generate_trees' is also enabled -> oak and birch).\n" +
                    "  # Possible values found in \"Resource location\" on: https://minecraft.wiki/w/Biome#Biome_IDs (use with namespace e.g. minecraft:plains)\n" +
                    "  biome: minecraft:plains\n\n");
        }
    }

    private void registerCommands() {
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register("tpll", "Teleports you to longitude and latitude", List.of("tpc"), new TpllCommand());
            commands.register("where", "Gives you the longitude and latitude of your minecraft coordinates", new WhereCommand());
            commands.register("offset", "Displays the x,y and z offset of your world", new OffsetCommand());
        });
    }

    private void setupTerraMinusMinus() {
        Disk.setConfigRoot(this.getDataFolder());
        Disk.setCacheRoot(this.getDataPath().resolve("cache").toFile());

        String userAgent = this.createHttpUserAgent();
        this.getComponentLogger().debug("Terraplusminus HTTP user agent: {}", userAgent);
        Http.userAgent(userAgent);
    }

    // This method has to rely on the unstable paper API as we use paper-plugin.yml, which itself is experimental
    private String createHttpUserAgent() {
        PluginMeta metadata = this.getPluginMeta();
        return format(Locale.ENGLISH, "%s/%s (%s/%s; +%s)",
                metadata.getName(),
                metadata.getVersion(),
                TerraConstants.LIB_NAME,
                TerraConstants.LIB_VERSION,
                metadata.getWebsite()
        );
    }

    private void extractTerraConfigFileToPluginDir(@NotNull String resourcePath, @NotNull String dropPath) {
        File droppedFile = this.getDataPath().resolve(dropPath).toFile();
        if (droppedFile.exists()) {
            this.getComponentLogger().debug("Terra-- config file {} is already present in plugin directory", droppedFile.getAbsolutePath());
            return;
        }
        if(droppedFile.getParentFile().mkdirs()) {
            this.getComponentLogger().trace("Created parent directory before extracting Terra-- configuration to: {}", droppedFile.getAbsolutePath());
        }
        try (InputStream resourceStream = this.getClass().getResourceAsStream(resourcePath); OutputStream fileStream = new FileOutputStream(droppedFile)) {
            checkState(resourceStream != null, "Missing internal resource: %s", resourcePath);
            resourceStream.transferTo(fileStream);
        } catch (IOException e) {
            this.getComponentLogger().warn("Failed to drop a Terra configuration file in plugin directory", e);
        }
        this.getComponentLogger().info("Created default Terra-- configuration at {}", droppedFile.getAbsolutePath());
    }

}