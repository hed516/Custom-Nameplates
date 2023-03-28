package net.momirealms.customnameplates.helper;

import net.momirealms.customnameplates.CustomNameplates;
import net.momirealms.customnameplates.manager.ConfigManager;
import net.momirealms.customnameplates.utils.AdventureUtils;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class VersionHelper {

    private boolean isNewerThan1_19_R2;
    private String serverVersion;
    private final CustomNameplates plugin;
    private final String pluginVersion;
    private boolean isLatest;

    public VersionHelper(CustomNameplates plugin) {
        this.plugin = plugin;
        this.pluginVersion = plugin.getDescription().getVersion();
    }

    public boolean isVersionNewerThan1_19_R2() {
        if (serverVersion == null) {
            this.serverVersion = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String[] split = serverVersion.split("_");
            int main_ver = Integer.parseInt(split[1]);
            if (main_ver >= 20) isNewerThan1_19_R2 = true;
            else if (main_ver == 19) isNewerThan1_19_R2 = Integer.parseInt(split[2].substring(1)) >= 2;
            else isNewerThan1_19_R2 = false;
        }
        return isNewerThan1_19_R2;
    }

    public void checkUpdate() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.polymart.org/v1/getResourceInfoSimple/?resource_id=2543&key=version");
                URLConnection conn = url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000);
                InputStream inputStream = conn.getInputStream();
                String newest = new BufferedReader(new InputStreamReader(inputStream)).readLine();
                String current = plugin.getDescription().getVersion();
                inputStream.close();
                isLatest = !compareVer(newest, current);
                if (isLatest) {
                    AdventureUtils.consoleMessage(ConfigManager.lang.equalsIgnoreCase("cn") ? "[CustomNameplates] 当前已是最新版本" : "[CustomNameplates] You are using the latest version.");
                    return;
                }

                if (ConfigManager.lang.equalsIgnoreCase("cn")) {
                    AdventureUtils.consoleMessage("[CustomNameplates] 当前版本: <red>" + current);
                    AdventureUtils.consoleMessage("[CustomNameplates] 最新版本: <green>" + newest);
                    AdventureUtils.consoleMessage("[CustomNameplates] 请到 <u>售后群<!u> 或 <u>https://polymart.org/resource/customnameplates.2543<!u> 获取最新版本.");
                }
                else {
                    AdventureUtils.consoleMessage("[CustomNameplates] Current version: <red>" + current);
                    AdventureUtils.consoleMessage("[CustomNameplates] Latest version: <green>" + newest);
                    AdventureUtils.consoleMessage("[CustomNameplates] Update is available: <u>https://polymart.org/resource/customnameplates.2543<!u>");
                }
            } catch (Exception exception) {
                Log.warn("Error occurred when checking update");
            }
        });
    }

    private boolean compareVer(String newV, String currentV) {
        if (newV == null || currentV == null || newV.isEmpty() || currentV.isEmpty()) {
            return false;
        }
        String[] newVS = newV.split("\\.");
        String[] currentVS = currentV.split("\\.");
        int maxL = Math.min(newVS.length, currentVS.length);
        for (int i = 0; i < maxL; i++) {
            try {
                String[] newPart = newVS[i].split("-");
                String[] currentPart = currentVS[i].split("-");
                int newNum = Integer.parseInt(newPart[0]);
                int currentNum = Integer.parseInt(currentPart[0]);
                if (newNum > currentNum) {
                    return true;
                } else if (newNum < currentNum) {
                    return false;
                } else if (newPart.length > 1 && currentPart.length > 1) {
                    String[] newHotfix = newPart[1].split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
                    String[] currentHotfix = currentPart[1].split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
                    // hotfix2 & hotfix
                    if (newHotfix.length == 2 && currentHotfix.length == 1) return true;
                        // hotfix3 & hotfix2
                    else if (newHotfix.length > 1 && currentHotfix.length > 1) {
                        int newHotfixNum = Integer.parseInt(newHotfix[1]);
                        int currentHotfixNum = Integer.parseInt(currentHotfix[1]);
                        if (newHotfixNum > currentHotfixNum) {
                            return true;
                        } else if (newHotfixNum < currentHotfixNum) {
                            return false;
                        } else {
                            return newHotfix[0].compareTo(currentHotfix[0]) > 0;
                        }
                    }
                } else if (newPart.length > 1) {
                    return true;
                } else if (currentPart.length > 1) {
                    return false;
                }
            }
            catch (NumberFormatException ignored) {
                return false;
            }
        }
        // if common parts are the same, the longer is newer
        return newVS.length > currentVS.length;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public boolean isLatest() {
        return isLatest;
    }
}
