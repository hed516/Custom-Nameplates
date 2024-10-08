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

package net.momirealms.customnameplates.backend.feature.tag;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.momirealms.customnameplates.api.AbstractCNPlayer;
import net.momirealms.customnameplates.api.CNPlayer;
import net.momirealms.customnameplates.api.ConfigManager;
import net.momirealms.customnameplates.api.CustomNameplates;
import net.momirealms.customnameplates.api.feature.CarouselText;
import net.momirealms.customnameplates.api.feature.JoinQuitListener;
import net.momirealms.customnameplates.api.feature.tag.NameTagConfig;
import net.momirealms.customnameplates.api.feature.tag.TagRenderer;
import net.momirealms.customnameplates.api.feature.tag.UnlimitedTagManager;
import net.momirealms.customnameplates.api.helper.VersionHelper;
import net.momirealms.customnameplates.api.network.Tracker;
import net.momirealms.customnameplates.api.requirement.Requirement;
import net.momirealms.customnameplates.api.util.Alignment;
import net.momirealms.customnameplates.api.util.ConfigUtils;
import net.momirealms.customnameplates.api.util.Vector3;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UnlimitedTagManagerImpl implements UnlimitedTagManager, JoinQuitListener {

    private final CustomNameplates plugin;
    private final LinkedHashMap<String, NameTagConfig> configs = new LinkedHashMap<>();
    private final ConcurrentHashMap<UUID, TagRendererImpl> tagRenderers = new ConcurrentHashMap<>();
    private NameTagConfig[] configArray = new NameTagConfig[0];
    private int previewDuration;
    private boolean alwaysShow;

    public UnlimitedTagManagerImpl(CustomNameplates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setPreviewing(CNPlayer player, boolean preview) {
        boolean isPreviewing = player.isPreviewing();
        if (isPreviewing) {
            if (preview) return;
            plugin.getUnlimitedTagManager().onRemovePlayer(player, player);
            player.removePlayerFromTracker(player);
            ((AbstractCNPlayer) player).setPreviewing(false);
        } else {
            if (!preview) return;
            Tracker tracker = player.addPlayerToTracker(player);
            tracker.setScale(player.scale());
            tracker.setCrouching(player.isCrouching());
            plugin.getUnlimitedTagManager().onAddPlayer(player, player);
            ((AbstractCNPlayer) player).setPreviewing(true);
        }
    }

    @Override
    public int previewDuration() {
        return previewDuration;
    }

    @Override
    public void onPlayerJoin(CNPlayer player) {
        TagRendererImpl sender = new TagRendererImpl(this, player);
        sender.onTick();
        TagRendererImpl previous = tagRenderers.put(player.uuid(), sender);
        if (previous != null) {
            previous.destroy();
        }
        setPreviewing(player, isAlwaysShow());
    }

    @Override
    public void onPlayerQuit(CNPlayer player) {
        TagRendererImpl sender = tagRenderers.remove(player.uuid());
        if (sender != null) {
            sender.destroy();
        }
    }

    @Override
    public void load() {
        if (!ConfigManager.nameplateModule()) return;
        this.loadConfig();
        this.resetArray();
        for (CNPlayer online : plugin.getOnlinePlayers()) {
            onPlayerJoin(online);
        }
    }

    @Override
    public void unload() {
        for (TagRendererImpl sender : tagRenderers.values()) {
            sender.destroy();
        }
        this.tagRenderers.clear();
        this.configs.clear();
        this.resetArray();
    }

    @Override
    public void onTick() {
        for (TagRendererImpl sender : tagRenderers.values()) {
            sender.onTick();
        }
    }

    @Override
    public boolean isAlwaysShow() {
        return alwaysShow;
    }

    private void resetArray() {
        configArray = configs.values().toArray(new NameTagConfig[0]);
    }

    @Override
    public NameTagConfig configById(String name) {
        return configs.get(name);
    }

    @Override
    public NameTagConfig[] nameTagConfigs() {
        return configArray;
    }

    @Override
    public void onAddPlayer(CNPlayer owner, CNPlayer added) {
        TagRendererImpl controller = tagRenderers.get(owner.uuid());
        if (controller != null) {
            controller.handlePlayerAdd(added);
        }
    }

    @Override
    public TagRenderer getTagRender(CNPlayer owner) {
        return tagRenderers.get(owner.uuid());
    }

    @Override
    public void onRemovePlayer(CNPlayer owner, CNPlayer removed) {
        TagRendererImpl controller = tagRenderers.get(owner.uuid());
        if (controller != null) {
            controller.handlePlayerRemove(removed);
        }
    }

    @Override
    public void onPlayerDataSet(CNPlayer owner, CNPlayer viewer, boolean isCrouching) {
        TagRendererImpl controller = tagRenderers.get(owner.uuid());
        if (controller != null) {
            controller.handleEntityDataChange(viewer, isCrouching);
        }
    }

    @Override
    public void onPlayerAttributeSet(CNPlayer owner, CNPlayer viewer, double scale) {
        TagRendererImpl controller = tagRenderers.get(owner.uuid());
        if (controller != null) {
            controller.handleAttributeChange(viewer, scale);
        }
    }

    private void loadConfig() {
        plugin.getConfigManager().saveResource("configs" + File.separator + "nameplate.yml");
        YamlDocument document = plugin.getConfigManager().loadData(new File(plugin.getDataDirectory().toFile(), "configs" + File.separator + "nameplate.yml"));
        previewDuration = document.getInt("preview-duration", 5);
        alwaysShow = document.getBoolean("always-show", false);
        Section unlimitedSection = document.getSection("unlimited");
        if (unlimitedSection == null) return;
        for (Map.Entry<String, Object> entry : unlimitedSection.getStringRouteMappedValues(false).entrySet()) {
            if (!(entry.getValue() instanceof Section section))
                return;
            Vector3 translation = ConfigUtils.vector3(section.getString("translation", "0,0,0"));
            this.configs.put(entry.getKey(),
                    NameTagConfig.builder()
                            .id(entry.getKey())
                            .ownerRequirement(plugin.getRequirementManager().parseRequirements(section.getSection("owner-conditions")))
                            .viewerRequirement(plugin.getRequirementManager().parseRequirements(section.getSection("viewer-conditions")))
                            .translation(VersionHelper.isVersionNewerThan1_20_2() ? translation : translation.add(0,0.5,0))
                            .scale(ConfigUtils.vector3(section.getString("scale", "1,1,1")))
                            .alignment(Alignment.valueOf(section.getString("alignment", "CENTER")))
                            .viewRange(section.getFloat("view-range", 1f))
                            .shadowRadius(section.getFloat("shadow-radius", 0f))
                            .shadowStrength(section.getFloat("shadow-strength", 1f))
                            .lineWidth(section.getInt("line-width", 200))
                            .hasShadow(section.getBoolean("has-shadow", false))
                            .seeThrough(section.getBoolean("is-see-through", false))
                            .opacity(section.getByte("opacity", (byte) -1))
                            .useDefaultBackgroundColor(section.getBoolean("use-default-background-color", false))
                            .backgroundColor(ConfigUtils.argb(section.getString("background-color", "64,0,0,0")))
                            .affectedByCrouching(section.getBoolean("affected-by-crouching", true))
                            .affectedByScaling(section.getBoolean("affected-by-scale-attribute", true))
                            .carouselText(
                                    section.contains("text") ?
                                            new CarouselText[]{new CarouselText(-1, new Requirement[0], section.getString("text"), false)} :
                                            ConfigUtils.carouselTexts(section.getSection("text-display-order"))
                            )
                            .build()
            );
        }
    }
}
