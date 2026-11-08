package org.Cry_Legende.sHIslandHolo;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class SHIslandHolo extends JavaPlugin implements TabCompleter {

    private Settings settings;
    private Messages messages;
    private ClickBoxes boxes;
    private HoloService holos;
    private BukkitTask refreshTask;
    private boolean placeholderApi;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfAbsent("tips.yml");
        saveResourceIfAbsent("messages.yml");
        placeholderApi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        boxes = new ClickBoxes();
        holos = new HoloService(this, boxes);
        loadFiles();
        boxes.listen((owner, left) -> Bukkit.getScheduler().runTask(this, () -> holos.click(owner, left)));
        getServer().getPluginManager().registerEvents(new IslandListener(this, holos), this);
        for (String name : List.of("conseil", "shislandholo")) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
        startRefreshTask();
        getLogger().info(settings.tips().size() + " conseil(s) chargé(s)"
                + (placeholderApi ? "." : ", PlaceholderAPI absent : les conditions à placeholders sont fausses."));
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        if (holos != null) {
            holos.removeAll();
        }
        if (boxes != null) {
            boxes.close();
        }
    }

    Settings settings() {
        return settings;
    }

    Messages messages() {
        return messages;
    }

    boolean hasPlaceholderApi() {
        return placeholderApi;
    }

    private void loadFiles() {
        reloadConfig();
        YamlConfiguration tips = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "tips.yml"));
        settings = Settings.load(getConfig(), tips.getConfigurationSection("tips"), getLogger());
        messages = new Messages(new File(getDataFolder(), "messages.yml"));
    }

    private void startRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(this, holos::refreshAll, 40L, settings.refreshTicks());
    }

    private void saveResourceIfAbsent(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("shislandholo")) {
            return adminCommand(sender, args);
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shislandholo.conseil")) {
            messages.send(player, "no-permission");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cacher")) {
            if (!holos.hide(player)) {
                messages.send(player, "nothing-shown");
            }
            return true;
        }
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        if (superiorPlayer == null || superiorPlayer.getIsland() == null) {
            messages.send(player, "no-island");
            return true;
        }
        messages.send(player, holos.restore(player) ? "tips-restored" : "nothing-hidden");
        return true;
    }

    private boolean adminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shislandholo.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            messages.send(sender, "admin-usage");
            return true;
        }
        holos.removeAll();
        loadFiles();
        startRefreshTask();
        holos.refreshAll();
        messages.send(sender, "reloaded", "tips", String.valueOf(settings.tips().size()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String option = command.getName().equalsIgnoreCase("shislandholo") ? "reload" : "cacher";
        return option.startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of(option) : List.of();
    }
}
