package org.frh.blazespawnerlock;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

public class BlazeSpawnerLock extends JavaPlugin implements Listener {

    /* ===============================
       UPDATE SETTINGS
       =============================== */
    private static final String VERSION_URL =
            "https://github.com/airtagdev/BlazeSpawnerLock/releases/latest/download/version.txt";
    private static final String DOWNLOAD_URL =
            "https://github.com/airtagdev/BlazeSpawnerLock/releases/latest/download/BlazeSpawnerLock.jar";

    private static final long UPDATE_CHECK_INTERVAL = 6L * 60L * 60L * 20L;

    private static final String BYPASS_PERMISSION = "blazespawnerlock.bypass";
    private static final String RELOAD_PERMISSION = "blazespawnerlock.reload";

    private boolean enabled;
    private boolean checkForUpdate;
    private String denyMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin successfully loaded");

        if (checkForUpdate) {
            checkForUpdates();

            getServer().getScheduler().runTaskTimerAsynchronously(
                    this,
                    this::checkForUpdates,
                    UPDATE_CHECK_INTERVAL,   // delay
                    UPDATE_CHECK_INTERVAL    // period
            );
        }
    }

    /* ===============================
       CONFIG HANDLING
       =============================== */
    private void loadConfig() {
        reloadConfig();

        enabled = getConfig().getBoolean("enabled", true);
        checkForUpdate = getConfig().getBoolean("check-for-update", true);

        denyMessage = ChatColor.translateAlternateColorCodes(
                '&',
                getConfig().getString(
                        "messages.deny",
                        "&l[&c&lBlaze&7&lSpawner&c&lLock&r&l] &cYou cannot break blaze spawners!"
                )
        );
    }

    /* ===============================
       COMMANDS
       =============================== */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sender.sendMessage("§cYou do not have permission to do this.");
                return true;
            }

            loadConfig();
            sender.sendMessage("§aBlazeSpawnerLock config reloaded.");
            return true;
        }

        sender.sendMessage("§cUsage: /" + label + " reload");
        return true;
    }

    /* ===============================
       BREAK PROTECTION
       =============================== */
    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        if (!enabled) return;

        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        if (event.getPlayer().hasPermission(BYPASS_PERMISSION)) return;

        CreatureSpawner spawner = (CreatureSpawner) block.getState();

        if (spawner.getSpawnedType() == EntityType.BLAZE) {
            event.setCancelled(true);

            event.getPlayer().sendMessage(denyMessage);

            try {
                Sound sound = Sound.valueOf("ENTITY_VILLAGER_NO");
                event.getPlayer().playSound(
                        event.getPlayer().getLocation(),
                        sound,
                        1.0f,
                        1.0f
                );
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /* ===============================
       EXPLOSION PROTECTION
       =============================== */
    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        if (!enabled) return;

        Iterator<Block> iterator = event.blockList().iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();

            if (block.getType() != Material.SPAWNER) continue;

            CreatureSpawner spawner = (CreatureSpawner) block.getState();

            if (spawner.getSpawnedType() == EntityType.BLAZE) {
                iterator.remove();
            }
        }
    }

    /* ===============================
       UPDATE CHECKER
       =============================== */
    private void checkForUpdates() {
        try {
            getLogger().info("Checking for updates...");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new URL(VERSION_URL).openStream())
            );

            String remoteVersion = reader.readLine().trim();
            reader.close();

            String localVersion = getDescription().getVersion();

            if (remoteVersion.equals(localVersion)) return;

            getLogger().warning("New version found: " + remoteVersion);
            getLogger().warning("Downloading update...");

            var pluginsDir = getDataFolder().getParentFile();
            var newJar = new java.io.File(pluginsDir, getFile().getName() + ".new");

            try (InputStream in = new URL(DOWNLOAD_URL).openStream()) {
                Files.copy(in, newJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            if (getFile().delete()) {
                newJar.renameTo(getFile());
                getLogger().warning("Update downloaded successfully.");
                getLogger().warning("RESTART THE SERVER to apply the update.");
            } else {
                getLogger().severe("Update downloaded but failed to replace the old plugin file.");
            }

        } catch (Exception e) {
            getLogger().severe("Update failed: " + e.getMessage());
        }
    }
}
