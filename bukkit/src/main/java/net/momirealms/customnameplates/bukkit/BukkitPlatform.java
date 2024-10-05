/*
 *  Copyright (C) <2024> <XiaoMoMi>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.momirealms.customnameplates.bukkit;

import it.unimi.dsi.fastutil.ints.IntList;
import me.clip.placeholderapi.PlaceholderAPI;
import net.momirealms.customnameplates.api.CNPlayer;
import net.momirealms.customnameplates.api.ConfigManager;
import net.momirealms.customnameplates.api.CustomNameplates;
import net.momirealms.customnameplates.api.Platform;
import net.momirealms.customnameplates.api.feature.actionbar.ActionBarManagerImpl;
import net.momirealms.customnameplates.api.feature.bossbar.BossBar;
import net.momirealms.customnameplates.api.helper.AdventureHelper;
import net.momirealms.customnameplates.api.helper.VersionHelper;
import net.momirealms.customnameplates.api.network.PacketEvent;
import net.momirealms.customnameplates.api.placeholder.DummyPlaceholder;
import net.momirealms.customnameplates.api.placeholder.Placeholder;
import net.momirealms.customnameplates.api.util.Alignment;
import net.momirealms.customnameplates.api.util.Vector3;
import net.momirealms.customnameplates.bukkit.util.EntityData;
import net.momirealms.customnameplates.bukkit.util.Reflections;
import net.momirealms.customnameplates.common.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

public class BukkitPlatform implements Platform {

    private final CustomNameplates plugin;
    private final boolean placeholderAPI;
    private final boolean geyser;
    private final boolean floodGate;
    private final boolean libsDisguises;

    private static final HashMap<String, TriConsumer<CNPlayer, PacketEvent, Object>> packetFunctions = new HashMap<>();

    private static void registerPacketConsumer(final TriConsumer<CNPlayer, PacketEvent, Object> functions, String... packet) {
        for (String s : packet) {
            packetFunctions.put(s, functions);
        }
    }

    public boolean hasPlaceholderAPI() {
        return placeholderAPI;
    }

    public boolean hasGeyser() {
        return geyser;
    }

    public boolean hasFloodGate() {
        return floodGate;
    }

    public boolean hasLibsDisguises() {
        return libsDisguises;
    }

    public BukkitPlatform(CustomNameplates plugin) {
        this.plugin = plugin;
        this.placeholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        this.geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null;
        this.floodGate = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        this.libsDisguises = Bukkit.getPluginManager().getPlugin("LibsDisguises") != null;
    }

    static {
        registerPacketConsumer((player, event, packet) -> {
            if (!ConfigManager.actionbarModule()) return;
            if (!ConfigManager.catchOtherActionBar()) return;
            try {
                Object component = Reflections.field$ClientboundSetActionBarTextPacket$text.get(packet);
                Object contents = Reflections.method$Component$getContents.invoke(component);
                if (Reflections.clazz$ScoreContents.isAssignableFrom(contents.getClass())) {
                    String name = (String) Reflections.field$ScoreContents$name.get(contents);
                    String objective = (String) Reflections.field$ScoreContents$objective.get(contents);
                    if (name.equals("np") && objective.equals("ab")) return;
                }
                CustomNameplates.getInstance().getScheduler().async().execute(() -> {
                    ((ActionBarManagerImpl) CustomNameplates.getInstance().getActionBarManager()).handleActionBarPacket(player, AdventureHelper.minecraftComponentToMiniMessage(component));
                });
            } catch (ReflectiveOperationException e) {
                CustomNameplates.getInstance().getPluginLogger().severe("Failed to handle ClientboundSetActionBarTextPacket", e);
            }
            event.cancelled(true);
        }, "ClientboundSetActionBarTextPacket");

        registerPacketConsumer((player, event, packet) -> {
            if (!ConfigManager.actionbarModule()) return;
            if (!ConfigManager.catchOtherActionBar()) return;
            try {
            boolean actionBar = (boolean) Reflections.field$ClientboundSystemChatPacket$overlay.get(packet);
                if (actionBar) {
                    CustomNameplates.getInstance().getScheduler().async().execute(() -> {
                        try {
                            if (VersionHelper.isVersionNewerThan1_20_4()) {
                                // 1.20.4+
                                Object component = Reflections.field$ClientboundSystemChatPacket$component.get(packet);
                                if (component == null) return;
                                ((ActionBarManagerImpl) CustomNameplates.getInstance().getActionBarManager()).handleActionBarPacket(player, AdventureHelper.minecraftComponentToMiniMessage(component));
                            } else {
                                // 1.20.4-
                                String json = (String) Reflections.field$ClientboundSystemChatPacket$text.get(packet);
                                if (json == null) return;
                                ((ActionBarManagerImpl) CustomNameplates.getInstance().getActionBarManager()).handleActionBarPacket(player, AdventureHelper.jsonToMiniMessage(json));
                            }
                        } catch (ReflectiveOperationException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    event.cancelled(true);
                }
            } catch (ReflectiveOperationException e) {
                CustomNameplates.getInstance().getPluginLogger().severe("Failed to handle ClientboundSystemChatPacket", e);
            }
        }, "ClientboundSystemChatPacket");

        // 1.20.2+
        registerPacketConsumer((player, event, packet) -> {
            if (!VersionHelper.isVersionNewerThan1_20_2()) return;
            try {
                int entityID = (int) Reflections.field$ClientboundAddEntityPacket$entityId.get(packet);
                CNPlayer added = CustomNameplates.getInstance().getPlayer(entityID);
                if (added != null) {
                    added.addPlayerToTracker(player);
                    CustomNameplates.getInstance().getUnlimitedTagManager().onAddPlayer(added, player);
                }
            } catch (ReflectiveOperationException e) {
                CustomNameplates.getInstance().getPluginLogger().severe("Failed to handle ClientboundAddEntityPacket", e);
            }
        }, "ClientboundAddEntityPacket", "PacketPlayOutSpawnEntity");

        // 1.19.4-1.20.1
        registerPacketConsumer((player, event, packet) -> {
            if (VersionHelper.isVersionNewerThan1_20_2()) return;
            try {
                int entityID = (int) Reflections.field$PacketPlayOutNamedEntitySpawn$entityId.get(packet);
                CNPlayer added = CustomNameplates.getInstance().getPlayer(entityID);
                if (added != null) {
                    added.addPlayerToTracker(player);
                    CustomNameplates.getInstance().getUnlimitedTagManager().onAddPlayer(added, player);
                }
            } catch (ReflectiveOperationException e) {
                CustomNameplates.getInstance().getPluginLogger().severe("Failed to handle PacketPlayOutNamedEntitySpawn", e);
            }
        }, "PacketPlayOutNamedEntitySpawn");

        registerPacketConsumer((player, event, packet) -> {
            try {
                IntList intList = (IntList) Reflections.field$ClientboundRemoveEntitiesPacket$entityIds.get(packet);
                for (int i : intList) {
                    CNPlayer removed = CustomNameplates.getInstance().getPlayer(i);
                    if (removed != null) {
                        removed.removePlayerFromTracker(player);
                        CustomNameplates.getInstance().getUnlimitedTagManager().onRemovePlayer(removed, player);
                    }
                }
            } catch (ReflectiveOperationException e) {
                CustomNameplates.getInstance().getPluginLogger().severe("Failed to handle ClientboundRemoveEntitiesPacket", e);
            }
        }, "PacketPlayOutEntityDestroy", "ClientboundRemoveEntitiesPacket");

        // for cosmetic plugin compatibility
        registerPacketConsumer((player, event, packet) -> {
            try {
                int[] passengers = (int[]) Reflections.field$ClientboundSetPassengersPacket$passengers.get(packet);
                int vehicle = (int) Reflections.field$ClientboundSetPassengersPacket$vehicle.get(packet);
                CNPlayer another = CustomNameplates.getInstance().getPlayer(vehicle);
                if (another != null) {
                    Set<Integer> otherEntities = player.getTrackedPassengerIds(another);
                    for (int passenger : passengers) {
                        otherEntities.add(passenger);
                    }
                    int[] merged = new int[otherEntities.size()];
                    int index = 0;
                    for (Integer element : otherEntities) {
                        merged[index++] = element;
                    }
                    Reflections.field$ClientboundSetPassengersPacket$passengers.set(packet, merged);
                }
            } catch (ReflectiveOperationException e) {
                CustomNameplates.getInstance().getPluginLogger().severe("Failed to handle ClientboundSetPassengersPacket", e);
            }
        }, "PacketPlayOutMount", "ClientboundSetPassengersPacket");

        registerPacketConsumer((player, event, packet) -> {
            if (!VersionHelper.isVersionNewerThan1_20_5()) return;
            try {
                int entityID = (int) Reflections.field$ClientboundUpdateAttributesPacket$id.get(packet);
                CNPlayer another = CustomNameplates.getInstance().getPlayer(entityID);
                if (another != null) {
                    @SuppressWarnings("unchecked")
                    List<Object> attributes = (List<Object>) Reflections.field$ClientboundUpdateAttributesPacket$attributes.get(packet);
                    for (Object attributeSnapshot : attributes) {
                        Object attributeHolder = Reflections.field$ClientboundUpdateAttributesPacket$AttributeSnapshot$attribute.get(attributeSnapshot);
                        Object attribute = Reflections.method$Holder$value.invoke(attributeHolder);
                        String id = (String) Reflections.field$Attribute$id.get(attribute);
                        if (id.equals("attribute.name.generic.scale")) {
                            double baseValue = (double) Reflections.field$ClientboundUpdateAttributesPacket$AttributeSnapshot$base.get(attributeSnapshot);
                            @SuppressWarnings("unchecked")
                            Collection<Object> modifiers = (Collection<Object>) Reflections.field$ClientboundUpdateAttributesPacket$AttributeSnapshot$modifiers.get(attributeSnapshot);
                            for (Object modifier : modifiers) {
                                double amount = (double) Reflections.field$AttributeModifier$amount.get(modifier);
                                baseValue += amount;
                            }
                            CustomNameplates.getInstance().getUnlimitedTagManager().onPlayerAttributeSet(another, player, baseValue);
                            return;
                        }
                    }
                }
            } catch (ReflectiveOperationException e) {
                CustomNameplates.getInstance().getPluginLogger().severe("Failed to handle ClientboundUpdateAttributesPacket", e);
            }
        }, "ClientboundUpdateAttributesPacket", "PacketPlayOutUpdateAttributes");

        registerPacketConsumer((player, event, packet) -> {
            try {
                int entityID = (int) Reflections.field$ClientboundSetEntityDataPacket$id.get(packet);
                CNPlayer another = CustomNameplates.getInstance().getPlayer(entityID);
                if (another != null) {
                    @SuppressWarnings("unchecked")
                    List<Object> dataValues = (List<Object>) Reflections.field$ClientboundSetEntityDataPacket$packedItems.get(packet);
                    for (Object dataValue : dataValues) {
                        int id = (int) Reflections.field$SynchedEntityData$DataValue$id.get(dataValue);
                        if (id == 0) {
                            byte value = (byte) Reflections.field$SynchedEntityData$DataValue$value.get(dataValue);
                            boolean isCrouching = EntityData.isCrouching(value);
                            CustomNameplates.getInstance().getUnlimitedTagManager().onPlayerDataSet(another, player, isCrouching);
                            return;
                        }
                    }
                }
            } catch (ReflectiveOperationException e) {
                CustomNameplates.getInstance().getPluginLogger().severe("Failed to handle ClientboundSetEntityDataPacket", e);
            }
        }, "ClientboundSetEntityDataPacket", "PacketPlayOutEntityMetadata");
    }

    @Override
    public Object jsonToMinecraftComponent(String json) {
        if (VersionHelper.isVersionNewerThan1_20_5()) {
            try {
                return Reflections.method$Component$Serializer$fromJson.invoke(null, json, Reflections.instance$MinecraftRegistry);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                return Reflections.method$CraftChatMessage$fromJSON.invoke(null, json);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String minecraftComponentToJson(Object component) {
        if (VersionHelper.isVersionNewerThan1_20_5()) {
            try {
                return (String) Reflections.method$Component$Serializer$toJson.invoke(null, component, Reflections.instance$MinecraftRegistry);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                return (String) Reflections.method$CraftChatMessage$toJSON.invoke(null, component);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Placeholder registerPlatformPlaceholder(String id) {
        if (!placeholderAPI) {
            return new DummyPlaceholder(id);
        }
        Placeholder placeholder;
        if (id.startsWith("%rel_")) {
            placeholder = plugin.getPlaceholderManager().registerRelationalPlaceholder(id,
                                                                        // viewer              // owner
                    (p1, p2) -> PlaceholderAPI.setRelationalPlaceholders((Player) p2.player(), (Player) p1.player(), id));
        } else if (id.startsWith("%shared_")) {
            String sub = "%" + id.substring("%shared_".length());
            placeholder =plugin.getPlaceholderManager().registerSharedPlaceholder(id,
                    () -> PlaceholderAPI.setPlaceholders(null, sub));
        } else {
            placeholder = plugin.getPlaceholderManager().registerPlayerPlaceholder(id,
                    (p) -> p == null ? PlaceholderAPI.setPlaceholders(null, id) : PlaceholderAPI.setPlaceholders((OfflinePlayer) p.player(), id));
        }
        return placeholder;
    }

    @Override
    public Object setActionBarTextPacket(Object component) {
        try {
            return Reflections.constructor$ClientboundSetActionBarTextPacket.newInstance(component);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object createBossBarPacket(UUID uuid, Object component, float progress, BossBar.Overlay overlay, BossBar.Color color) {
        try {
            Object barColor = Reflections.method$BossEvent$BossBarColor$valueOf.invoke(null, color.name());
            Object barOverlay = Reflections.method$BossEvent$BossBarOverlay$valueOf.invoke(null, overlay.name());
            Object operationInstance = Reflections.allocateAddOperationInstance();
            Reflections.field$ClientboundBossEventPacket$AddOperation$name.set(operationInstance, component);
            Reflections.field$ClientboundBossEventPacket$AddOperation$progress.set(operationInstance, progress);
            Reflections.field$ClientboundBossEventPacket$AddOperation$color.set(operationInstance, barColor);
            Reflections.field$ClientboundBossEventPacket$AddOperation$overlay.set(operationInstance, barOverlay);
            Reflections.field$ClientboundBossEventPacket$AddOperation$darkenScreen.set(operationInstance, false);
            Reflections.field$ClientboundBossEventPacket$AddOperation$playMusic.set(operationInstance, false);
            Reflections.field$ClientboundBossEventPacket$AddOperation$createWorldFog.set(operationInstance, false);
            return Reflections.constructor$ClientboundBossEventPacket.newInstance(uuid, operationInstance);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object removeBossBarPacket(UUID uuid) {
        try {
            return Reflections.constructor$ClientboundBossEventPacket.newInstance(uuid, Reflections.instance$ClientboundBossEventPacket$REMOVE_OPERATION);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object updateBossBarNamePacket(UUID uuid, Object component) {
        try {
            Object operation = Reflections.constructor$ClientboundBossEventPacket$UpdateNameOperation.newInstance(component);
            return Reflections.constructor$ClientboundBossEventPacket.newInstance(uuid, operation);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Object> createTextDisplayPacket(
            int entityID, UUID uuid,
            Vector3 position, float pitch, float yaw, double headYaw,
            int interpolationDelay, int transformationInterpolationDuration, int positionRotationInterpolationDuration,
            Object component, int backgroundColor, byte opacity,
            boolean hasShadow, boolean isSeeThrough, boolean useDefaultBackgroundColor, Alignment alignment,
            float viewRange, float shadowRadius, float shadowStrength,
            Vector3 scale, Vector3 translation, int lineWidth, boolean isCrouching
    ) {
        try {
            Object addEntityPacket = Reflections.constructor$ClientboundAddEntityPacket.newInstance(
                    entityID, uuid, position.x(), position.y(), position.z(), pitch, yaw,
                    Reflections.instance$EntityType$TEXT_DISPLAY, 0, Reflections.instance$Vec3$Zero, headYaw
            );

            // It's shit code
            ArrayList<Object> values = new ArrayList<>();
            EntityData.InterpolationDelay.addEntityDataIfNotDefaultValue(interpolationDelay,            values);
            if (VersionHelper.isVersionNewerThan1_20_2()) {
                EntityData.PositionRotationInterpolationDuration.addEntityDataIfNotDefaultValue(positionRotationInterpolationDuration, values);
                EntityData.TransformationInterpolationDuration.addEntityDataIfNotDefaultValue(transformationInterpolationDuration, values);
            } else {
                EntityData.InterpolationDuration.addEntityDataIfNotDefaultValue(transformationInterpolationDuration, values);
            }
            EntityData.BillboardConstraints.addEntityDataIfNotDefaultValue((byte) 3,                     values);
            EntityData.BackgroundColor.addEntityDataIfNotDefaultValue(     backgroundColor,              values);
            EntityData.Text.addEntityDataIfNotDefaultValue(                component,                    values);
            EntityData.TextOpacity.addEntityDataIfNotDefaultValue(         isCrouching ? 64 : opacity,   values);
            EntityData.ViewRange.addEntityDataIfNotDefaultValue(           viewRange,                    values);
            EntityData.ShadowRadius.addEntityDataIfNotDefaultValue(        shadowRadius,                 values);
            EntityData.ShadowStrength.addEntityDataIfNotDefaultValue(      shadowStrength,               values);
            EntityData.LineWidth.addEntityDataIfNotDefaultValue(           lineWidth,                    values);
            EntityData.Scale.addEntityDataIfNotDefaultValue(               scale.toVec3(),               values);
            EntityData.Translation.addEntityDataIfNotDefaultValue(         translation.toVec3(),         values);
            EntityData.TextDisplayMasks.addEntityDataIfNotDefaultValue(EntityData.encodeMask(hasShadow, isSeeThrough, useDefaultBackgroundColor, alignment.getId()), values);

            Object setDataPacket = Reflections.constructor$ClientboundSetEntityDataPacket.newInstance(entityID, values);

            return List.of(addEntityPacket, setDataPacket);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Consumer<List<Object>> createInterpolationDelayModifier(int delay) {
        return (values) -> EntityData.InterpolationDelay.addEntityData(delay, values);
    }

    @Override
    public Consumer<List<Object>> createTransformationInterpolationDurationModifier(int duration) {
        if (VersionHelper.isVersionNewerThan1_20_2()) {
            return (values) -> EntityData.TransformationInterpolationDuration.addEntityData(duration, values);
        } else {
            return (values) -> EntityData.InterpolationDuration.addEntityData(duration, values);
        }
    }

    @Override
    public Consumer<List<Object>> createTextComponentModifier(Object component) {
        return (values) -> EntityData.Text.addEntityData(component, values);
    }

    @Override
    public Consumer<List<Object>> createScaleModifier(Vector3 scale) {
        return (values) -> EntityData.Scale.addEntityData(scale.toVec3(), values);
    }

    @Override
    public Consumer<List<Object>> createTranslationModifier(Vector3 translation) {
        return (values) -> EntityData.Translation.addEntityData(translation.toVec3(), values);
    }

    @Override
    public Consumer<List<Object>> createOpacityModifier(byte opacity) {
        return (values) -> EntityData.TextOpacity.addEntityData(opacity, values);
    }

    @Override
    public Object updateTextDisplayPacket(int entityID, List<Consumer<List<Object>>> modifiers) {
        try {
            ArrayList<Object> values = new ArrayList<>();
            for (Consumer<List<Object>> modifier : modifiers) {
                modifier.accept(values);
            }
            return Reflections.constructor$ClientboundSetEntityDataPacket.newInstance(entityID, values);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object setPassengersPacket(int vehicle, int[] passengers) {
        try {
            Object packet = Reflections.allocateClientboundSetPassengersPacketInstance();
            Reflections.field$ClientboundSetPassengersPacket$passengers.set(packet, passengers);
            Reflections.field$ClientboundSetPassengersPacket$vehicle.set(packet, vehicle);
            return packet;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object removeEntityPacket(int... entityID) {
        try {
            return Reflections.constructor$ClientboundRemoveEntitiesPacket.newInstance((Object) entityID);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object vec3(double x, double y, double z) {
        try {
            return Reflections.constructor$Vector3f.newInstance((float) x, (float) y, (float) z);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onPacketSend(CNPlayer player, PacketEvent event) {
        try {
            Object packet = event.getPacket();
            if (Reflections.clazz$ClientboundBundlePacket.isInstance(packet)) {
                Iterable<Object> packets = (Iterable<Object>) Reflections.field$BundlePacket$packets.get(packet);
                for (Object p : packets) {
                    handlePacket(player, event, p);
                }
            } else {
                handlePacket(player, event, packet);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void handlePacket(CNPlayer player, PacketEvent event, Object packet) throws ReflectiveOperationException {
        Optional.ofNullable(packetFunctions.get(packet.getClass().getSimpleName()))
                .ifPresent(function -> function.accept(player, event, packet));
    }
}