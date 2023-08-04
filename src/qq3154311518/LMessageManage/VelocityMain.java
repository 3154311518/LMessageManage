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

import javax.inject.Inject;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;

@Plugin(id = "lmessagemanage")
public class VelocityMain {
    private final ProxyServer server;

    private Properties config, WhitelistCommand, BlacklistCommand, IncludeTipMessage, ViolationIncludeMessage;
    private List<String> ViolationRegexMessage;
    HashMap<String, Long> CooldownMessage = new HashMap();

    @Inject
    public VelocityMain(ProxyServer server) {
        this.server = server;

        server.getCommandManager().register("lmmr", new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                reloadConfig();
                source.sendMessage(Component.text(config.getProperty("Message.BungeeMessageManageReload")));
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("op");
            }
        }, "lmmr");

        reloadConfig();
    }

    public void reloadConfig() {
        try {
            File dataFolder = new File("plugins/LMessageManage");
            if (!dataFolder.exists()) {
                dataFolder.mkdir();
            }

            String[] fileNames = {"config.properties", "WhitelistCommand.properties", "BlacklistCommand.properties", "IncludeTipMessage.properties", "ViolationIncludeMessage.properties"};
            Properties[] properties = {config, WhitelistCommand, BlacklistCommand, IncludeTipMessage, ViolationIncludeMessage};

            for (int i = 0; i < fileNames.length; i++) {
                String fileName = fileNames[i];
                File file = new File(dataFolder, fileName);
                if (!file.exists()) {
                    try (InputStream in = getClass().getClassLoader().getResourceAsStream(fileName)) {
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

            File File = new File(dataFolder, "ViolationRegexMessage.properties");
            if (!File.exists()) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("ViolationRegexMessage.properties")) {
                    Files.copy(in, File.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            ViolationRegexMessage = Files.readAllLines(File.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(config);
    }

    @Subscribe
    public void PlayerChatEvent(PlayerChatEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (player.hasPermission("op")) return;
        String message = event.getMessage();

        if (CooldownMessage.containsKey(player.getUsername())) {
            if (System.currentTimeMillis() - CooldownMessage.get(player.getUsername()) < Integer.parseInt(config.getProperty("Settings.CooldownMessage"))) {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                player.sendMessage(Component.text(config.getProperty("Message.CooldownMessage")));
                return;
            }
        }

        CooldownMessage.put(player.getUsername(), System.currentTimeMillis());

        String[] disableServers = config.getProperty("Settings.DisableServer").split(",");
        for (String server : disableServers) {
            if (player.getCurrentServer().get().getServerInfo().getName().equals(server)) {
                return;
            }
        }

        for (String include : IncludeTipMessage.stringPropertyNames()) {
            String value = IncludeTipMessage.getProperty(include);
            if (message.toLowerCase().contains(include)) {
                player.sendMessage(Component.text(value));
                break;
            }
        }

        String processedMessage = message.replaceAll("\\s+", "");

        for (String include : ViolationIncludeMessage.stringPropertyNames()) {
            for (String regex : ViolationRegexMessage) {
                if (processedMessage.toLowerCase().contains(include) || Pattern.compile(regex).matcher(processedMessage).find()) {
                    event.setResult(PlayerChatEvent.ChatResult.denied());
                    player.sendMessage(Component.text(config.getProperty("Message.ViolationMessage")));
        
                    String adminMessage = config.getProperty("Message.AdminReceivesMessages")
                            .replace("%player%", player.getUsername())
                            .replace("%message%", message);
                    for (Player p : server.getAllPlayers()) {
                        if (p.hasPermission("op")) {
                            p.sendMessage(Component.text(adminMessage));
                        }
                    }
        
                    return;
                }
            }
        }
    }

    @Subscribe
    public void CommandExecuteEvent(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;
        Player player = (Player) event.getCommandSource();
        if (player.hasPermission("op")) return;
        String message = "/"+event.getCommand();

        for (String command : WhitelistCommand.stringPropertyNames()) {
            if (message.toLowerCase().startsWith(command)) {
                return;
            }
        }

        if (CooldownMessage.containsKey(player.getUsername())) {
            if (System.currentTimeMillis() - CooldownMessage.get(player.getUsername()) < Integer.parseInt(config.getProperty("Settings.CooldownMessage"))) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
                player.sendMessage(Component.text(config.getProperty("Message.CooldownMessage")));
                return;
            }
        }

        CooldownMessage.put(player.getUsername(), System.currentTimeMillis());

        String[] disableServers = config.getProperty("Settings.DisableServer").split(",");
        for (String server : disableServers) {
            if (player.getCurrentServer().get().getServerInfo().getName().equals(server)) {
                return;
            }
        }

        for (String command : BlacklistCommand.stringPropertyNames()) {
            if (message.toLowerCase().startsWith(command)) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
                player.sendMessage(Component.text(config.getProperty("Message.BlacklistCommand")));

                String adminMessage = config.getProperty("Message.AdminReceivesMessages")
                        .replace("%player%", player.getUsername())
                        .replace("%message%", message);
                for (Player p : server.getAllPlayers()) {
                    if (p.hasPermission("op")) {
                        p.sendMessage(Component.text(adminMessage));
                    }
                }

                return;
            }
        }

        for (String include : IncludeTipMessage.stringPropertyNames()) {
            String value = IncludeTipMessage.getProperty(include);
            if (message.toLowerCase().contains(include)) {
                player.sendMessage(Component.text(value));
                break;
            }
        }

        String processedMessage = message.replaceAll("\\s+", "");

        for (String include : ViolationIncludeMessage.stringPropertyNames()) {
            for (String regex : ViolationRegexMessage) {
                if (processedMessage.toLowerCase().contains(include) || Pattern.compile(regex).matcher(processedMessage).find()) {
                    event.setResult(CommandExecuteEvent.CommandResult.denied());
                    player.sendMessage(Component.text(config.getProperty("Message.ViolationMessage")));
        
                    String adminMessage = config.getProperty("Message.AdminReceivesMessages")
                            .replace("%player%", player.getUsername())
                            .replace("%message%", message);
                    for (Player p : server.getAllPlayers()) {
                        if (p.hasPermission("op")) {
                            p.sendMessage(Component.text(adminMessage));
                        }
                    }
        
                    return;
                }
            }
        }
    }
}