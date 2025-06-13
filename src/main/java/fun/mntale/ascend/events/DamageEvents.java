package fun.mntale.ascend.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import fun.mntale.ascend.Ascend;

public class DamageEvents implements Listener {
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            // Cancel fall damage if player is teleporting
            if (Ascend.instance.getPlayerManager().isPlayerTeleporting(player.getUniqueId())) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                    Ascend.instance.debug("Cancelled fall damage for teleporting player " + player.getUniqueId());
                }
                return;
            }

            // Cancel countdown if player takes damage
            if (Ascend.instance.getPlayerManager().hasCountdownTask(player.getUniqueId())) {
                Ascend.instance.debug("Player " + player.getUniqueId() + " took damage during countdown");
                Ascend.instance.getPlayerManager().cancelCountdown(player.getUniqueId());
                String message = "§cCountdown cancelled due to damage!";
                Ascend.instance.debugPlayerMessage(player, message);
            }
        }
    }
} 