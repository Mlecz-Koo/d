/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.dbteku.telecom.c.f
 *  com.dbteku.telecom.dualsim.DualSimManager
 *  com.dbteku.telecom.models.Carrier
 *  joserodpt.realscoreboard.api.RealScoreboardAPI
 *  joserodpt.realscoreboard.api.scoreboard.RScoreboard
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.Server
 *  org.bukkit.entity.OfflinePlayer
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.dbteku.telecom.dualsim;

import com.dbteku.telecom.c.f;
import com.dbteku.telecom.dualsim.DualSimManager;
import com.dbteku.telecom.models.Carrier;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import joserodpt.realscoreboard.api.RealScoreboardAPI;
import joserodpt.realscoreboard.api.scoreboard.RScoreboard;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Server;
import org.bukkit.entity.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TelecomDualSimPlugin
extends JavaPlugin
implements Listener {
    private static final String SECOND_TOKEN = "%telecomdual_second_operator%";
    private static final String SIGNAL1 = "%telecommulti_phone_signal_1%";
    private static final String SIGNAL2 = "%telecommulti_phone_signal_2%";

    public void onEnable() {
        Server server = this.getServer();
        if (server != null && server.getPluginManager() != null) {
            server.getPluginManager().registerEvents((Listener)this, (Plugin)this);
        }
        try {
            new Expansion().register();
        }
        catch (Throwable throwable) {
            this.getLogger().log(Level.WARNING, "Nie uda\u0142o si\u0119 zarejestrowa\u0107 placeholdera telecomdual.", throwable);
        }
        this.patchLoadedScoreboards();
        this.getLogger().info("TelecomDualSIM 5.0.0 enabled (safe RealScoreboard integration).");
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        if (playerCommandPreprocessEvent == null || playerCommandPreprocessEvent.isCancelled()) {
            return;
        }
        Player player = playerCommandPreprocessEvent.getPlayer();
        if (player == null) {
            return;
        }
        if (!player.hasPermission("telecom.use") && !player.isOp()) {
            return;
        }
        String string = playerCommandPreprocessEvent.getMessage();
        if (string == null) {
            return;
        }
        String[] stringArray = string.trim().split("\\s+");
        if (stringArray.length < 2) {
            return;
        }
        String string2 = stringArray[0];
        if (!string2.equalsIgnoreCase("/telecom") && !string2.equalsIgnoreCase("/tcom")) {
            return;
        }
        if (stringArray[1].equalsIgnoreCase("simleave")) {
            DualSimManager.clearSecondCarrier((UUID)player.getUniqueId());
            playerCommandPreprocessEvent.setCancelled(true);
            player.sendMessage("\u00a7aSIM 2 zosta\u0142a od\u0142\u0105czona.");
            return;
        }
        if (!stringArray[1].equalsIgnoreCase("join") || stringArray.length < 3) {
            return;
        }
        Carrier carrier = TelecomDualSimPlugin.safePrimaryCarrier(player.getName());
        if (carrier == null || carrier.isNull()) {
            return;
        }
        String string3 = stringArray[2];
        Carrier carrier2 = TelecomDualSimPlugin.findCarrierByName(string3);
        if (carrier2 == null || carrier2.isNull()) {
            return;
        }
        if (carrier2.getId().equalsIgnoreCase(carrier.getId())) {
            playerCommandPreprocessEvent.setCancelled(true);
            player.sendMessage("\u00a7cTen operator jest ju\u017c Twoj\u0105 g\u0142\u00f3wn\u0105 kart\u0105 SIM.");
            return;
        }
        Carrier carrier3 = null;
        try {
            carrier3 = DualSimManager.getSecondCarrier((UUID)player.getUniqueId());
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        DualSimManager.setSecondCarrierId((UUID)player.getUniqueId(), (String)carrier2.getId());
        playerCommandPreprocessEvent.setCancelled(true);
        if (carrier3 != null && !carrier3.isNull()) {
            player.sendMessage("\u00a7aSIM 2 zmieniona na operatora \u00a7f" + carrier2.getName() + "\u00a7a.");
        } else {
            player.sendMessage("\u00a7aPo\u0142\u0105czono SIM 2 z operatorem \u00a7f" + carrier2.getName() + "\u00a7a.");
        }
    }

    private static Carrier safePrimaryCarrier(String string) {
        try {
            return f.a().i(string);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private static Carrier findCarrierByName(String string) {
        try {
            f f2 = f.a();
            if (!f2.b(string)) {
                return null;
            }
            return f2.d(string);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public static String secondOperator(OfflinePlayer offlinePlayer) {
        try {
            if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
                return "$skip";
            }
            Carrier carrier = DualSimManager.getSecondCarrier((UUID)offlinePlayer.getUniqueId());
            if (carrier == null || carrier.isNull()) {
                return "$skip";
            }
            return carrier.getName();
        }
        catch (Throwable throwable) {
            return "$skip";
        }
    }

    private void patchLoadedScoreboards() {
        try {
            RealScoreboardAPI realScoreboardAPI = RealScoreboardAPI.getInstance();
            if (realScoreboardAPI == null || realScoreboardAPI.getScoreboardManagerAPI() == null) {
                this.getLogger().warning("RealScoreboard API jest niedost\u0119pne \u2014 pomijam patch scoreboardu.");
                return;
            }
            Collection collection = realScoreboardAPI.getScoreboardManagerAPI().getScoreboards();
            if (collection == null) {
                return;
            }
            int n = 0;
            int n2 = 0;
            for (RScoreboard rScoreboard : collection) {
                for (List<String> list : TelecomDualSimPlugin.getAllLineLists(rScoreboard)) {
                    int n3;
                    if (list == null || list.isEmpty()) continue;
                    n2 += TelecomDualSimPlugin.removeGarbage(list);
                    if (TelecomDualSimPlugin.containsToken(list) || (n3 = TelecomDualSimPlugin.findOperatorLine(list)) < 0) continue;
                    list.add(n3 + 1, TelecomDualSimPlugin.buildSecondLine(list.get(n3)));
                    ++n;
                }
            }
            this.getLogger().info("RealScoreboard startup patch: SIM2 rows inserted=" + n + ", signal rows removed=" + n2 + ".");
        }
        catch (Throwable throwable) {
            this.getLogger().log(Level.WARNING, "Nie uda\u0142o si\u0119 przygotowa\u0107 RealScoreboard dla Dual SIM.", throwable);
        }
    }

    private static Collection<List<String>> getAllLineLists(RScoreboard rScoreboard) throws Exception {
        ArrayList<List<String>> arrayList = new ArrayList<List<String>>();
        Class<?> clazz = rScoreboard.getClass();
        if ("joserodpt.realscoreboard.api.scoreboard.RScoreboardBoards".equals(clazz.getName())) {
            Field field = clazz.getDeclaredField("boards");
            field.setAccessible(true);
            Object object = field.get(rScoreboard);
            if (object instanceof Iterable) {
                for (Object t : (Iterable)object) {
                    Method method = t.getClass().getMethod("getLines", new Class[0]);
                    Object object2 = method.invoke(t, new Object[0]);
                    if (!(object2 instanceof List)) continue;
                    arrayList.add((List)object2);
                }
            }
        } else {
            arrayList.add(rScoreboard.getLines());
        }
        return arrayList;
    }

    private static int removeGarbage(List<String> list) {
        int n = 0;
        Iterator<String> iterator = list.iterator();
        while (iterator.hasNext()) {
            String string;
            String string2 = iterator.next();
            if (string2 == null || !(string = string2.toLowerCase(Locale.ROOT)).contains(SIGNAL1) && !string.contains(SIGNAL2)) continue;
            iterator.remove();
            ++n;
        }
        return n;
    }

    private static boolean containsToken(List<String> list) {
        for (String string : list) {
            if (string == null || !string.toLowerCase(Locale.ROOT).contains(SECOND_TOKEN)) continue;
            return true;
        }
        return false;
    }

    private static int findOperatorLine(List<String> list) {
        String string;
        String string2;
        int n;
        for (n = 0; n < list.size(); ++n) {
            string2 = list.get(n);
            if (string2 == null || (string = string2.toLowerCase(Locale.ROOT)).contains("phone_signal") || string.contains("signal_")) continue;
            if (string.contains("%telecommulti_carrier_name%")
                    || string.contains("%telecommulti_carrier%")
                    || string.contains("%telecom_carrier_name%")
                    || string.contains("%telecom_carrier%")
                    || string.contains("%carrier_name%")
                    || string.contains("%operator%")
                    || string.contains("operator:")
                    || string.contains("carrier:")) {
                return n;
            }
            return n;
        }
        for (n = 0; n < list.size(); ++n) {
            string2 = list.get(n);
            if (string2 == null || !(string = string2.toLowerCase(Locale.ROOT)).contains("%telecommulti_") || string.contains("signal")) continue;
            return n;
        }
        return -1;
    }

    private static String buildSecondLine(String string) {
        String line = string == null ? "" : string;
        String lower = line.toLowerCase(Locale.ROOT);
        int colon = line.indexOf(':');

        if (colon >= 0) {
            String label = line.substring(0, colon);
            String value = line.substring(colon + 1);
            String labelLower = label.toLowerCase(Locale.ROOT);
            if (labelLower.contains("operator") || labelLower.contains("carrier")) {
                String newLabel = label.replaceAll("(?i)operator|carrier", "SIM 2");
                String formatting = value.replaceFirst("^(\\s*(?:[&§][0-9a-fk-or])*)?.*$", "$1");
                return newLabel + ":" + formatting + SECOND_TOKEN;
            }
        }

        String indent = line.replaceFirst("^([^\\S\\r\\n]*).*$", "$1");
        return indent + "&7SIM 2: &f" + SECOND_TOKEN;
    }

    public static final class Expansion
    extends PlaceholderExpansion {
        public String getIdentifier() {
            return "telecomdual";
        }

        public String getAuthor() {
            return "dbteku";
        }

        public String getVersion() {
            return "5.0.0";
        }

        public String onRequest(OfflinePlayer offlinePlayer, String string) {
            if (string == null) {
                return null;
            }
            switch (string.toLowerCase(Locale.ROOT)) {
                case "second_operator": 
                case "sim2_operator": 
                case "operator_2": {
                    return TelecomDualSimPlugin.secondOperator(offlinePlayer);
                }
                case "has_second_operator": 
                case "has_sim2": {
                    return "$skip".equals(TelecomDualSimPlugin.secondOperator(offlinePlayer)) ? "false" : "true";
                }
                case "second_operator_id": 
                case "sim2_operator_id": {
                    try {
                        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
                            return "";
                        }
                        Carrier carrier = DualSimManager.getSecondCarrier((UUID)offlinePlayer.getUniqueId());
                        return carrier == null || carrier.isNull() ? "" : carrier.getId();
                    }
                    catch (Throwable throwable) {
                        return "";
                    }
                }
            }
            return null;
        }
    }
}

