package com.maximde.passengerapi;

import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.maximde.passengerapi.events.AsyncAddPassengerEvent;
import com.maximde.passengerapi.events.AsyncPassengerPacketEvent;
import com.maximde.passengerapi.events.AsyncRemovePassengerEvent;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PassengerManager {
    /**
     * String -> Plugin name
     * Integer -> Passenger entity ID
     * List<Integer> -> The passenger ID's for this entity
     */
    @Getter
    private final Map<String, Map<Integer, Set<Integer>>> passengersHashmap = new ConcurrentHashMap<>();
    private final PlayerManager playerManager;
    private final String PLUGIN_NAME = "PassengerAPI (Internal)";
    private final PassengerAPI passengerAPI;

    public PassengerManager(PlayerManager playerManager, PassengerAPI passengerAPI) {
        this.playerManager = playerManager;
        this.passengerAPI = passengerAPI;
    }

    public PassengerActions initActions(JavaPlugin plugin) {
        String pluginName = plugin.getDescription().getName();
        return new PassengerActionsImpl(pluginName);
    }

    public int getTotalPassengersCount() {
        return passengersHashmap.values().stream()
                .flatMap(map -> map.values().stream())
                .mapToInt(Set::size)
                .sum();
    }

    public int getTotalTargetEntitiesCount() {
        return passengersHashmap.values().stream()
                .flatMap(map -> map.keySet().stream())
                .collect(Collectors.toSet())
                .size();
    }

    public void removePassenger(boolean async, int targetEntity, int passengerID, boolean sendPackets) {
        AsyncRemovePassengerEvent removePassengerEvent = new AsyncRemovePassengerEvent(async, targetEntity, Set.of(passengerID), PLUGIN_NAME);
        Bukkit.getPluginManager().callEvent(removePassengerEvent);
        if (removePassengerEvent.isCancelled()) return;
        for (Map<Integer, Set<Integer>> pluginPassengers : passengersHashmap.values()) {
            if (pluginPassengers == null) continue;
            Set<Integer> passengers = pluginPassengers.get(targetEntity);
            if (passengers == null) return;
            passengers.remove(passengerID);
            if (passengers.isEmpty()) {
                pluginPassengers.remove(targetEntity);
            }
        }
        if (sendPackets) sendPassengerPacket(async,targetEntity);
    }

    /**
     * Internal method for the PassengerAPI
     * Don't even try to use it somehow in your own plugin!
     */
    public void removePassengers(boolean async, int[] passengerIDs, boolean sendPackets) {
            Set<Integer> passengerSet = Arrays.stream(passengerIDs).boxed().collect(Collectors.toSet());
            AsyncRemovePassengerEvent removePassengerEvent = new AsyncRemovePassengerEvent(async,-1, passengerSet, PLUGIN_NAME);
            Bukkit.getPluginManager().callEvent(removePassengerEvent);
            if (removePassengerEvent.isCancelled()) return;

            Map<String, Map<Integer, Set<Integer>>> tempHashmap = new HashMap<>(passengersHashmap);
            for (Map.Entry<String, Map<Integer, Set<Integer>>> entry : tempHashmap.entrySet()) {
                Map<Integer, Set<Integer>> pluginMap = entry.getValue();
                Map<Integer, Set<Integer>> newPluginMap = new HashMap<>();

                for (Map.Entry<Integer, Set<Integer>> pluginEntry : pluginMap.entrySet()) {
                    int targetEntity = pluginEntry.getKey();
                    Set<Integer> passengers = new HashSet<>(pluginEntry.getValue());
                    passengers.removeAll(passengerSet);
                    if (!passengers.isEmpty()) {
                        newPluginMap.put(targetEntity, passengers);
                    }
                }

                if (!newPluginMap.isEmpty()) {
                    passengersHashmap.put(entry.getKey(), newPluginMap);
                } else {
                    passengersHashmap.remove(entry.getKey());
                }
            }
            if (sendPackets) sendPassengerPackets(false);
    }

    /**
     * Internal method for the PassengerAPI
     * Don't even try to use it somehow in your own plugin!
     */
    public void addPassengers(boolean async, int targetEntity, int[] passengerIDs, boolean sendPackets) {
            Set<Integer> passengerSet = Arrays.stream(passengerIDs)
                    .boxed()
                    .collect(Collectors.toSet());

            AsyncAddPassengerEvent addPassengerEvent = new AsyncAddPassengerEvent(async, targetEntity, passengerSet, PLUGIN_NAME);
            Bukkit.getPluginManager().callEvent(addPassengerEvent);
            if (addPassengerEvent.isCancelled()) return;
            passengersHashmap.computeIfAbsent(PLUGIN_NAME, k -> new HashMap<>())
                    .computeIfAbsent(targetEntity, k -> new HashSet<>())
                    .addAll(passengerSet);

            if (sendPackets) sendPassengerPacket(async, targetEntity);
    }


    private class PassengerActionsImpl implements PassengerActions {
        private final String pluginName;

        PassengerActionsImpl(String pluginName) {
            this.pluginName = pluginName;
        }

        @Override
        public void addPassenger(boolean async, int targetEntity, int passengerEntity) {
            AsyncAddPassengerEvent addPassengerEvent = new AsyncAddPassengerEvent(async, targetEntity, Set.of(passengerEntity), pluginName);
            Bukkit.getPluginManager().callEvent(addPassengerEvent);
            if (addPassengerEvent.isCancelled()) return;
            passengersHashmap.computeIfAbsent(pluginName, k -> new HashMap<>())
                    .computeIfAbsent(targetEntity, k -> new HashSet<>())
                    .add(passengerEntity);
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void addPassengers(boolean async, int targetEntity, @NotNull Set<Integer> passengerIDs) {
            AsyncAddPassengerEvent addPassengerEvent = new AsyncAddPassengerEvent(async, targetEntity, passengerIDs, pluginName);
            Bukkit.getPluginManager().callEvent(addPassengerEvent);
            if (addPassengerEvent.isCancelled()) return;
            passengersHashmap.computeIfAbsent(pluginName, k -> new HashMap<>())
                    .computeIfAbsent(targetEntity, k -> new HashSet<>())
                    .addAll(passengerIDs);
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void addPassengers(boolean async, int targetEntity, int[] passengerIDs) {
            Set<Integer> passengerSet = Arrays.stream(passengerIDs)
                    .boxed()
                    .collect(Collectors.toSet());

            AsyncAddPassengerEvent addPassengerEvent = new AsyncAddPassengerEvent(async, targetEntity, passengerSet, pluginName);
            Bukkit.getPluginManager().callEvent(addPassengerEvent);
            if (addPassengerEvent.isCancelled()) return;
            passengersHashmap.computeIfAbsent(pluginName, k -> new HashMap<>())
                    .computeIfAbsent(targetEntity, k -> new HashSet<>())
                    .addAll(passengerSet);

            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void removePassenger(boolean async, int targetEntity, int passengerID) {
            AsyncRemovePassengerEvent removePassengerEvent = new AsyncRemovePassengerEvent(async, targetEntity, Set.of(passengerID), pluginName);
            Bukkit.getPluginManager().callEvent(removePassengerEvent);
            if (removePassengerEvent.isCancelled()) return;
            Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
            if (pluginPassengers == null) return;
            Set<Integer> passengers = pluginPassengers.get(targetEntity);
            if (passengers == null) return;
            passengers.remove(passengerID);
            if (passengers.isEmpty()) {
                pluginPassengers.remove(targetEntity);
            }
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void removePassenger(boolean async, int passengerID) {
            AsyncRemovePassengerEvent removePassengerEvent = new AsyncRemovePassengerEvent(async, -1, Set.of(passengerID), pluginName);
            Bukkit.getPluginManager().callEvent(removePassengerEvent);
            if (removePassengerEvent.isCancelled()) return;

            passengersHashmap.get(pluginName).values().forEach(passengers -> passengers.remove(passengerID));
            sendPassengerPackets(async, pluginName);
        }

        @Override
        public void removePassengers(boolean async, int targetEntity, @NotNull Set<Integer> passengerIDs) {
            AsyncRemovePassengerEvent removePassengerEvent = new AsyncRemovePassengerEvent(async, targetEntity, passengerIDs, pluginName);
            Bukkit.getPluginManager().callEvent(removePassengerEvent);
            if (removePassengerEvent.isCancelled()) return;
            Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
            if (pluginPassengers == null) return;
            Set<Integer> passengers = pluginPassengers.get(targetEntity);
            if (passengers == null) return;
            passengers.removeAll(passengerIDs);
            if (passengers.isEmpty()) {
                pluginPassengers.remove(targetEntity);
            }
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void removePassengers(boolean async, int targetEntity, int[] passengerIDs) {
            Set<Integer> passengerSet = Arrays.stream(passengerIDs)
                    .boxed()
                    .collect(Collectors.toSet());

            AsyncRemovePassengerEvent removePassengerEvent = new AsyncRemovePassengerEvent(async, targetEntity, passengerSet, pluginName);
            Bukkit.getPluginManager().callEvent(removePassengerEvent);
            if (removePassengerEvent.isCancelled()) return;
            Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
            if (pluginPassengers == null) return;
            Set<Integer> passengers = pluginPassengers.get(targetEntity);
            if (passengers == null) return;
            passengers.removeAll(passengerSet);
            if (passengers.isEmpty()) {
                pluginPassengers.remove(targetEntity);
            }
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void removePassengers(boolean async, int[] passengerIDs) {
            Set<Integer> passengerSet = Arrays.stream(passengerIDs).boxed().collect(Collectors.toSet());
            removePassengers(async, passengerSet);
        }

        @Override
        public void removePassengers(boolean async, @NotNull Set<Integer> passengerIDs) {
            AsyncRemovePassengerEvent removePassengerEvent = new AsyncRemovePassengerEvent(async, -1, passengerIDs, pluginName);
            Bukkit.getPluginManager().callEvent(removePassengerEvent);
            if (removePassengerEvent.isCancelled()) return;

            passengersHashmap.keySet().forEach(key -> {
                passengersHashmap.get(key).values().forEach(passengers -> passengers.removeAll(passengerIDs));
                passengersHashmap.get(key).forEach((target, passengers) -> {
                    passengers.removeAll(passengerIDs);
                    if (passengersHashmap.get(key).get(target).isEmpty()) {
                        passengersHashmap.get(key).remove(target);
                    }
                });
                if (passengersHashmap.get(key).isEmpty()) {
                    passengersHashmap.remove(key);
                }
            });

            sendPassengerPackets(async, pluginName);
        }

        @Override
        public void removeAllPassengers(boolean async, int targetEntity) {
            Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
            if (pluginPassengers == null) return;
            AsyncRemovePassengerEvent removePassengerEvent = new AsyncRemovePassengerEvent(async, targetEntity, pluginPassengers.get(targetEntity), pluginName);
            Bukkit.getPluginManager().callEvent(removePassengerEvent);
            if (removePassengerEvent.isCancelled()) return;
            pluginPassengers.remove(targetEntity, null);
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public Set<Integer> getPassengers(boolean async, int targetEntity) {
            Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
            if (pluginPassengers == null) return Set.of();
            Set<Integer> passengers = pluginPassengers.get(targetEntity);
            return passengers != null ? new HashSet<>(passengers) : Set.of();
        }


        @Override
        public void removeGlobalPassengers(boolean async, int targetEntity, @NotNull Set<Integer> passengerIDs) {
            passengersHashmap.values().forEach(map -> {
                Set<Integer> passengers = map.get(targetEntity);
                if (passengers != null) {
                    passengers.removeAll(passengerIDs);
                }
            });
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void removeAllGlobalPassengers(boolean async, int targetEntity) {
            passengersHashmap.values().forEach(map -> map.remove(targetEntity));
            sendPassengerPacket(async, targetEntity);
        }


        @Override
        public Set<Integer> getGlobalPassengers(boolean async, int targetEntity) {
            Set<Integer> allPassengers = new HashSet<>();
            passengersHashmap.values().forEach(map -> {
                Set<Integer> passengers = map.get(targetEntity);
                if (passengers != null) {
                    allPassengers.addAll(passengers);
                }
            });
            return allPassengers;
        }


    }

    private void sendPassengerPackets(boolean async) {
        passengersHashmap.keySet().forEach(key -> sendPassengerPackets(async, key));
    }

    private void sendPassengerPackets(boolean async, String pluginName) {
        passengersHashmap.get(pluginName).keySet().forEach(entity -> sendPassengerPacket(async, entity));
    }

    private void sendPassengerPacket(boolean async, int targetEntity) {
        Set<Integer> allPassengersList = passengersHashmap.values().stream()
                .filter(map -> map.get(targetEntity) != null)
                .flatMap(map -> map.get(targetEntity).stream().filter(Objects::nonNull))
                .collect(Collectors.toSet());

        int[] allPassengersArray = allPassengersList.stream().mapToInt(Integer::intValue).toArray();

        List<Player> receivers = new ArrayList<>(Bukkit.getOnlinePlayers());
        AsyncPassengerPacketEvent passengerPacketEvent = new AsyncPassengerPacketEvent(async, targetEntity, allPassengersList, receivers);
        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(targetEntity, allPassengersArray);
        Bukkit.getPluginManager().callEvent(passengerPacketEvent);
        passengerPacketEvent.getPacketReceivers().forEach(player -> {

            this.playerManager.sendPacketSilently(player, packet);
        });
    }
}
