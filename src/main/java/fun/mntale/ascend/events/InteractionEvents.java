package fun.mntale.ascend.events;

import fun.mntale.ascend.Ascend;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.inventory.ItemStack;

public class InteractionEvents implements Listener {
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if player is teleporting or in countdown
        if (Ascend.instance.getPlayerManager().isPlayerTeleporting(player.getUniqueId()) ||
            Ascend.instance.getPlayerManager().hasCountdownTask(player.getUniqueId())) {
            
            // If player is using an ender pearl, cancel the event
            if (item != null && item.getType() == Material.ENDER_PEARL) {
                event.setCancelled(true);
                String message = "§cYou cannot use ender pearls while teleporting!";
                Ascend.instance.debugPlayerMessage(player, message);
            }
        }
    }
    
    @EventHandler
    public void onEntityMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            // Check if player is teleporting or in countdown
            if (Ascend.instance.getPlayerManager().isPlayerTeleporting(player.getUniqueId()) ||
                Ascend.instance.getPlayerManager().hasCountdownTask(player.getUniqueId())) {
                
                event.setCancelled(true);
                String message = "§cYou cannot mount entities while teleporting!";
                Ascend.instance.debugPlayerMessage(player, message);
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        
        // Check if this is an ender pearl teleport
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL || event.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT || event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND  ) {
            // Check if player is teleporting or in countdown
            if (Ascend.instance.getPlayerManager().isPlayerTeleporting(player.getUniqueId()) ||
                Ascend.instance.getPlayerManager().hasCountdownTask(player.getUniqueId())) {
                
                event.setCancelled(true);
                String message = "§cYou cannot use ender pearls while teleporting!";
                Ascend.instance.debugPlayerMessage(player, message);
            }
        }
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is teleporting or in countdown
        if (Ascend.instance.getPlayerManager().isPlayerTeleporting(player.getUniqueId()) ||
            Ascend.instance.getPlayerManager().hasCountdownTask(player.getUniqueId())) {
            
            event.setCancelled(true);
            String message = "§cYou cannot enter a bed while teleporting!";
            Ascend.instance.debugPlayerMessage(player, message);
        }
    }
} 