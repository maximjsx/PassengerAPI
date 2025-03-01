<div align="center">
<a href="https://discord.gg/2UTkYj26B4" target="_blank"><img src="https://img.shields.io/badge/Discord_Server-7289DA?style=flat&logo=discord&logoColor=white" alt="Join Discord Server" style="border-radius: 15px; height: 20px;"></a>  
</div>

[![Card](https://fancy-readme-stats.vercel.app/api/pin-wide/?username=maximjsx&repo=PassengerAPI&dark_bg=3&theme=snow&height=150)](https://github.com/max1mde/fancy-readme-stats)


# PassengerAPI
PassengerAPI is a Java API that allows you to manage passengers (entities attached to other entities) for multiple plugins.
It works as a plugin and provides an easy-to-use interface for adding, removing, and retrieving passengers for any entity,
regardless of whether the entity exists or not (NPC's, packet based entities...).

# Why Use PassengerAPI?
It solves compatibility issues that can occur when different plugins create entities  
**by sending packets** to players and setting them as passengers.  
This can lead to conflicts and unintended behavior like **unmounting of previous set passengers** by other plugins.

For example this makes these plugins automatically compatible with each other:
- Better Chat Bubbles
- ProdigyCape
- VanillaMinimaps
- PlayerMounts

# Showcase

https://github.com/max1mde/PassengerAPI/assets/114857048/224a9df1-3b22-4176-bfce-40a555fc71a2



> [!IMPORTANT]  
> This plugin **works out-of-the-box!**  
> Just put it into your plugins folder and restart your server.  
> _It should fix most entity passenger compatibility bugs with other plugins_
>
> BUT if you are a **developer** you can still add/access/remove passengers using this API (Not recommended)!  
> For example if you want to remove a passenger from an entity, without killing it, this could be usefull.


# Commmands:

Permission `passengerapi.commands`

```
/passengerapi debug 
/passengerapi reload
```
> [!TIP]   
> When you are in debug mode and holding a block in your hand
> you will get additional debugging in chat.

# Config:

```yml
# DO NOT TOUCH ANYTHING IN THIS FILE
# IF YOU ARE NOT 100% SURE WHAT YOU ARE DOING!

AutoPassengerDetection:
  SetPassengerPacket: true
  EntityDestroyPacket: true
  VehicleExitEvent: true
  VehicleExitPacket: true
```


# Getting Started

1. Add PassengerAPI as a compile-only dependency to your plugin.

Gradle:
```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
    maven { url 'https://maven.evokegames.gg/snapshots' }
}

dependencies {
    implementation 'com.github.maximjsx:PassengerAPI:<VERSION>'
}
```
(More information: https://jitpack.io/#maximjsx/PassengerAPI)  

2. Add the following line to your `plugin.yml` file:
```
depend:
- PassengerAPI
```

Also add the plugin [Packet Events](https://www.spigotmc.org/resources/packetevents-api.80279/) to your server
which is required by this plugin!

# Usage

### Obtaining the PassengerActions Instance

To access the PassengerAPI functionality, you need to obtain the PassengerActions instance:

```java
PassengerActions passengerActions = PassengerAPI.getAPI(yourPluginInstance);
```

> [!IMPORTANT]  
> Do not use `getAPI()` before your onEnable() was called!
>

Replace yourPluginInstance with the instance of your plugin's main class.  
(For example with `this` if you use it in your main class)

### Managing Passengers

Here are some examples of how to use the `PassengerActions` interface:

Keep in mind that you can only retrive and remove passengers, which you have set here by using the addPassenger... methods.  
**Except:** when using the "global" methods like getGlobalPassengers.

Only use the addPassenger.. methods from the api if your entity does not actually exist (Packet based)
Try using Entity#addPassengers() instead if its a real entity... PassengerAPI will handle that automatically!

```java
// Add a single passenger
passengerActions.addPassenger(<async>, targetEntityId, passengerEntityId);

// Add multiple passengers
passengerActions.addPassengers(<async>, targetEntityId, Set.of(passenger1Id, passenger2Id, ...));

// Remove a single passenger
passengerActions.removePassenger(<async>, targetEntityId, passengerEntityId);

// Remove multiple passengers
passengerActions.removePassengers(<async>, targetEntityId, Set.of(passenger1Id, passenger2Id, ...));

// Remove all passengers for a target entity
passengerActions.removeAllPassengers(<async>, targetEntityId);

// Get all passengers for a target entity
Set<Integer> passengers = passengerActions.getPassengers(targetEntityId);

// Remove global passengers (passengers set by all plugins)
passengerActions.removeGlobalPassengers(<async>, targetEntityId, Set.of(passenger1Id, passenger2Id, ...));

// Remove all global passengers for a target entity (passengers set by all plugins)
passengerActions.removeAllGlobalPassengers(<async>, targetEntityId);

// Get all global passengers for a target entity (passengers set by all plugins)
Set<Integer> globalPassengers = passengerActions.getGlobalPassengers(targetEntityId);
```

> [!NOTE]   
> All entities are identified by their entity ID (not UUID).

### Listening to events

PassengerAPI also provides events that you can listen to and handle accordingly:

```java
@EventHandler
public void onAddPassenger(AsyncAddPassengerEvent event) {
    // The name of the plugin which tries to add these passengers
    String pluginName = event.getPluginName();
    int targetEntity = event.getTargetEntityID();
    Set<Integer> passengers = event.getPassengerList();
}

@EventHandler
public void onRemovePassenger(AsyncRemovePassengerEvent event) {
    String pluginName = event.getPluginName();
    int targetEntity = event.getTargetEntityID();
    Set<Integer> removedPassengers = event.getPassengerList();
    // Perform actions with these properties
}

@EventHandler
public void onPassengerPacket(AsyncPassengerPacketEvent event) {
    int targetEntity = event.getTargetEntityID();
    Set<Integer> passengers = event.getPassengerList();
    // Which players should receive the packet (You can modify that list)
    List<Player> receivers = event.getPacketReceivers();
}
```
Don't forget to register your event class 


# Contributions are welcome!
