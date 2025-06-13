package fun.mntale.ascend;

import org.bukkit.plugin.java.JavaPlugin;
import fun.mntale.ascend.events.PlayerEvents;
import fun.mntale.ascend.events.MovementEvents;
import fun.mntale.ascend.events.DamageEvents;
import fun.mntale.ascend.events.InteractionEvents;
import fun.mntale.ascend.utils.PlayerManager;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class Ascend extends JavaPlugin implements Listener {
    public static Ascend instance;
    public static final int LAUNCH_HEIGHT = 600;
    public static final int TELEPORT_HEIGHT = 700;
    private PlayerManager playerManager;
    private boolean debugMode;

    @Override
    public void onEnable() {
        instance = this;
        playerManager = new PlayerManager();
        
        // Load configuration
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        debugMode = config.getBoolean("debug-mode", false);
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerEvents(), this);
        getServer().getPluginManager().registerEvents(new MovementEvents(), this);
        getServer().getPluginManager().registerEvents(new DamageEvents(), this);
        getServer().getPluginManager().registerEvents(new InteractionEvents(), this);
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("Ascend plugin has been enabled!");
        if (debugMode) {
            getLogger().info("Debug mode is enabled!");
        }
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public void debugPlayerMessage(Player player, String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] [Player Message] " + player.getName() + " (" + player.getUniqueId() + "): " + message);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Ascend plugin has been disabled!");
    }
}
