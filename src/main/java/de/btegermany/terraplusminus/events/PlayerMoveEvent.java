package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;


public class PlayerMoveEvent implements Listener {
    final int yOffsetConfigEntry;
    private final int xOffset;
    private final int zOffset;
    private final boolean linkedWorldsEnabled;

    private final String linkedWorldsMethod;
    private final Plugin plugin;
    private final HashMap<String, Integer> worldHashMap;

    public PlayerMoveEvent(Plugin plugin) {
        this.plugin = plugin;
        this.xOffset = Terraplusminus.config.getInt("terrain_offset.x");
        this.yOffsetConfigEntry = Terraplusminus.config.getInt("terrain_offset.y");
        this.zOffset = Terraplusminus.config.getInt("terrain_offset.z");
        this.linkedWorldsEnabled = Terraplusminus.config.getBoolean("linked_worlds.enabled");
        this.linkedWorldsMethod = Terraplusminus.config.getString("linked_worlds.method");
        this.worldHashMap = new HashMap<>();
        if (this.linkedWorldsEnabled && this.linkedWorldsMethod != null
                && this.linkedWorldsMethod.equalsIgnoreCase("MULTIVERSE")) {
            List<LinkedWorld> worlds = ConfigurationHelper.getWorlds();
            for (LinkedWorld world : worlds) {
                this.worldHashMap.put(world.getWorldName(), world.getOffset());
            }
            Terraplusminus.instance.getComponentLogger().info("Linked worlds enabled, using Multiverse method.");
        }
        this.startKeepActionBarAlive();
    }

    @EventHandler
    void onPlayerMove(org.bukkit.event.player.@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        setHeightInActionBar(player);
    }

    private void startKeepActionBarAlive() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                setHeightInActionBar(p);
            }
        }, 0, 20);
    }

    private void setHeightInActionBar(@NotNull Player p) {
        worldHashMap.putIfAbsent(p.getWorld().getName(), yOffsetConfigEntry);
        if (p.getInventory().getItemInMainHand().getType() != Material.DEBUG_STICK) {
            int height = p.getLocation().getBlockY() - worldHashMap.get(p.getWorld().getName());
            p.sendActionBar(Component.text(height + "m").decorate(TextDecoration.BOLD));
        }
    }

    @EventHandler
    void onPlayerFall(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!this.linkedWorldsEnabled && !this.linkedWorldsMethod.equalsIgnoreCase("MULTIVERSE")) {
            return;
        }

        Player p = event.getPlayer();
        World world = p.getWorld();
        Location location = p.getLocation();

        // Verzögerte Teleportation
        new BukkitRunnable() {
            @Override
            public void run() {
                // Teleport player from world to world
                if (p.getLocation().getY() < 0) {
                    LinkedWorld previousServer = ConfigurationHelper.getPreviousServerName(world.getName());
                    if (previousServer != null) {
                        teleportPlayer(previousServer, location, p);
                    }
                } else if (p.getLocation().getY() > world.getMaxHeight()) {
                    LinkedWorld nextServer = ConfigurationHelper.getNextServerName(world.getName());
                    if (nextServer != null) {
                        teleportPlayer(nextServer, location, p);
                    }
                }
            }
        }.runTaskLater(plugin, 60L);
    }

    private void teleportPlayer(@NotNull LinkedWorld linkedWorld, @NotNull Location location, @NotNull Player p) {
        World tpWorld = Bukkit.getWorld(linkedWorld.getWorldName());
        Location newLocation = new Location(tpWorld, location.getX() + xOffset,
                Objects.requireNonNull(tpWorld).getMinHeight(), location.getZ() + zOffset, location.getYaw(), location.getPitch());
        p.teleportAsync(newLocation);
        p.sendMessage(Terraplusminus.config.getString("prefix") + "§7You have been teleported to another world.");
    }
}
