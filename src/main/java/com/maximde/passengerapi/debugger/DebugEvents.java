package com.maximde.passengerapi.debugger;

import com.maximde.passengerapi.PassengerAPI;
import com.maximde.passengerapi.PassengerManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;

public class DebugEvents implements Listener {
    private final Set<Player> debugPlayers = new HashSet<>();
    private final PassengerAPI passengerAPI;

    public DebugEvents(PassengerAPI passengerAPI) {
        this.passengerAPI = passengerAPI;
        startActionBarTask();
    }

    public void toggleDebugMode(Player player) {
        if (debugPlayers.contains(player)) {
            debugPlayers.remove(player);
            player.sendMessage(ChatColor.GREEN + "Debug mode disabled.");
            return;
        }
        debugPlayers.add(player);
        player.sendMessage(ChatColor.GREEN + "Debug mode enabled.");
    }

    private void startActionBarTask() {
        passengerAPI.getServer().getScheduler().runTaskTimer(this.passengerAPI, this::updateActionBar, 0, 20);
    }

    private void updateActionBar() {
        if (debugPlayers.isEmpty()) return;

        PassengerManager passengerManager = passengerAPI.getPassengerManager();
        StringBuilder actionBarText = new StringBuilder();

        actionBarText.append(ChatColor.DARK_GREEN + "Plugins Using PassengerAPI: " + ChatColor.WHITE)
                .append(passengerManager.getPassengersHashmap().keySet().size())
                .append(ChatColor.DARK_GREEN + " Total Passengers: " + ChatColor.WHITE)
                .append(passengerManager.getTotalPassengersCount())
                .append(ChatColor.DARK_GREEN + " Total Target Entities: " + ChatColor.WHITE)
                .append(passengerManager.getTotalTargetEntitiesCount());


        for (Player player : debugPlayers) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarText.toString()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        debugPlayers.remove(event.getPlayer());
    }

    /*
     * These hooks replace the former AsyncAddPassengerEvent / AsyncRemovePassengerEvent /
     * AsyncPassengerPacketEvent Bukkit events. They are called directly from the PassengerManager
     * and are effectively free when nobody is in debug mode (single isEmpty() check), avoiding the
     * cost of firing a Bukkit event for every passenger packet on the Netty thread.
     */

    public void debugAdd(String pluginName, int targetEntity, int passengerCount) {
        if (debugPlayers.isEmpty()) return;
        sendDebug(ChatColor.WHITE + "AddPassenger: Plugin=" + pluginName +
                ", TargetEntityID=" + targetEntity + ", PassengersAmount=" + passengerCount);
    }

    public void debugRemove(String pluginName, int targetEntity, int passengerCount) {
        if (debugPlayers.isEmpty()) return;
        sendDebug(ChatColor.WHITE + "RemovePassenger: Plugin=" + pluginName +
                ", TargetEntityID=" + targetEntity + ", PassengersAmount=" + passengerCount);
    }

    public void debugPacket(int targetEntity, int passengerCount, int receiverCount) {
        if (debugPlayers.isEmpty()) return;
        sendDebug(ChatColor.LIGHT_PURPLE + "PassengerPacket: TargetEntityID=" + targetEntity +
                ", PassengersAmount=" + passengerCount + ", PacketReceiversAmount=" + receiverCount);
    }

    private void sendDebug(String message) {
        // These hooks can be invoked from the Netty thread, so touch the Bukkit player API on the main thread.
        if (passengerAPI.getServer().isPrimaryThread()) {
            dispatchDebug(message);
        } else {
            passengerAPI.getServer().getScheduler().runTask(passengerAPI, () -> dispatchDebug(message));
        }
    }

    private void dispatchDebug(String message) {
        for (Player player : debugPlayers) {
            if (player.getItemInHand().getType() == Material.AIR) continue;
            player.sendMessage(ChatColor.DARK_GREEN + "[" + ChatColor.YELLOW + "Debug" + ChatColor.DARK_GREEN + "] " + message);
        }
    }

}
