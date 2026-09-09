package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.Permission;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.buildtheearth.terraminusminus.TerraminusminusService;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class WhereCommand implements BasicCommand {

    private final Terraplusminus plugin;
    private final TerraminusminusService terraminusminusService;

    public WhereCommand(Terraplusminus plugin, TerraminusminusService terraminusminusService) {
        this.plugin = plugin;
        this.terraminusminusService = terraminusminusService;
    }

    @Override
    public boolean canUse(final @NonNull CommandSender sender) {
        return sender instanceof Player && Permission.WHERE_CMD.isGrantedTo(sender);
    }

    @Override
    public void execute(@NonNull CommandSourceStack stack, String @Nullable [] args) {
        if (!(stack.getSender() instanceof Player player)) return; // Will not happen because of Brigadier

        int xOffset = plugin.getConfig().getInt("terrain_offset.x");
        int zOffset = plugin.getConfig().getInt("terrain_offset.z");

        TextComponent message = new TextComponent(plugin.getConfig().getString("prefix"));

        double playerX = player.getLocation().getX() - xOffset;
        double playerZ = player.getLocation().getZ() - zOffset;
        try {
            double[] coordinates = terraminusminusService.toGeo(playerX, playerZ);
            message.addExtra("§7Your coordinates are:");
            message.addExtra("\n§8" + coordinates[1] + ", " + coordinates[0] + "§7.");
            message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://maps.google.com/maps?t=k&q=loc:" + coordinates[1] + "+" + coordinates[0]));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7Click here to view in Google Maps.").create()));
        } catch (OutOfProjectionBoundsException e) {
            message.addExtra(ChatColor.RED + "You are currently outside of the world's projection and your location in the Minecraft world has no equivalent on Earth.");
        }
        player.spigot().sendMessage(message);
    }
}
