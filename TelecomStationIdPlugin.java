/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.dbteku.telecom.api.TelecomApi
 *  com.dbteku.telecom.c.f
 *  com.dbteku.telecom.c.k
 *  com.dbteku.telecom.models.Carrier
 *  com.dbteku.telecom.models.CellSignal
 *  com.dbteku.telecom.models.CellTower
 *  com.dbteku.telecom.models.NetworkBand
 *  com.dbteku.telecom.models.WorkerRole
 *  com.dbteku.telecom.models.WorldLocation
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.dbteku.telecomstationid;

import com.dbteku.telecom.api.TelecomApi;
import com.dbteku.telecom.c.f;
import com.dbteku.telecom.c.k;
import com.dbteku.telecom.models.Carrier;
import com.dbteku.telecom.models.CellSignal;
import com.dbteku.telecom.models.CellTower;
import com.dbteku.telecom.models.NetworkBand;
import com.dbteku.telecom.models.WorkerRole;
import com.dbteku.telecom.models.WorldLocation;
import com.dbteku.telecomstationid.TelecomStationIdPlugin$StationPlaceholder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
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

public final class TelecomStationIdPlugin
extends JavaPlugin
implements Listener,
CommandExecutor {
    private static final String PLACEHOLDER = "%telecomstation_station_id%";
    private static final String STATION_LINE = "&7Stacja: &f%telecomstation_station_id%";
    private static final int TARGET_DISTANCE = 64;
    private final Properties ids = new Properties();
    private File idsFile;

    public void onEnable() {
        this.getDataFolder().mkdirs();
        this.idsFile = new File(this.getDataFolder(), "stations.properties");
        this.loadIds();
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this);
        PluginCommand pluginCommand = this.getCommand("ctid");
        if (pluginCommand != null) {
            pluginCommand.setExecutor((CommandExecutor)this);
        }
        this.registerPlaceholder();
        this.injectScoreboardLine();
        this.getLogger().info("TelecomStationId 1.2.0 enabled.");
    }

    public void onDisable() {
        this.saveIds();
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        String[] stringArray = playerCommandPreprocessEvent.getMessage().trim().split("\\s+");
        if (stringArray.length != 3 || !this.matchesCommand(stringArray[0], "ct")) {
            return;
        }
        Player player = playerCommandPreprocessEvent.getPlayer();
        if (!this.hasPermission(player)) {
            return;
        }
        String string = stringArray[1];
        String string2 = stringArray[2];
        if (!this.isValidId(string2)) {
            player.sendMessage("ID stacji: tylko litery, cyfry, _ i -, maks. 24 znaki.");
            playerCommandPreprocessEvent.setCancelled(true);
            return;
        }
        Block block = this.findTargetBlock(player);
        if (block == null) {
            player.sendMessage("Nie znaleziono bloku stacji. Celuj bezpo\u015brednio w blok sieci (maks. 64 bloki).");
            playerCommandPreprocessEvent.setCancelled(true);
            return;
        }
        if (!this.createTowerAt(player, string, block)) {
            playerCommandPreprocessEvent.setCancelled(true);
            return;
        }
        this.ids.setProperty(this.blockKey(block), string2);
        this.saveIds();
        playerCommandPreprocessEvent.setCancelled(true);
        player.sendMessage("Utworzono stacj\u0119 " + string + " z ID " + string2 + ".");
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!command.getName().equalsIgnoreCase("ctid")) {
            return false;
        }
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Tylko gracz mo\u017ce ustawia\u0107 identyfikator stacji.");
            return true;
        }
        Player player = (Player)commandSender;
        if (!this.hasPermission(player)) {
            player.sendMessage("Nie masz uprawnie\u0144 do ustawiania identyfikatora stacji.");
            return true;
        }
        if (stringArray.length != 1) {
            player.sendMessage("U\u017cycie: /ctid <ID|CLEAR>");
            return true;
        }
        Block block = this.findTargetBlock(player);
        if (block == null) {
            player.sendMessage("Patrz bezpo\u015brednio na blok stacji/sieci (maks. 64 bloki).");
            return true;
        }
        String string2 = this.blockKey(block);
        if (stringArray[0].equalsIgnoreCase("CLEAR")) {
            this.ids.remove(string2);
            this.saveIds();
            player.sendMessage("Usuni\u0119to ID stacji z tego bloku.");
            return true;
        }
        if (!this.isValidId(stringArray[0])) {
            player.sendMessage("ID stacji: tylko litery, cyfry, _ i -, maks. 24 znaki.");
            return true;
        }
        this.ids.setProperty(string2, stringArray[0]);
        this.saveIds();
        player.sendMessage("Ustawiono ID stacji: " + stringArray[0]);
        return true;
    }

    private boolean createTowerAt(Player player, String string, Block block) {
        NetworkBand networkBand = k.a().a(string);
        if (networkBand == null || networkBand.getName() == null || !networkBand.getName().equalsIgnoreCase(string)) {
            player.sendMessage("Nieznana sie\u0107: " + string + ".");
            return false;
        }
        WorldLocation worldLocation = new WorldLocation(block.getLocation());
        f f2 = f.a();
        Carrier carrier = f2.f(player.getName());
        if (carrier == null || carrier.isNull()) {
            carrier = f2.h(player.getUniqueId().toString());
        }
        if (carrier == null || carrier.isNull()) {
            player.sendMessage("Nie znaleziono Twojej sieci/operatora.");
            return false;
        }
        boolean bl = carrier.getOwner() != null && carrier.getOwner().equalsIgnoreCase(player.getName());
        boolean bl2 = carrier.hasWorkerWithRole(player.getUniqueId().toString(), WorkerRole.ENGINEER);
        if (!bl && !bl2) {
            player.sendMessage("Nie masz uprawnie\u0144 do tworzenia stacji.");
            return false;
        }
        if (this.hasTowerOfType(carrier, worldLocation, string)) {
            player.sendMessage("Na tym bloku istnieje ju\u017c stacja " + string + ".");
            return false;
        }
        int n = this.countTowersAt(carrier, worldLocation);
        f2.a(player, worldLocation, carrier, string);
        int n2 = this.countTowersAt(carrier, worldLocation);
        if (n2 <= n) {
            player.sendMessage("Nie uda\u0142o si\u0119 utworzy\u0107 stacji " + string + ".");
            return false;
        }
        return true;
    }

    private boolean hasTowerOfType(Carrier carrier, WorldLocation worldLocation, String string) {
        Iterator iterator = carrier.getTowers();
        while (iterator.hasNext()) {
            CellTower cellTower = (CellTower)iterator.next();
            if (cellTower == null || !worldLocation.equals((Object)cellTower.getLocation()) || !string.equalsIgnoreCase(cellTower.getType())) continue;
            return true;
        }
        return false;
    }

    private int countTowersAt(Carrier carrier, WorldLocation worldLocation) {
        int n = 0;
        Iterator iterator = carrier.getTowers();
        while (iterator.hasNext()) {
            CellTower cellTower = (CellTower)iterator.next();
            if (cellTower == null || !worldLocation.equals((Object)cellTower.getLocation())) continue;
            ++n;
        }
        return n;
    }

    private boolean hasPermission(Player player) {
        return player.isOp() || player.hasPermission("telecomfix.stationid");
    }

    private boolean isValidId(String string) {
        return string != null && string.matches("[A-Za-z0-9_-]{1,24}") && !string.equalsIgnoreCase("CLEAR");
    }

    private Block findTargetBlock(Player player) {
        Block block;
        try {
            block = player.getTargetBlockExact(64);
            if (this.isNetworkBlock(block)) {
                return block;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            block = null;
            Block block2 = player.getTargetBlock((Set)block, 64);
            if (this.isNetworkBlock(block2)) {
                return block2;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
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
            CellSignal cellSignal = TelecomApi.get().getCellSignal(player);
            if (cellSignal == null || cellSignal.isNull() || !cellSignal.hasSignal()) {
                return "-";
            }
            CellTower cellTower = cellSignal.getTower();
            if (cellTower == null || cellTower.isNull() || cellTower.getLocation() == null) {
                return "-";
            }
            String string = this.ids.getProperty(this.blockKey(cellTower.getLocation()));
            return string == null || string.isEmpty() ? "-" : string;
        }
        catch (Throwable throwable) {
            return "-";
        }
    }

    private void registerPlaceholder() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (plugin != null) {
                new TelecomStationIdPlugin$StationPlaceholder(this).register();
            }
        }
        catch (Throwable throwable) {
            this.getLogger().warning("Nie uda\u0142o si\u0119 zarejestrowa\u0107 placeholdera: " + throwable.getMessage());
        }
    }

    private void injectScoreboardLine() {
        try {
            if (Bukkit.getPluginManager().getPlugin("RealScoreboard") == null) {
                return;
            }
            Class<?> clazz = Class.forName("joserodpt.realscoreboard.api.RealScoreboardAPI");
            Method method = clazz.getMethod("getInstance", new Class[0]);
            Object object = method.invoke(null, new Object[0]);
            if (object == null) {
                return;
            }
            Method method2 = clazz.getMethod("getScoreboardManagerAPI", new Class[0]);
            Object object2 = method2.invoke(object, new Object[0]);
            if (object2 == null) {
                return;
            }
            Method method3 = object2.getClass().getMethod("getScoreboards", new Class[0]);
            Object object3 = method3.invoke(object2, new Object[0]);
            if (!(object3 instanceof Iterable)) {
                return;
            }
            for (Object t : (Iterable)object3) {
                Method method4;
                Object object4;
                if (t == null || !((object4 = (method4 = t.getClass().getMethod("getLines", new Class[0])).invoke(t, new Object[0])) instanceof List)) continue;
                List list = (List)object4;
                boolean bl = false;
                int n = -1;
                for (int i = 0; i < list.size(); ++i) {
                    String string = (String)list.get(i);
                    if (string == null) continue;
                    String string2 = string.toLowerCase(Locale.ROOT);
                    if (string2.contains(PLACEHOLDER.toLowerCase(Locale.ROOT)) || string2.contains("stacja:")) {
                        bl = true;
                    }
                    if (!string2.contains("%telecom_phone_signal%")
                            && !string2.contains("%carrier_signal%")
                            && !string2.contains("%telecom_signal%")
                            && !string2.contains("%telecommulti_phone_signal_1%")
                            && !string2.contains("%telecommulti_phone_signal_2%")) continue;
                    n = i;
                }
                if (bl || n < 0 || n + 1 > list.size()) continue;
                list.add(n + 1, STATION_LINE);
                try {
                    t.getClass().getMethod("saveScoreboard", new Class[0]).invoke(t, new Object[0]);
                }
                catch (Throwable throwable) {}
            }
        }
        catch (Throwable throwable) {
            this.getLogger().warning("Nie uda\u0142o si\u0119 doda\u0107 linii Stacja do RealScoreboard: " + throwable.getMessage());
        }
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String blockKey(WorldLocation worldLocation) {
        return worldLocation.getWorldName() + ":" + worldLocation.getX() + ":" + worldLocation.getY() + ":" + worldLocation.getZ();
    }

    private boolean matchesCommand(String string, String string2) {
        if (string == null || !string.startsWith("/")) {
            return false;
        }
        String string3 = string.substring(1);
        int n = string3.indexOf(58);
        if (n >= 0) {
            string3 = string3.substring(n + 1);
        }
        return string3.equalsIgnoreCase(string2);
    }

    private void loadIds() {
        if (!this.idsFile.exists()) {
            return;
        }
        try (InputStreamReader inputStreamReader = new InputStreamReader((InputStream)new FileInputStream(this.idsFile), StandardCharsets.UTF_8);){
            this.ids.load(inputStreamReader);
        }
        catch (IOException iOException) {
            this.getLogger().warning("Nie mo\u017cna wczyta\u0107 stations.properties: " + iOException.getMessage());
        }
    }

    private void saveIds() {
        if (this.idsFile == null) {
            return;
        }
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter((OutputStream)new FileOutputStream(this.idsFile), StandardCharsets.UTF_8);){
            this.ids.store(outputStreamWriter, "Telecom Station IDs");
        }
        catch (IOException iOException) {
            this.getLogger().warning("Nie mo\u017cna zapisa\u0107 stations.properties: " + iOException.getMessage());
        }
    }
}

