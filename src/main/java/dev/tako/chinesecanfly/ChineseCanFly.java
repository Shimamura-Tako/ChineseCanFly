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

import dev.tako.chinesecanfly.metrics.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChineseCanFly extends JavaPlugin {

    private FlyManager flyManager;

    @Override
    public void onEnable() {
        flyManager = new FlyManager(this);
        getServer().getPluginManager().registerEvents(flyManager, this);

        // bStats
        new Metrics(this, 33133);

        getLogger().info("中国人能飞！插件已启用。");
    }

    @Override
    public void onDisable() {
        if (flyManager != null) {
            flyManager.cleanup();
        }
        getLogger().info("ChineseCanFly 插件已禁用。");
    }
}
