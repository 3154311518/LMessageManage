package qq3154311518.LMessageManage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class BungeeMain extends Plugin implements Listener {
    private Properties config, WhitelistCommand, BlacklistCommand, IncludeTipMessage, ViolationIncludeMessage;
    private List<String> ViolationRegexMessage;
    HashMap<String, Long> CooldownMessage = new HashMap();

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new Command("lmmr", "op") {
            public void execute(CommandSender sender, String[] args) {
                reloadConfig();
                sender.sendMessage(config.getProperty("Message.BungeeMessageManageReload"));
            }
        });
        reloadConfig();
    }

    public void reloadConfig() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }
            
            String[] fileNames = {"config.properties", "WhitelistCommand.properties", "BlacklistCommand.properties", "IncludeTipMessage.properties", "ViolationIncludeMessage.properties"};
            Properties[] properties = {config, WhitelistCommand, BlacklistCommand, IncludeTipMessage, ViolationIncludeMessage};
            
            for (int i = 0; i < fileNames.length; i++) {
                String fileName = fileNames[i];
                File file = new File(getDataFolder(), fileName);
                if (!file.exists()) {
                    try (InputStream in = getResourceAsStream(fileName)) {
                        Files.copy(in, file.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                
                properties[i] = new Properties();
                try (InputStream input = new FileInputStream(file); InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    properties[i].load(reader);
                }
            }
            
            config = properties[0];
            WhitelistCommand = properties[1];
            BlacklistCommand = properties[2];
            IncludeTipMessage = properties[3];
            ViolationIncludeMessage = properties[4];

            File File = new File(getDataFolder(), "ViolationRegexMessage.properties");
            if (!File.exists()) {
                try (InputStream in = getResourceAsStream("ViolationRegexMessage.properties")) {
                    Files.copy(in, File.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            ViolationRegexMessage = Files.readAllLines(File.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (event.isCancelled()) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (player.hasPermission("op")) return;
        String message = event.getMessage();

        for (String command : WhitelistCommand.stringPropertyNames()) {
            if (message.toLowerCase().startsWith(command)) {
                return;
            }
        }
    
        if (CooldownMessage.containsKey(player.getName())) {
            if (System.currentTimeMillis() - CooldownMessage.get(player.getName()) < Integer.parseInt(config.getProperty("Settings.CooldownMessage"))) {
                event.setCancelled(true);
                player.sendMessage(config.getProperty("Message.CooldownMessage"));
                return;
            }
        }
    
        CooldownMessage.put(player.getName(), System.currentTimeMillis());

        String[] disableServers = config.getProperty("Settings.DisableServer").split(",");
        for (String server : disableServers) {
            if (player.getServer().getInfo().getName().equals(server)) {
                return;
            }
        }

        for (String command : BlacklistCommand.stringPropertyNames()) {
            if (message.toLowerCase().startsWith(command)) {
                event.setCancelled(true);
                player.sendMessage(config.getProperty("Message.BlacklistCommand"));

                String adminMessage = config.getProperty("Message.AdminReceivesMessages")
                        .replace("%player%", player.getName())
                        .replace("%message%", message);
                for (ProxiedPlayer p : getProxy().getPlayers()) {
                    if (p.hasPermission("op")) {
                        p.sendMessage(adminMessage);
                    }
                }

                return;
            }
        }
    
        for (String include : IncludeTipMessage.stringPropertyNames()) {
            String value = IncludeTipMessage.getProperty(include);
            if (message.toLowerCase().contains(include)) {
                player.sendMessage(value);
                break;
            }
        }
    
        String processedMessage = message.replaceAll("\\s+", "");

        for (String include : ViolationIncludeMessage.stringPropertyNames()) {
            for (String regex : ViolationRegexMessage) {
                if (processedMessage.toLowerCase().contains(include) || Pattern.compile(regex).matcher(processedMessage).find()) {
                    event.setCancelled(true);
                    player.sendMessage(config.getProperty("Message.ViolationMessage"));
        
                    String adminMessage = config.getProperty("Message.AdminReceivesMessages")
                            .replace("%player%", player.getName())
                            .replace("%message%", message);
                    for (ProxiedPlayer p : getProxy().getPlayers()) {
                        if (p.hasPermission("op")) {
                            p.sendMessage(adminMessage);
                        }
                    }
        
                    return;
                }
            }
        }
    }
}