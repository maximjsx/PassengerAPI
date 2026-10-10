package com.maximde.passengerapi;

import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
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
        debug(d -> d.debugRemove(PLUGIN_NAME, targetEntity, 1));
        for (Map<Integer, Set<Integer>> pluginPassengers : passengersHashmap.values()) {
            if (pluginPassengers == null) continue;
            Set<Integer> passengers = pluginPassengers.get(targetEntity);
            if (passengers == null) return;
            passengers.remove(passengerID);
            if (passengers.isEmpty()) {
                pluginPassengers.remove(targetEntity);
            }
        }
        if (sendPackets) sendPassengerPacket(async, targetEntity);
    }

    /**
     * Internal method for the PassengerAPI
     * Don't even try to use it somehow in your own plugin!
     */
    public void removePassengers(boolean async, int[] passengerIDs, boolean sendPackets) {
        Set<Integer> passengerSet = Arrays.stream(passengerIDs).boxed().collect(Collectors.toSet());
        debug(d -> d.debugRemove(PLUGIN_NAME, -1, passengerSet.size()));

        Map<String, Map<Integer, Set<Integer>>> tempHashmap = new HashMap<>(passengersHashmap);
        for (Map.Entry<String, Map<Integer, Set<Integer>>> entry : tempHashmap.entrySet()) {
            Map<Integer, Set<Integer>> pluginMap = entry.getValue();
            // Keep the same concurrent-safe, insertion-ordered types used everywhere else in this class.
            // Rebuilding with a plain HashMap/HashSet here silently made the stored map non-thread-safe
            // and caused ConcurrentModificationException on later concurrent access (GitHub #7).
            Map<Integer, Set<Integer>> newPluginMap = new ConcurrentHashMap<>();

            for (Map.Entry<Integer, Set<Integer>> pluginEntry : pluginMap.entrySet()) {
                int targetEntity = pluginEntry.getKey();
                Set<Integer> passengers = new CopyOnWriteArraySet<>(pluginEntry.getValue());
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
                .collect(Collectors.toCollection(LinkedHashSet::new));

        debug(d -> d.debugAdd(PLUGIN_NAME, targetEntity, passengerSet.size()));

        passengersHashmap.computeIfAbsent(PLUGIN_NAME, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(targetEntity, k -> new CopyOnWriteArraySet<>())
                .addAll(passengerSet);

        if (sendPackets) sendPassengerPacket(async, targetEntity);
    }

    /**
     * Internal method used by the packet interception (auto-detection) path.
     * Stores the intercepted passengers and re-sends the merged list only to the viewer
     * the original (now cancelled) packet was destined for, unless the config disables it.
     */
    public void addInterceptedPassengers(int targetEntity, int[] passengerIDs, Player viewer) {
        Set<Integer> passengerSet = Arrays.stream(passengerIDs)
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        debug(d -> d.debugAdd(PLUGIN_NAME, targetEntity, passengerSet.size()));

        passengersHashmap.computeIfAbsent(PLUGIN_NAME, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(targetEntity, k -> new CopyOnWriteArraySet<>())
                .addAll(passengerSet);

        if (passengerAPI.getPassengerConfig().isResendToViewerOnly()) {
            sendPassengerPacketToViewer(targetEntity, viewer);
        } else {
            sendPassengerPacket(true, targetEntity);
        }
    }


    private class PassengerActionsImpl implements PassengerActions {
        private final String pluginName;

        PassengerActionsImpl(String pluginName) {
            this.pluginName = pluginName;
        }

        @Override
        public void addPassenger(boolean async, int targetEntity, int passengerEntity) {
            debug(d -> d.debugAdd(pluginName, targetEntity, 1));
            passengersHashmap.computeIfAbsent(pluginName, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(targetEntity, k -> new CopyOnWriteArraySet<>())
                    .add(passengerEntity);
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void addPassengers(boolean async, int targetEntity, @NotNull Set<Integer> passengerIDs) {
            debug(d -> d.debugAdd(pluginName, targetEntity, passengerIDs.size()));
            passengersHashmap.computeIfAbsent(pluginName, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(targetEntity, k -> new CopyOnWriteArraySet<>())
                    .addAll(passengerIDs);
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void addPassengers(boolean async, int targetEntity, int[] passengerIDs) {
            Set<Integer> passengerSet = Arrays.stream(passengerIDs)
                    .boxed()
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            debug(d -> d.debugAdd(pluginName, targetEntity, passengerSet.size()));
            passengersHashmap.computeIfAbsent(pluginName, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(targetEntity, k -> new CopyOnWriteArraySet<>())
                    .addAll(passengerSet);

            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public void removePassenger(boolean async, int targetEntity, int passengerID) {
            debug(d -> d.debugRemove(pluginName, targetEntity, 1));
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
            debug(d -> d.debugRemove(pluginName, -1, 1));

            Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
            if (pluginPassengers == null) return;
            pluginPassengers.values().forEach(passengers -> passengers.remove(passengerID));
            sendPassengerPackets(async, pluginName);
        }

        @Override
        public void removePassengers(boolean async, int targetEntity, @NotNull Set<Integer> passengerIDs) {
            debug(d -> d.debugRemove(pluginName, targetEntity, passengerIDs.size()));
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

            debug(d -> d.debugRemove(pluginName, targetEntity, passengerSet.size()));
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
            debug(d -> d.debugRemove(pluginName, -1, passengerIDs.size()));

            passengersHashmap.keySet().forEach(key -> {
                Map<Integer, Set<Integer>> pluginMap = passengersHashmap.get(key);
                if (pluginMap == null) return;
                pluginMap.forEach((target, passengers) -> {
                    passengers.removeAll(passengerIDs);
                    if (passengers.isEmpty()) {
                        pluginMap.remove(target);
                    }
                });
                if (pluginMap.isEmpty()) {
                    passengersHashmap.remove(key);
                }
            });

            sendPassengerPackets(async, pluginName);
        }

        @Override
        public void removeAllPassengers(boolean async, int targetEntity) {
            Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
            if (pluginPassengers == null) return;
            Set<Integer> current = pluginPassengers.get(targetEntity);
            debug(d -> d.debugRemove(pluginName, targetEntity, current == null ? 0 : current.size()));
            pluginPassengers.remove(targetEntity);
            sendPassengerPacket(async,targetEntity);
        }

        @Override
        public Set<Integer> getPassengers(boolean async, int targetEntity) {
            Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
            if (pluginPassengers == null) return Set.of();
            Set<Integer> passengers = pluginPassengers.get(targetEntity);
            return passengers != null ? new LinkedHashSet<>(passengers) : Set.of();
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
            Set<Integer> allPassengers = new LinkedHashSet<>();
            passengersHashmap.values().forEach(map -> {
                Set<Integer> passengers = map.get(targetEntity);
                if (passengers != null) {
                    allPassengers.addAll(passengers);
                }
            });
            return allPassengers;
        }


    }

    private void debug(java.util.function.Consumer<com.maximde.passengerapi.debugger.DebugEvents> action) {
        com.maximde.passengerapi.debugger.DebugEvents debugEvents = passengerAPI.getDebugEvents();
        if (debugEvents != null) action.accept(debugEvents);
    }

    /**
     * Builds the merged passenger list for a target entity across all plugins.
     * The internal (auto-detected) passengers are kept first to preserve the
     * server-side seat order (e.g. the boat/minecart driver).
     */
    private int[] computeMergedPassengers(int targetEntity) {
        Set<Integer> allPassengersList = new LinkedHashSet<>();

        Map<Integer, Set<Integer>> internalMap = passengersHashmap.get(PLUGIN_NAME);
        if (internalMap != null) {
            Set<Integer> internalPassengers = internalMap.get(targetEntity);
            if (internalPassengers != null) {
                allPassengersList.addAll(internalPassengers);
            }
        }

        passengersHashmap.forEach((plugin, map) -> {
            if (!plugin.equals(PLUGIN_NAME)) {
                Set<Integer> passengers = map.get(targetEntity);
                if (passengers != null) {
                    allPassengersList.addAll(passengers);
                }
            }
        });

        allPassengersList.removeIf(Objects::isNull);
        return allPassengersList.stream().mapToInt(Integer::intValue).toArray();
    }

    private void sendPassengerPackets(boolean async) {
        passengersHashmap.keySet().forEach(key -> sendPassengerPackets(async, key));
    }

    private void sendPassengerPackets(boolean async, String pluginName) {
        Map<Integer, Set<Integer>> pluginPassengers = passengersHashmap.get(pluginName);
        if (pluginPassengers == null) return;
        pluginPassengers.keySet().forEach(entity -> sendPassengerPacket(async, entity));
    }

    private void sendPassengerPacket(boolean async, int targetEntity) {
        int[] allPassengersArray = computeMergedPassengers(targetEntity);
        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(targetEntity, allPassengersArray);

        Collection<? extends Player> receivers = Bukkit.getOnlinePlayers();
        debug(d -> d.debugPacket(targetEntity, allPassengersArray.length, receivers.size()));
        for (Player player : receivers) {
            this.playerManager.sendPacketSilently(player, packet);
        }
    }

    /**
     * Re-sends the merged passenger list only to a single viewer.
     * Used by the interception path so we don't broadcast every intercepted
     * SET_PASSENGERS packet to every online player.
     */
    private void sendPassengerPacketToViewer(int targetEntity, Player viewer) {
        if (viewer == null) return;
        int[] allPassengersArray = computeMergedPassengers(targetEntity);
        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(targetEntity, allPassengersArray);
        debug(d -> d.debugPacket(targetEntity, allPassengersArray.length, 1));
        this.playerManager.sendPacketSilently(viewer, packet);
    }
}
