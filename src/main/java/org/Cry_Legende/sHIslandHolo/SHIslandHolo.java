package org.Cry_Legende.sHIslandHolo;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class SHIslandHolo extends JavaPlugin implements TabCompleter {

    private static final String RELOAD = "shislandholo.admin.reload";

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
        try {
            Loaded loaded = readFiles();
            settings = loaded.settings();
            messages = loaded.messages();
        } catch (ConfigException e) {
            getLogger().severe("Fichier " + e.getFile() + " illisible, valeurs du jar utilisées : " + e.getMessage());
            YamlConfiguration config = bundled("config.yml");
            settings = Settings.load(config, bundled("tips.yml").getConfigurationSection("tips"), getLogger());
            messages = new Messages(bundled("messages.yml"));
        }
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

    /** Erreur de lecture d'un fichier de configuration : nom du fichier et cause. */
    private static final class ConfigException extends Exception {
        private final String file;

        ConfigException(String file, String message) {
            super(message);
            this.file = file;
        }

        String getFile() {
            return file;
        }
    }

    /** Réglages, conseils et messages lus ensemble : rien n'est appliqué si l'un des trois fichiers est invalide. */
    private record Loaded(Settings settings, Messages messages) {
    }

    private Loaded readFiles() throws ConfigException {
        YamlConfiguration config = readYaml("config.yml");
        YamlConfiguration tips = readYaml("tips.yml");
        YamlConfiguration loadedMessages = readYaml("messages.yml");
        return new Loaded(Settings.load(config, tips.getConfigurationSection("tips"), getLogger()), new Messages(loadedMessages));
    }

    /** Fichier du dossier du plugin lu strictement (erreur de syntaxe signalée), complété par les valeurs du jar. */
    private YamlConfiguration readYaml(String name) throws ConfigException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new File(getDataFolder(), name));
        } catch (IOException | InvalidConfigurationException e) {
            String reason = e.getMessage() == null ? e.toString() : e.getMessage().lines().findFirst().orElse("");
            throw new ConfigException(name, reason);
        }
        if (!name.equals("tips.yml")) {
            // tips.yml n'a pas de valeurs par défaut : le complément du jar rendrait aux conseils les clés qu'on y a retirées
            yaml.setDefaults(bundled(name));
        }
        return yaml;
    }

    private YamlConfiguration bundled(String name) {
        try (InputStream in = getResource(name)) {
            return in == null ? new YamlConfiguration()
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
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
        if (!sender.hasPermission(RELOAD)) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            messages.send(sender, "admin-usage");
            return true;
        }
        Loaded loaded;
        try {
            loaded = readFiles();
        } catch (ConfigException e) {
            getLogger().severe("Rechargement annulé, " + e.getFile() + " illisible : " + e.getMessage());
            messages.send(sender, "reload-failed", "file", e.getFile(), "error", String.valueOf(e.getMessage()));
            return true;
        }
        holos.removeAll();
        settings = loaded.settings();
        messages = loaded.messages();
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
        String option;
        if (command.getName().equalsIgnoreCase("shislandholo")) {
            if (!sender.hasPermission(RELOAD)) {
                return List.of();
            }
            option = "reload";
        } else {
            option = "cacher";
        }
        return option.startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of(option) : List.of();
    }
}
