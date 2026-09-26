/*
 * Decompiled with CFR 0.152.
 */
package com.dbteku.telecom.dualsim;

import com.dbteku.telecom.c.f;
import com.dbteku.telecom.c.k;
import com.dbteku.telecom.chat.c;
import com.dbteku.telecom.dualsim.DualSimManager;
import com.dbteku.telecom.lang.b;
import com.dbteku.telecom.models.Carrier;
import com.dbteku.telecom.models.CellSignal;
import com.dbteku.telecom.models.CellSignal;
import com.dbteku.telecom.models.CellTower;
import com.dbteku.telecom.models.WorldLocation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import joserodpt.realscoreboard.api.RealScoreboardAPI;
import joserodpt.realscoreboard.api.scoreboard.RScoreboard;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Server;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TelecomDualSimPlugin extends JavaPlugin implements Listener {
    private static final String SECOND_OPERATOR = "%telecomdual_second_operator%";
    private static final String SECOND_SIGNAL = "%telecommulti_phone_signal_2%";
    private static final String SECOND_STATION = "%telecomstation_second_station_id%";

    private static final String PRIMARY_SIGNAL_1 = "%telecommulti_phone_signal_1%";
    private static final String PRIMARY_SIGNAL_2 = "%telecommulti_phone_signal_2%";

    public void onEnable() {
        Server server = this.getServer();
        if (server != null && server.getPluginManager() != null) {
            server.getPluginManager().registerEvents(this, this);
        }

        try {
            new Expansion().register();
        } catch (Throwable throwable) {
            this.getLogger().log(Level.WARNING, "Nie udało się zarejestrować placeholdera telecomdual.", throwable);
        }

        /*
         * RealScoreboard renders asynchronously in some versions. Do the one-time
         * line layout change from the main thread after all normal plugin onEnable()
         * methods have had a chance to inject their own lines.
         */
        try {
            this.getServer().getScheduler().runTaskLater(this, this::patchLoadedScoreboards, 20L);
        } catch (Throwable throwable) {
            this.getLogger().log(Level.WARNING, "Nie udało się zaplanować patcha RealScoreboard.", throwable);
        }

        this.getLogger().info("TelecomDualSIM 5.1.0 enabled.");
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!player.hasPermission("telecom.use") && !player.isOp()) {
            return;
        }

        String message = event.getMessage();
        if (message == null) {
            return;
        }

        String[] args = message.trim().split("\\s+");
        if (args.length < 2) {
            return;
        }

        String root = args[0];
        if (!root.equalsIgnoreCase("/telecom") && !root.equalsIgnoreCase("/tcom")) {
            return;
        }

        if (args[1].equalsIgnoreCase("simleave")) {
            DualSimManager.clearSecondCarrier(player.getUniqueId());
            event.setCancelled(true);
            player.sendMessage("§aSIM 2 została odłączona.");
            return;
        }

        if (!args[1].equalsIgnoreCase("join") || args.length < 3) {
            return;
        }

        Carrier primary = safePrimaryCarrier(player.getName());
        if (primary == null || primary.isNull()) {
            return;
        }

        Carrier second = findCarrierByName(args[2]);
        if (second == null || second.isNull()) {
            return;
        }

        if (second.getId().equalsIgnoreCase(primary.getId())) {
            event.setCancelled(true);
            player.sendMessage("§cTen operator jest już Twoją główną kartą SIM.");
            return;
        }

        Carrier oldSecond = null;
        try {
            oldSecond = DualSimManager.getSecondCarrier(player.getUniqueId());
        } catch (Throwable ignored) {
        }

        DualSimManager.setSecondCarrierId(player.getUniqueId(), second.getId());
        event.setCancelled(true);

        if (oldSecond != null && !oldSecond.isNull()) {
            player.sendMessage("§aSIM 2 zmieniona na operatora §f" + second.getName() + "§a.");
        } else {
            player.sendMessage("§aPołączono SIM 2 z operatorem §f" + second.getName() + "§a.");
        }
    }

    private static Carrier safePrimaryCarrier(String playerName) {
        try {
            return f.a().i(playerName);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Carrier findCarrierByName(String name) {
        try {
            f telecom = f.a();
            if (!telecom.b(name)) {
                return null;
            }
            return telecom.d(name);
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static String secondOperator(OfflinePlayer player) {
        try {
            if (player == null || player.getUniqueId() == null) {
                return "$skip";
            }
            Carrier carrier = DualSimManager.getSecondCarrier(player.getUniqueId());
            if (carrier == null || carrier.isNull()) {
                return "$skip";
            }
            return carrier.getName();
        } catch (Throwable throwable) {
            return "$skip";
        }
    }

    /**
     * Calculates the second SIM signal using the real Telecom tower-selection logic.
     * No primary SIM state is changed.
     */
    public static CellSignal getSecondCellSignal(Player player) {
        if (player == null) {
            return new CellSignal();
        }

        try {
            Carrier carrier = DualSimManager.getSecondCarrier(player.getUniqueId());
            if (carrier == null || carrier.isNull()) {
                return new CellSignal();
            }

            WorldLocation location = new WorldLocation(player.getLocation());
            CellTower tower = carrier.getBestTowerByBand(location);

            if (tower != null && !tower.isNull()) {
                double strength = tower.determineStrength(location);
                if (strength > 0.0) {
                    return new CellSignal(carrier.getName(), player.getName(), tower, strength);
                }
            }

            // Match Telecom's normal peer fallback when enabled.
            if (k.a().l()) {
                Iterator<String> peers = carrier.getPeers();
                Carrier peerCarrier = new Carrier();
                CellTower peerTower = new CellTower();
                double peerStrength = 0.0;

                while (peerStrength <= 0.0 && peers.hasNext()) {
                    peerCarrier = f.a().e(peers.next());
                    peerTower = peerCarrier.getBestTowerByBand(location);
                    if (peerTower != null && !peerTower.isNull()) {
                        peerStrength = peerTower.determineStrength(location);
                    }
                }

                if (peerStrength > 0.0 && peerTower != null && !peerTower.isNull()) {
                    return new CellSignal(
                        peerCarrier.getName(),
                        player.getName(),
                        peerTower,
                        peerStrength,
                        true,
                        carrier.getName()
                    );
                }
            }
        } catch (Throwable ignored) {
        }

        return new CellSignal();
    }

    /**
     * Formats SIM2 exactly through Telecom's normal signal template, so dots,
     * band and the internet/globe indicator stay consistent with SIM1.
     */
    public static String secondSignal(OfflinePlayer offlinePlayer) {
        try {
            if (offlinePlayer == null || !offlinePlayer.isOnline()) {
                return "";
            }

            Player player = offlinePlayer.getPlayer();
            CellSignal signal = getSecondCellSignal(player);
            if (signal == null || !signal.hasSignal()) {
                return "";
            }

            String internetColor;
            try {
                if (c.c().e(player)) {
                    internetColor = "&a";
                } else {
                    internetColor = signal.getBand().isInternetCapable() ? "&6" : "&c";
                }
            } catch (Throwable throwable) {
                internetColor = signal.getBand().isInternetCapable() ? "&6" : "&c";
            }

            String template = b.a().bm;
            if (template == null || template.isEmpty()) {
                return signal.toString();
            }

            return template
                .replace("%carrier_signal%", signal.toString())
                .replace("%internet_status_color%", internetColor);
        } catch (Throwable throwable) {
            return "";
        }
    }

    private void patchLoadedScoreboards() {
        try {
            RealScoreboardAPI api = RealScoreboardAPI.getInstance();
            if (api == null || api.getScoreboardManagerAPI() == null) {
                this.getLogger().warning("RealScoreboard API jest niedostępne — pomijam patch scoreboardu.");
                return;
            }

            Collection<?> scoreboards = api.getScoreboardManagerAPI().getScoreboards();
            if (scoreboards == null) {
                return;
            }

            int inserted = 0;

            for (Object scoreboardObject : scoreboards) {
                if (!(scoreboardObject instanceof RScoreboard)) {
                    continue;
                }

                RScoreboard scoreboard = (RScoreboard) scoreboardObject;
                for (List<String> lines : getAllLineLists(scoreboard)) {
                    if (lines == null || lines.isEmpty() || containsToken(lines)) {
                        continue;
                    }

                    int operatorIndex = findOperatorLine(lines);
                    if (operatorIndex < 0) {
                        continue;
                    }

                    int signalIndex = findSignalLine(lines, operatorIndex);
                    if (signalIndex < 0) {
                        continue;
                    }

                    int stationIndex = findStationLine(lines, signalIndex);
                    int insertAfter = stationIndex >= 0 ? stationIndex : signalIndex;

                    String operatorLine = buildSecondOperatorLine(lines.get(operatorIndex));
                    String signalLine = buildSecondSignalLine(lines.get(signalIndex));
                    String stationLine = buildSecondStationLine(stationIndex >= 0 ? lines.get(stationIndex) : null);

                    lines.add(insertAfter + 1, operatorLine);
                    lines.add(insertAfter + 2, signalLine);
                    lines.add(insertAfter + 3, stationLine);
                    inserted++;

                    saveScoreboard(scoreboard);
                }
            }

            this.getLogger().info("RealScoreboard startup patch: complete SIM2 blocks inserted=" + inserted + ".");
        } catch (Throwable throwable) {
            this.getLogger().log(Level.WARNING, "Nie udało się przygotować RealScoreboard dla Dual SIM.", throwable);
        }
    }

    private static Collection<List<String>> getAllLineLists(RScoreboard scoreboard) throws Exception {
        ArrayList<List<String>> result = new ArrayList<>();
        Class<?> clazz = scoreboard.getClass();

        if ("joserodpt.realscoreboard.api.scoreboard.RScoreboardBoards".equals(clazz.getName())) {
            Field field = clazz.getDeclaredField("boards");
            field.setAccessible(true);
            Object boards = field.get(scoreboard);

            if (boards instanceof Iterable<?>) {
                for (Object board : (Iterable<?>) boards) {
                    if (board == null) {
                        continue;
                    }
                    Method getLines = board.getClass().getMethod("getLines");
                    Object lines = getLines.invoke(board);
                    if (lines instanceof List<?>) {
                        @SuppressWarnings("unchecked")
                        List<String> typed = (List<String>) lines;
                        result.add(typed);
                    }
                }
            }
        } else {
            result.add(scoreboard.getLines());
        }

        return result;
    }

    private static void saveScoreboard(RScoreboard scoreboard) {
        try {
            scoreboard.getClass().getMethod("saveScoreboard").invoke(scoreboard);
        } catch (Throwable ignored) {
        }
    }

    private static boolean containsToken(List<String> lines) {
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains(SECOND_OPERATOR)
                || lower.contains(SECOND_SIGNAL)
                || lower.contains(SECOND_STATION)) {
                return true;
            }
        }
        return false;
    }

    private static int findOperatorLine(List<String> lines) {
        String[] placeholders = {
            "%telecommulti_carrier_name%",
            "%telecommulti_carrier%",
            "%telecom_carrier_name%",
            "%telecom_carrier%",
            "%carrier_name%",
            "%operator%"
        };

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }

            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("phone_signal") || lower.contains("signal_")) {
                continue;
            }

            for (String placeholder : placeholders) {
                if (lower.contains(placeholder.toLowerCase(Locale.ROOT))) {
                    return i;
                }
            }

            if (lower.contains("operator:") || lower.contains("carrier:")) {
                return i;
            }
        }

        return -1;
    }

    private static int findSignalLine(List<String> lines, int operatorIndex) {
        for (int i = operatorIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }

            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains(PRIMARY_SIGNAL_1)
                || lower.contains("%telecom_phone_signal%")
                || lower.contains("%carrier_signal%")
                || lower.contains("%telecom_signal%")
                || lower.contains("phone_signal")) {
                return i;
            }

            if (lower.contains("signal_")) {
                return i;
            }

            // Stop once we hit an unrelated scoreboard section.
            if (lower.contains("ping:")
                || lower.contains("xyz:")
                || lower.contains("godzina:")
                || lower.contains("tps:")) {
                break;
            }
        }

        return -1;
    }

    private static int findStationLine(List<String> lines, int signalIndex) {
        if (signalIndex + 1 < lines.size()) {
            String next = lines.get(signalIndex + 1);
            if (next != null) {
                String lower = next.toLowerCase(Locale.ROOT);
                if (lower.contains("stacja:") || lower.contains("%telecomstation_station_id%")) {
                    return signalIndex + 1;
                }
            }
        }

        return -1;
    }

    private static String buildSecondOperatorLine(String primaryLine) {
        String line = primaryLine == null ? "" : primaryLine;

        String[] placeholders = {
            "%telecommulti_carrier_name%",
            "%telecommulti_carrier%",
            "%telecom_carrier_name%",
            "%telecom_carrier%",
            "%carrier_name%",
            "%operator%"
        };

        for (String placeholder : placeholders) {
            String replaced = line.replaceAll("(?i)" + Pattern.quote(placeholder), Matcher.quoteReplacement(SECOND_OPERATOR));
            if (!replaced.equals(line)) {
                return replaced;
            }
        }

        int colon = line.indexOf(':');
        if (colon >= 0) {
            String label = line.substring(0, colon);
            if (label.toLowerCase(Locale.ROOT).contains("operator")
                || label.toLowerCase(Locale.ROOT).contains("carrier")) {
                String newLabel = label.replaceAll("(?i)operator|carrier", "SIM 2");
                return newLabel + ":" + line.substring(colon + 1).replaceAll("[-&§\\w]*$", "") + " " + SECOND_OPERATOR;
            }
        }

        String indent = line.replaceFirst("^([^\\S\\r\\n]*).*$", "$1");
        return indent + SECOND_OPERATOR;
    }

    private static String buildSecondSignalLine(String primaryLine) {
        String line = primaryLine == null ? "" : primaryLine;

        String[] placeholders = {
            PRIMARY_SIGNAL_1,
            "%telecom_phone_signal%",
            "%carrier_signal%",
            "%telecom_signal%"
        };

        for (String placeholder : placeholders) {
            String replaced = line.replaceAll("(?i)" + Pattern.quote(placeholder), Matcher.quoteReplacement(SECOND_SIGNAL));
            if (!replaced.equals(line)) {
                return replaced;
            }
        }

        String indent = line.replaceFirst("^([^\\S\\r\\n]*).*$", "$1");
        return indent + SECOND_SIGNAL;
    }

    private static String buildSecondStationLine(String primaryStationLine) {
        String line = primaryStationLine;

        if (line != null && !line.isEmpty()) {
            String replaced = line.replaceAll(
                "(?i)" + Pattern.quote("%telecomstation_station_id%"),
                Matcher.quoteReplacement(SECOND_STATION)
            );
            if (!replaced.equals(line)) {
                return replaced;
            }
            if (line.toLowerCase(Locale.ROOT).contains("stacja:")) {
                int colon = line.indexOf(':');
                if (colon >= 0) {
                    return line.substring(0, colon + 1) + line.substring(colon + 1).replaceAll(
                        "(?i)%[^%]+%",
                        Matcher.quoteReplacement(SECOND_STATION)
                    );
                }
            }
        }

        String indent = line == null ? "" : line.replaceFirst("^([^\\S\\r\\n]*).*$", "$1");
        return indent + "&7Stacja: &f" + SECOND_STATION;
    }

    public static final class Expansion extends PlaceholderExpansion {
        public String getIdentifier() {
            return "telecomdual";
        }

        public String getAuthor() {
            return "dbteku/OpenAI";
        }

        public String getVersion() {
            return "5.1.0";
        }

        public String onRequest(OfflinePlayer player, String identifier) {
            if (identifier == null) {
                return null;
            }

            switch (identifier.toLowerCase(Locale.ROOT)) {
                case "second_operator":
                case "sim2_operator":
                case "operator_2":
                    return TelecomDualSimPlugin.secondOperator(player);

                case "second_operator_id":
                case "sim2_operator_id":
                    try {
                        if (player == null || player.getUniqueId() == null) {
                            return "";
                        }
                        Carrier carrier = DualSimManager.getSecondCarrier(player.getUniqueId());
                        return carrier == null || carrier.isNull() ? "" : carrier.getId();
                    } catch (Throwable throwable) {
                        return "";
                    }

                case "second_signal":
                case "sim2_signal":
                case "phone_signal_2":
                    return TelecomDualSimPlugin.secondSignal(player);

                case "has_second_operator":
                case "has_sim2":
                    return "$skip".equals(TelecomDualSimPlugin.secondOperator(player)) ? "false" : "true";

                default:
                    return null;
            }
        }
    }
}
