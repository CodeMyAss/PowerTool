/*
 * Copyright 2011 Allan Saddi <allan@saddi.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tyrannyofheaven.bukkit.PowerTool;

import static org.tyrannyofheaven.bukkit.util.ToHLoggingUtils.debug;
import static org.tyrannyofheaven.bukkit.util.ToHMessageUtils.colorize;
import static org.tyrannyofheaven.bukkit.util.ToHMessageUtils.sendMessage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PowerToolListener implements Listener {

    private static final Map<Action, PowerToolAction> actionMap;

    private final PowerToolPlugin plugin;

    static {
        Map<Action, PowerToolAction> am = new HashMap<Action, PowerToolAction>();

        am.put(Action.LEFT_CLICK_AIR, PowerToolAction.LEFT_CLICK);
        am.put(Action.LEFT_CLICK_BLOCK, PowerToolAction.LEFT_CLICK);
        am.put(Action.RIGHT_CLICK_AIR, PowerToolAction.RIGHT_CLICK);
        am.put(Action.RIGHT_CLICK_BLOCK, PowerToolAction.RIGHT_CLICK);

        actionMap = Collections.unmodifiableMap(am);
    }

    PowerToolListener(PowerToolPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().hasPermission("powertool.use")) return;

        // NB: Don't care if it's canceled or not.
        // Interaction will be canceled if it doesn't hit a block, which is
        // something we care about.

        if (!event.hasItem()) return; // no bare fists (for now...)

        PowerTool pt = plugin.getPowerTool(event.getPlayer(), event.getItem().getTypeId(), false);
        if (pt != null) {
            PowerToolAction action = actionMap.get(event.getAction());
            if (action != null) {
                PowerTool.Command command = pt.getCommand(action);
                if (command != null && !command.hasPlayerToken()) {
                    String commandString = command.getCommand();
                    if (command.hasLocationToken()) {
                        commandString = plugin.substituteLocation(event.getPlayer(), event.getClickedBlock(), commandString, command.hasAirToken());
                    }
                    if (commandString != null) {
                        plugin.execute(event.getPlayer(), commandString);
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority=EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().hasPermission("powertool.use")) return;

        int itemId = event.getPlayer().getItemInHand().getTypeId();
        
        if (itemId == Material.AIR.getId()) return;
        
        PowerTool pt = plugin.getPowerTool(event.getPlayer(), itemId, false);
        if (pt != null) {
            PowerTool.Command command = pt.getCommand(PowerToolAction.RIGHT_CLICK);
            if (command != null) {
                String commandString = null;
                
                if (command.hasPlayerToken()) {
                    // Player is required, only execute if player was target
                    Entity entity = event.getRightClicked();
                    if (entity instanceof Player) {
                        Player clicked = (Player)entity;
                        debug(plugin, "%s right-clicked %s", event.getPlayer().getName(), clicked.getName());
    
                        commandString = command.getCommand().replace(plugin.getPlayerToken(), clicked.getName());
                    }
                }
                
                if (commandString != null) {
                    if (command.hasLocationToken()) {
                        commandString = plugin.substituteLocation(event.getPlayer(), null, commandString, command.hasAirToken());
                    }
                    if (commandString != null) {
                        plugin.execute(event.getPlayer(), commandString);
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.loadPersistentPowerTools(event.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.forgetPlayer(event.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        if (!plugin.isVerbose()) return;

        if (!event.getPlayer().hasPermission("powertool.use")) return;

        ItemStack itemStack = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (itemStack == null) return;
        
        int itemId = itemStack.getTypeId();
        
        if (itemId == Material.AIR.getId()) return;

        PowerTool pt = plugin.getPowerTool(event.getPlayer(), itemId, false);
        if (pt != null) {
            boolean headerSent = false;

            for (PowerToolAction action : PowerToolAction.values()) {
                PowerTool.Command command = pt.getCommand(action);
                if (command != null) {
                    if (!headerSent) {
                        sendMessage(event.getPlayer(), colorize("`yPower tool:"));
                        headerSent = true;
                    }
                    
                    sendMessage(event.getPlayer(), colorize("  `y%s: `g%s"), action.getDisplayName(), command);
                }
            }
        }
    }

    @EventHandler(priority=EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent)event;
            if (e.getDamager() instanceof Player) {
                Player attacker = (Player)e.getDamager();
                
                if (!attacker.hasPermission("powertool.use")) return;

                int itemId = attacker.getItemInHand().getTypeId();
                
                if (itemId == Material.AIR.getId()) return;
                
                PowerTool pt = plugin.getPowerTool(attacker, itemId, false);
                if (pt != null) {
                    PowerTool.Command command = pt.getCommand(PowerToolAction.LEFT_CLICK);
                    if (command != null) {
                        String commandString = null;
                        
                        if (command.hasPlayerToken()) {
                            // Player target required
                            if (e.getEntity() instanceof Player) {
                                Player victim = (Player)e.getEntity();
                                debug(plugin, "%s left-clicked %s", attacker.getName(), victim.getName());
                                
                                commandString = command.getCommand().replace(plugin.getPlayerToken(), victim.getName());
                            }
                        }
                        else {
                            // A left-click is a left-click
                            commandString = command.getCommand();
                        }
                        
                        if (commandString != null) {
                            if (command.hasLocationToken()) {
                                commandString = plugin.substituteLocation(attacker, null, commandString, command.hasAirToken());
                            }
                            if (commandString != null) {
                                plugin.execute(attacker, commandString);
                                event.setCancelled(true);
                            }
                        }
                    }
                }
            }
        }
    }

}