package fun.mntale.ascend;

import org.bukkit.plugin.java.JavaPlugin;
import fun.mntale.ascend.events.PlayerEvents;
import fun.mntale.ascend.events.MovementEvents;
import fun.mntale.ascend.events.DamageEvents;
import fun.mntale.ascend.utils.PlayerManager;

public class Ascend extends JavaPlugin {
    public static Ascend instance;
    public static final int LAUNCH_HEIGHT = 600;
    public static final int TELEPORT_HEIGHT = 700;
    private PlayerManager playerManager;

    @Override
    public void onEnable() {
        instance = this;
        playerManager = new PlayerManager();
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerEvents(), this);
        getServer().getPluginManager().registerEvents(new MovementEvents(), this);
        getServer().getPluginManager().registerEvents(new DamageEvents(), this);
        
        getLogger().info("Ascend plugin has been enabled!");
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    @Override
    public void onDisable() {
        getLogger().info("Ascend plugin has been disabled!");
    }
}
