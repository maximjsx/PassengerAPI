package com.maximde.passengerapi.listeners;

import com.maximde.passengerapi.PassengerAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.spigotmc.event.entity.EntityMountEvent;

public record EntityMountListener(PassengerAPI passengerAPI) implements Listener {
    @EventHandler
    public void onEntityMount(EntityMountEvent event) {
        //getMount() -> The entity which will be ridden.
        if(event.getEntity() instanceof Player player) passengerAPI.getVehicles().put(player, event.getMount().getEntityId());
    }
}
