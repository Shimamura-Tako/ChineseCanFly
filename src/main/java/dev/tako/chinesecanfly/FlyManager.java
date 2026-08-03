/*
 * This file is part of ChineseCanFly.
 *
 * ChineseCanFly is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ChineseCanFly is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ChineseCanFly. If not, see <https://www.gnu.org/licenses/>.
 */
package dev.tako.chinesecanfly;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FlyManager implements Listener {

    private static final String MSG_GRANTED = "§a中国人能飞！已为你开启飞行权限。";
    private static final String MSG_REVOKED = "§c讲中文才飞！请切换回中文以恢复飞行。";

    private final ChineseCanFly plugin;
    /** 由本插件授予飞行权限的玩家 */
    private final Set<UUID> flyingByPlugin = new HashSet<>();
    /** 刚被收回飞行、享有摔落保护的玩家 */
    private final Set<UUID> fallProtected = new HashSet<>();

    public FlyManager(ChineseCanFly plugin) {
        this.plugin = plugin;
    }

    // ========== 事件监听 ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        Player player = event.getPlayer();
        // event.getLocale() 返回的是新 locale
        String newLocale = event.getLocale();
        applyFlight(player, newLocale);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 延迟 1 tick 确保 locale 已同步
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applyFlight(player, player.getLocale());
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flyingByPlugin.remove(uuid);
        fallProtected.remove(uuid);
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();

        // 切换到创造/旁观：原生飞行接管，移除插件跟踪
        if (newMode == GameMode.CREATIVE || newMode == GameMode.SPECTATOR) {
            flyingByPlugin.remove(player.getUniqueId());
            return;
        }

        // 切换到生存/冒险：延迟 1 tick 重新检查（等待模式切换完成）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applyFlight(player, player.getLocale());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (fallProtected.remove(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ========== 核心逻辑 ==========

    private void applyFlight(Player player, String locale) {
        GameMode mode = player.getGameMode();

        // 创造/旁观模式不干预
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }

        if (isChinese(locale)) {
            grantFlight(player);
        } else {
            revokeFlight(player);
        }
    }

    private boolean isChinese(String locale) {
        if (locale == null) return false;
        String lower = locale.toLowerCase();
        return lower.equals("zh_cn")
                || lower.equals("zh_tw")
                || lower.equals("zh_hk")
                || lower.equals("lzh");
    }

    private void grantFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (!flyingByPlugin.contains(uuid)) {
            flyingByPlugin.add(uuid);
            player.setAllowFlight(true);
            player.sendMessage(MSG_GRANTED);
        } else {
            // 已由插件授予，确保状态一致
            player.setAllowFlight(true);
        }
    }

    private void revokeFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (!flyingByPlugin.remove(uuid)) {
            // 不是由插件授予的飞行，不干预
            return;
        }

        boolean wasFlying = player.isFlying();
        player.setFlying(false);
        player.setAllowFlight(false);
        player.sendMessage(MSG_REVOKED);

        // 如果玩家正在飞行中被收回权限，给予摔落保护
        if (wasFlying) {
            fallProtected.add(uuid);
            // 10 秒后移除保护（防止永久残留）
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                fallProtected.remove(uuid);
            }, 200L);
        }
    }

    /** 插件禁用时清理所有状态 */
    public void cleanup() {
        for (UUID uuid : new HashSet<>(flyingByPlugin)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                GameMode mode = player.getGameMode();
                if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            }
        }
        flyingByPlugin.clear();
        fallProtected.clear();
    }
}
