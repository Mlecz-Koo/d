/*
 * Decompiled with CFR 0.152.
 */
package com.dbteku.telecomstationid;

import com.dbteku.telecom.c.f;
import com.dbteku.telecom.c.k;
import com.dbteku.telecom.dualsim.DualSimManager;
import com.dbteku.telecom.models.Carrier;
import com.dbteku.telecom.models.CellSignal;
import com.dbteku.telecom.models.CellTower;
import com.dbteku.telecom.models.NetworkBand;
import com.dbteku.telecom.models.WorkerRole;
import com.dbteku.telecom.models.WorldLocation;
import com.dbteku.telecom.api.TelecomApi;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TelecomStationIdPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final String PLACEHOLDER = "%telecomstation_station_id%";
    private static final String SECOND_PLACEHOLDER = "%telecomstation_second_station_id%";
    private static final String STATION_LINE = "&7Stacja: &f" + PLACEHOLDER;
    private static final int TARGET_DISTANCE = 64;

    private final Properties ids = new Properties();
    private File idsFile;

    public void onEnable() {
        this.getDataFolder().mkdirs();
        this.idsFile = new File(this.getDataFolder(), "stations.properties");
        this.loadIds();
        Bukkit.getPluginManager().registerEvents(this, this);

        PluginCommand command = this.getCommand("ctid");
        if (command != null) {
            command.setExecutor(this);
        }

        this.registerPlaceholder();
        this.injectScoreboardLine();
        this.getServer().getScheduler().runTaskLater(this, () -> this.injectScoreboardLine(), 10L);
        this.getLogger().info("TelecomStationId 1.3.0 enabled.");
    }

    public void onDisable() {
        this.saveIds();
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().trim().split("\\s+");
        if (args.length != 3 || !this.matchesCommand(args[0], "ct")) {
            return;
        }

        Player player = event.getPlayer();
        if (!this.hasPermission(player)) {
            return;
        }

        String network = args[1];
        String stationId = args[2];

        if (!this.isValidId(stationId)) {
            player.sendMessage("ID stacji: tylko litery, cyfry, _ i -, maks. 24 znaki.");
            event.setCancelled(true);
            return;
        }

        Block block = this.findTargetBlock(player);
        if (block == null) {
            player.sendMessage("Nie znaleziono bloku stacji. Celuj bezpośrednio w blok sieci (maks. 64 bloki).");
            event.setCancelled(true);
            return;
        }

        if (!this.createTowerAt(player, network, block)) {
            event.setCancelled(true);
            return;
        }

        this.ids.setProperty(this.blockKey(block), stationId);
        this.saveIds();
        event.setCancelled(true);
        player.sendMessage("Utworzono stację " + network + " z ID " + stationId + ".");
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ctid")) {
            return false;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tylko gracz może ustawiać identyfikator stacji.");
            return true;
        }

        Player player = (Player)sender;
        if (!this.hasPermission(player)) {
            player.sendMessage("Nie masz uprawnień do ustawiania identyfikatora stacji.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("Użycie: /ctid <ID|CLEAR>");
            return true;
        }

        Block block = this.findTargetBlock(player);
        if (block == null) {
            player.sendMessage("Patrz bezpośrednio na blok stacji/sieci (maks. 64 bloki).");
            return true;
        }

        String key = this.blockKey(block);
        if (args[0].equalsIgnoreCase("CLEAR")) {
            this.ids.remove(key);
            this.saveIds();
            player.sendMessage("Usunięto ID stacji z tego bloku.");
            return true;
        }

        if (!this.isValidId(args[0])) {
            player.sendMessage("ID stacji: tylko litery, cyfry, _ i -, maks. 24 znaki.");
            return true;
        }

        this.ids.setProperty(key, args[0]);
        this.saveIds();
        player.sendMessage("Ustawiono ID stacji: " + args[0]);
        return true;
    }

    private boolean createTowerAt(Player player, String networkName, Block block) {
        NetworkBand band = k.a().a(networkName);
        if (band == null || band.getName() == null || !band.getName().equalsIgnoreCase(networkName)) {
            player.sendMessage("Nieznana sieć: " + networkName + ".");
            return false;
        }

        WorldLocation worldLocation = new WorldLocation(block.getLocation());
        f telecom = f.a();
        Carrier carrier = telecom.f(player.getName());

        if (carrier == null || carrier.isNull()) {
            carrier = telecom.h(player.getUniqueId().toString());
        }
        if (carrier == null || carrier.isNull()) {
            player.sendMessage("Nie znaleziono Twojej sieci/operatora.");
            return false;
        }

        boolean owner = carrier.getOwner() != null && carrier.getOwner().equalsIgnoreCase(player.getName());
        boolean engineer = carrier.hasWorkerWithRole(player.getUniqueId().toString(), WorkerRole.ENGINEER);

        if (!owner && !engineer) {
            player.sendMessage("Nie masz uprawnień do tworzenia stacji.");
            return false;
        }

        if (this.hasTowerOfType(carrier, worldLocation, networkName)) {
            player.sendMessage("Na tym bloku istnieje już stacja " + networkName + ".");
            return false;
        }

        int before = this.countTowersAt(carrier, worldLocation);
        telecom.a(player, worldLocation, carrier, networkName);
        int after = this.countTowersAt(carrier, worldLocation);

        if (after <= before) {
            player.sendMessage("Nie udało się utworzyć stacji " + networkName + ".");
            return false;
        }

        return true;
    }

    private boolean hasTowerOfType(Carrier carrier, WorldLocation location, String type) {
        Iterator<?> iterator = carrier.getTowers();
        while (iterator.hasNext()) {
            CellTower tower = (CellTower)iterator.next();
            if (tower == null || !location.equals(tower.getLocation()) || !type.equalsIgnoreCase(tower.getType())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private int countTowersAt(Carrier carrier, WorldLocation location) {
        int count = 0;
        Iterator<?> iterator = carrier.getTowers();
        while (iterator.hasNext()) {
            CellTower tower = (CellTower)iterator.next();
            if (tower == null || !location.equals(tower.getLocation())) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean hasPermission(Player player) {
        return player.isOp() || player.hasPermission("telecomfix.stationid");
    }

    private boolean isValidId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,24}") && !value.equalsIgnoreCase("CLEAR");
    }

    private Block findTargetBlock(Player player) {
        try {
            Block block = player.getTargetBlockExact(64);
            if (this.isNetworkBlock(block)) {
                return block;
            }
        } catch (Throwable ignored) {
        }

        try {
            @SuppressWarnings("deprecation")
            Block block = player.getTargetBlock(null, 64);
            if (this.isNetworkBlock(block)) {
                return block;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private boolean isNetworkBlock(Block block) {
        if (block == null || block.getType() == Material.AIR) {
            return false;
        }
        Material material = block.getType();
        return material == Material.IRON_BLOCK || k.a().a(material);
    }

    public String currentStationId(Player player) {
        try {
            CellSignal signal = TelecomApi.get().getCellSignal(player);
            return this.stationIdFromSignal(signal);
        } catch (Throwable throwable) {
            return "-";
        }
    }

    public String currentSecondStationId(Player player) {
        try {
            if (player == null) {
                return "-";
            }

            Carrier carrier = DualSimManager.getSecondCarrier(player.getUniqueId());
            if (carrier == null || carrier.isNull()) {
                return "-";
            }

            WorldLocation location = new WorldLocation(player.getLocation());
            CellTower tower = carrier.getBestTowerByBand(location);

            if (tower != null && !tower.isNull()) {
                double strength = tower.determineStrength(location);
                if (strength > 0.0) {
                    return this.idForTower(tower);
                }
            }

            if (k.a().l()) {
                Iterator<String> peers = carrier.getPeers();
                while (peers.hasNext()) {
                    Carrier peer = f.a().e(peers.next());
                    CellTower peerTower = peer.getBestTowerByBand(location);
                    if (peerTower == null || peerTower.isNull()) {
                        continue;
                    }
                    double strength = peerTower.determineStrength(location);
                    if (strength > 0.0) {
                        return this.idForTower(peerTower);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return "-";
    }

    private String stationIdFromSignal(CellSignal signal) {
        if (signal == null || signal.isNull() || !signal.hasSignal()) {
            return "-";
        }
        return this.idForTower(signal.getTower());
    }

    private String idForTower(CellTower tower) {
        if (tower == null || tower.isNull() || tower.getLocation() == null) {
            return "-";
        }
        String id = this.ids.getProperty(this.blockKey(tower.getLocation()));
        return id == null || id.isEmpty() ? "-" : id;
    }

    private void registerPlaceholder() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (plugin != null) {
                new TelecomStationIdPlugin$StationPlaceholder(this).register();
            }
        } catch (Throwable throwable) {
            this.getLogger().warning("Nie udało się zarejestrować placeholdera: " + throwable.getMessage());
        }
    }

    private void injectScoreboardLine() {
        try {
            if (Bukkit.getPluginManager().getPlugin("RealScoreboard") == null) {
                return;
            }

            Class<?> apiClass = Class.forName("joserodpt.realscoreboard.api.RealScoreboardAPI");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            if (api == null) {
                return;
            }

            Method getManager = apiClass.getMethod("getScoreboardManagerAPI");
            Object manager = getManager.invoke(api);
            if (manager == null) {
                return;
            }

            Method getScoreboards = manager.getClass().getMethod("getScoreboards");
            Object scoreboards = getScoreboards.invoke(manager);
            if (!(scoreboards instanceof Iterable<?>)) {
                return;
            }

            for (Object scoreboard : (Iterable<?>)scoreboards) {
                if (scoreboard == null) {
                    continue;
                }

                Method getLines = scoreboard.getClass().getMethod("getLines");
                Object linesObject = getLines.invoke(scoreboard);
                if (!(linesObject instanceof List<?>)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>)linesObject;

                boolean alreadyThere = false;
                int signalIndex = -1;
                int fallbackSignal = -1;

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line == null) {
                        continue;
                    }

                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.contains(PLACEHOLDER.toLowerCase(Locale.ROOT)) || lower.contains("stacja:")) {
                        alreadyThere = true;
                    }

                    if (lower.contains("%telecommulti_phone_signal_1%")) {
                        signalIndex = i;
                    } else if (lower.contains("%telecommulti_phone_signal_2%")
                        || lower.contains("%telecom_phone_signal%")
                        || lower.contains("%carrier_signal%")
                        || lower.contains("%telecom_signal%")) {
                        if (fallbackSignal < 0) {
                            fallbackSignal = i;
                        }
                    }
                }

                if (signalIndex < 0) {
                    signalIndex = fallbackSignal;
                }

                if (alreadyThere || signalIndex < 0) {
                    continue;
                }

                lines.add(signalIndex + 1, STATION_LINE);

                try {
                    scoreboard.getClass().getMethod("saveScoreboard").invoke(scoreboard);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable throwable) {
            this.getLogger().warning("Nie udało się dodać linii Stacja do RealScoreboard: " + throwable.getMessage());
        }
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String blockKey(WorldLocation location) {
        return location.getWorldName() + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ();
    }

    private boolean matchesCommand(String command, String expected) {
        if (command == null || !command.startsWith("/")) {
            return false;
        }
        String value = command.substring(1);
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        return value.equalsIgnoreCase(expected);
    }

    private void loadIds() {
        if (!this.idsFile.exists()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(
            new FileInputStream(this.idsFile), StandardCharsets.UTF_8)) {
            this.ids.load(reader);
        } catch (IOException exception) {
            this.getLogger().warning("Nie można wczytać stations.properties: " + exception.getMessage());
        }
    }

    private void saveIds() {
        if (this.idsFile == null) {
            return;
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
            new FileOutputStream(this.idsFile), StandardCharsets.UTF_8)) {
            this.ids.store(writer, "Telecom Station IDs");
        } catch (IOException exception) {
            this.getLogger().warning("Nie można zapisać stations.properties: " + exception.getMessage());
        }
    }
}
