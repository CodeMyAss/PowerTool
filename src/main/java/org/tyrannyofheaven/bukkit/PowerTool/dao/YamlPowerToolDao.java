package org.tyrannyofheaven.bukkit.PowerTool.dao;

import static org.tyrannyofheaven.bukkit.util.ToHLoggingUtils.warn;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.tyrannyofheaven.bukkit.PowerTool.ItemKey;
import org.tyrannyofheaven.bukkit.PowerTool.PowerTool;
import org.tyrannyofheaven.bukkit.PowerTool.PowerToolAction;
import org.tyrannyofheaven.bukkit.PowerTool.PowerToolPlugin;
import org.tyrannyofheaven.bukkit.util.ToHFileUtils;
import org.tyrannyofheaven.bukkit.util.ToHStringUtils;

public class YamlPowerToolDao implements PowerToolDao {

    private static final String NOT_MAP_NODE_MSG = "%s must be a mapping node";

    private static final String UNKNOWN_MATERIAL_MSG = "Unknown material '%s'; power tool ignored";

    private static final String BAD_TOKENS_MSG = "Power tool '%s' uses both player and coordinate tokens; ignored";

    private final PowerToolPlugin plugin;

    private final File file;

    private final boolean useDisplayNames;

    private FileConfiguration config;
    
    public YamlPowerToolDao(PowerToolPlugin plugin, File file, boolean useDisplayNames) {
        this.plugin = plugin;
        this.file = file;
        config = YamlConfiguration.loadConfiguration(file);
        this.useDisplayNames = useDisplayNames;
    }
    
    public YamlPowerToolDao(PowerToolPlugin plugin, File file, FileConfiguration config, boolean useDisplayNames) {
        this.plugin = plugin;
        this.file = file;
        this.config = config;
        this.useDisplayNames = useDisplayNames;
    }

    public void setConfig(FileConfiguration config) {
        this.config = config;
    }

    private String getMaterialPath(Player player, ItemKey key) {
        return String.format("%s.%s", getBasePath(player), key);
    }

    private String getBasePath(Player player) {
        String basePath;
        if (player != null) {
            basePath = String.format("players.%s", player.getName());
        }
        else {
            basePath = "powertools";
        }
        return basePath;
    }

    @Override
    public PowerTool loadPowerTool(Player player, ItemKey key) {
        ConfigurationSection section = config.getConfigurationSection(getBasePath(player));
        if (section != null) {
            Map<String, Object> nodes = section.getValues(false);

            // Have to iterate since the keys can have many forms...
            for (Map.Entry<String, Object> me : nodes.entrySet()) {
                if (!(me.getValue() instanceof ConfigurationSection)) {
                    warn(plugin, NOT_MAP_NODE_MSG, me.getKey());
                    continue;
                }

                ItemKey matchedKey = ItemKey.fromString(me.getKey(), useDisplayNames);
                if (matchedKey != null) {
                    if (matchedKey.equals(key)) {
                        return loadPowerTool(player == null, (ConfigurationSection)me.getValue(), me.getKey());
                    }
                }
                else
                    warn(plugin, UNKNOWN_MATERIAL_MSG, me.getKey());
            }
        }
        return null;
    }

    private PowerTool loadPowerTool(boolean global, ConfigurationSection node, String materialName) {
        if (node != null) {
            PowerTool pt = new PowerTool();
            for (PowerToolAction action : PowerToolAction.values()) {
                String command = node.getString(action.getDisplayName());
                if (ToHStringUtils.hasText(command)) {
                    boolean hasPlayerToken = command.contains(plugin.getPlayerToken());
                    boolean hasAirToken = command.contains(plugin.getYAirToken());
                    boolean hasLocationToken = command.contains(plugin.getXToken()) || command.contains(plugin.getYToken()) || command.contains(plugin.getZToken()) || hasAirToken;
                    if (hasPlayerToken && hasLocationToken) {
                        warn(plugin, BAD_TOKENS_MSG, materialName);
                        return null;
                    }
                    pt.setCommand(action, command, hasPlayerToken, hasLocationToken, hasAirToken);
                }
            }
            return pt;
        }
        return null;
    }

    @Override
    public Map<ItemKey, PowerTool> loadPowerTools(Player player) {
        Map<ItemKey, PowerTool> powerTools = new HashMap<ItemKey, PowerTool>();
        ConfigurationSection section = config.getConfigurationSection(getBasePath(player));
        if (section != null) {
            Map<String, Object> nodes = section.getValues(false);

            for (Map.Entry<String, Object> me : nodes.entrySet()) {
                if (!(me.getValue() instanceof ConfigurationSection)) {
                    warn(plugin, NOT_MAP_NODE_MSG, me.getKey());
                    continue;
                }

                ItemKey matchedKey = ItemKey.fromString(me.getKey(), useDisplayNames);
                if (matchedKey != null) {
                    PowerTool pt = loadPowerTool(player == null, (ConfigurationSection)me.getValue(), me.getKey());
                    if (pt != null)
                        powerTools.put(matchedKey, pt);
                }
                else
                    warn(plugin, UNKNOWN_MATERIAL_MSG, me.getKey());
            }
        }
        return powerTools;
    }

    @Override
    public void removePowerTool(Player player, ItemKey key) {
        ConfigurationSection section = config.getConfigurationSection(getBasePath(player));
        if (section != null) {
            Map<String, Object> nodes = section.getValues(false);

            // Have to iterate since the keys can have many forms...
            for (Map.Entry<String, Object> me : nodes.entrySet()) {
                if (!(me.getValue() instanceof ConfigurationSection)) {
                    warn(plugin, NOT_MAP_NODE_MSG, me.getKey());
                    continue;
                }

                ItemKey matchedKey = ItemKey.fromString(me.getKey(), useDisplayNames);
                if (matchedKey != null) {
                    if (matchedKey.equals(key)) {
                        // TODO This can probably be done better...
                        config.set(String.format("%s.%s", getBasePath(player), me.getKey()), null); // FIXME added a few commits after CB1317
                        ToHFileUtils.saveConfig(plugin, config, file.getParentFile(), file.getName());
                        break;
                    }
                }
                else
                    warn(plugin, UNKNOWN_MATERIAL_MSG, me.getKey());
            }
        }
    }

    @Override
    public void savePowerTool(Player player, ItemKey key, PowerTool powerTool) {
        ConfigurationSection section = config.getConfigurationSection(getBasePath(player));
        if (section == null)
            section = config.createSection(getBasePath(player));

        Map<String, Object> nodes = section.getValues(false);

        // Have to iterate since the keys can have many forms... (TODO screaming for a refactoring)
        for (Map.Entry<String, Object> me : nodes.entrySet()) {
            if (!(me.getValue() instanceof ConfigurationSection)) {
                warn(plugin, NOT_MAP_NODE_MSG, me.getKey());
                continue;
            }

            ItemKey matchedKey = ItemKey.fromString(me.getKey(), useDisplayNames);
            if (matchedKey != null) {
                if (matchedKey.equals(key)) {
                    // Remove this node first.
                    config.set(String.format("%s.%s", getBasePath(player), me.getKey()), null); // FIXME
                    break;
                }
            }
            else
                warn(plugin, UNKNOWN_MATERIAL_MSG, me.getKey());
        }
        
        // Do the actual save.
        String materialPath = getMaterialPath(player, key);
        for (PowerToolAction action : PowerToolAction.values()) {
            PowerTool.Command command = powerTool.getCommand(action);
            if (command != null) {
                config.set(String.format("%s.%s", materialPath, action.getDisplayName()), command.getCommand());
            }
        }

        // Save
        ToHFileUtils.saveConfig(plugin, config, file.getParentFile(), file.getName());
    }

}
