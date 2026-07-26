package com.maximde.passengerapi;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PassengerManagerTest {

    @Test
    void copyWithoutPassengersKeepsSharedContainersConcurrent() {
        Map<Integer, Set<Integer>> pluginMap = new HashMap<>();
        pluginMap.put(10, Set.of(20, 21, 22));
        pluginMap.put(11, Set.of(20));

        Map<Integer, Set<Integer>> result = PassengerManager.copyWithoutPassengers(pluginMap, Set.of(20, 22));

        assertInstanceOf(ConcurrentHashMap.class, result);
        assertEquals(Set.of(21), result.get(10));
        assertInstanceOf(ConcurrentHashMap.KeySetView.class, result.get(10));
        assertFalse(result.containsKey(11));
    }
}
