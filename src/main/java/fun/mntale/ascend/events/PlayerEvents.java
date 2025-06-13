package fun.mntale.ascend.events;

import fun.mntale.ascend.Ascend;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class PlayerEvents implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // If player was teleporting, store their locations
        if (Ascend.instance.getPlayerManager().isPlayerTeleporting(uuid)) {
            Ascend.instance.getPlayerManager().storeLaunchLocation(uuid, Ascend.instance.getPlayerManager().getCenterPosition(uuid));
        }
        
        // Cancel any ongoing countdown
        Ascend.instance.getPlayerManager().cancelCountdown(uuid);
        
        // Remove from starting countdown set
        Ascend.instance.getPlayerManager().removeFromStartingCountdown(uuid);
        
        // If player was teleporting, remove levitation and clean up
        if (Ascend.instance.getPlayerManager().removeTeleportingPlayer(uuid)) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
            Ascend.instance.getPlayerManager().removeCenterPosition(uuid);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Check if player has stored launch location
        Location launchLoc = Ascend.instance.getPlayerManager().getStoredLaunchLocation(uuid);
        if (launchLoc != null) {
            // Teleport player back to their launch position
            player.removePotionEffect(PotionEffectType.LEVITATION);
            player.teleportAsync(launchLoc);
            player.sendMessage("§eYou have been returned to your launch location.");
        }
    }
} 