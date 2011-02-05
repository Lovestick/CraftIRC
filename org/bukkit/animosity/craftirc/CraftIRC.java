package org.bukkit.animosity.craftirc;

import java.io.File;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Server;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.animosity.craftirc.CraftIRCListener;
import org.bukkit.animosity.craftirc.Minebot;
import org.bukkit.util.config.CraftIRCConfigurationNode;

/**
 * @author Animosity
 * @author ricin
 * @author Protected
 */

public class CraftIRC extends JavaPlugin {
	public static final String NAME = "CraftIRC";
	public static String VERSION;

	protected static final Logger log = Logger.getLogger("Minecraft");
	
	private final CraftIRCListener listener = new CraftIRCListener(this);
	private ArrayList<Minebot> instances;
	private boolean debug;
	
	private ArrayList<CraftIRCConfigurationNode> bots;
	private ArrayList<CraftIRCConfigurationNode> colormap;
	
	public CraftIRC(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin,
			ClassLoader cLoader) {
		super(pluginLoader, instance, desc, folder, plugin, cLoader);
		VERSION = desc.getVersion();
	}

	@SuppressWarnings("unchecked")
	public void onEnable() {
		//Parsing node lists, because Bukkit doesn't do it yet.
		ArrayList<Object> botList = new ArrayList<Object>(getConfiguration().getList("bots"));
		for (Iterator<Object> it = botList.iterator(); it.hasNext();) {
			Map<String, Object> bot = (Map<String, Object>)it.next();
			CraftIRCConfigurationNode botnode = new CraftIRCConfigurationNode(bot); 
			bots.add(botnode);
			ArrayList<CraftIRCConfigurationNode> chans = new ArrayList<CraftIRCConfigurationNode>();
			ArrayList<String> chanNames = new ArrayList<String>();
			ArrayList<Object> chanList = new ArrayList<Object>(botnode.getList("channels"));
			for (Iterator<Object> iti = chanList.iterator(); iti.hasNext();) {
				CraftIRCConfigurationNode chanNode = new CraftIRCConfigurationNode((Map<String, Object>)iti.next());
				chans.add(chanNode);
				chanNames.add(chanNode.getString("name"));
			}
			bot.put("chanlist", chans);
			bot.put("channames", chanNames);
		}
		ArrayList<Object> colorList = new ArrayList<Object>(getConfiguration().getList("colormap"));
		for (Iterator<Object> it = colorList.iterator(); it.hasNext();) {
			colormap.add(new CraftIRCConfigurationNode((Map<String, Object>)it.next()));
		}
		
		//Event listeners
		getServer().getPluginManager().registerEvent(Event.Type.PLAYER_JOIN, listener, Priority.Monitor, this);
		getServer().getPluginManager().registerEvent(Event.Type.PLAYER_QUIT, listener, Priority.Monitor, this);
		getServer().getPluginManager().registerEvent(Event.Type.PLAYER_COMMAND, listener, Priority.Monitor, this);
		getServer().getPluginManager().registerEvent(Event.Type.PLAYER_CHAT, listener, Priority.Monitor, this);
		
		//Create bots
		for (int i = 0; i < bots.size(); i++)
			instances.add(new Minebot(this, i).init());
		
		log.info(NAME + " Enabled.");
		
		setDebug(cDebug());
	}

	public void onDisable() {
		
		//Disconnect bots
		for (int i = 0; i < bots.size(); i++)
			instances.get(i).disconnect();
		
		log.info(NAME + " Disabled.");
	}
	
	public void sendMessage(String message, String tag, String event) {
		for (int i = 0; i < bots.size(); i++) {
			ArrayList<String> chans = cBotChannels(i);
			Iterator<String> it = chans.iterator();
			while (it.hasNext()) {
				String chan = it.next();
				if ((tag == null || cChanCheckTag(tag, i, chan))
						&& (event == null || cEvents(event, i, chan)))
					instances.get(i).sendMessage(chan, message);
			}
		}
	}
	
	public ArrayList<String> ircUserLists (String tag) {
		ArrayList<String> result = new ArrayList<String>();
		if (tag == null) return result;
		for (int i = 0; i < bots.size(); i++) {
			ArrayList<String> chans = cBotChannels(i);
			Iterator<String> it = chans.iterator();
			while (it.hasNext()) {
				String chan = it.next();
				if (cChanCheckTag(tag, i, chan))
					result.add(Util.getIrcUserList(instances.get(i), chan));
			}
		}
		return result;
	}
	
	public void noticeAdmins(String message) {
		for (int i = 0; i < bots.size(); i++) {
			ArrayList<String> chans = cBotChannels(i);
			Iterator<String> it = chans.iterator();
			while (it.hasNext()) {
				String chan = it.next();
				if (cChanAdmin(i, chan))
					instances.get(i).sendNotice(chan, message);
			}
		}
	}
	
	public void setDebug(boolean d) {
		debug = d;

		for (int i = 0; i < bots.size(); i++)
			instances.get(i).setVerbose(true);
		
		log.info(NAME + " DEBUG [" + (d ? "ON" : "OFF") + "]");
	}

	public boolean isDebug() {
		return debug;
	}
		
	@SuppressWarnings("unchecked")
	private CraftIRCConfigurationNode getChanNode(int bot, String channel) {
		ArrayList<CraftIRCConfigurationNode> botChans = (ArrayList<CraftIRCConfigurationNode>)bots.get(bot).getProperty("chanlist");
		for (Iterator<CraftIRCConfigurationNode> it = botChans.iterator(); it.hasNext(); ) {
			CraftIRCConfigurationNode chan = it.next();
			if (chan.getString("name").equalsIgnoreCase(channel)) return chan;
		}
		return null;
	}
	
	public boolean cDebug() {
		return getConfiguration().getBoolean("settings.debug", false);
	}
	
	public String cAdminsCmd() {
		return getConfiguration().getString("settings.admins-cmd", "/admins!");
	}
	
	public ArrayList<String> cConsoleCommands() {
		return new ArrayList<String>(getConfiguration().getStringList("settings.console-commands", null));
	}
	
	public ArrayList<String> cIgnoredPrefixes(String source) {
		return new ArrayList<String>(getConfiguration().getStringList("settings.ignored-prefixes." + source, null));
	}
	
	public int cHold(String eventType) {
		return getConfiguration().getInt("settings.hold-after-enable." + eventType, 0);
	}
	
	public String cFormatting(String eventType, int bot, String channel) {
		CraftIRCConfigurationNode source = getChanNode(bot, channel);
		if (source == null || source.getString("formatting." + eventType) == null)
			source = bots.get(bot);
		if (source == null || source.getString("formatting." + eventType) == null)
			return getConfiguration().getString("settings.formatting." + eventType, "MESSAGE FORMATTING MISSING");
		else
			return source.getString("formatting." + eventType);
	}
	
	public boolean cEvents(String eventType, int bot, String channel) {
		CraftIRCConfigurationNode source = null;
		boolean def = (eventType.equalsIgnoreCase("game-to-irc.all-chat") || eventType.equalsIgnoreCase("irc-to-game.all-chat")
				? true : false);
		if (channel != null)
			source = getChanNode(bot, channel);
		if ((source == null || source.getProperty("events." + eventType) == null) && bot > -1)
			source = bots.get(bot);
		if (source == null || source.getProperty("events." + eventType) == null)
			return getConfiguration().getBoolean("settings.events." + eventType, def);
		else
			return source.getBoolean("events." + eventType, false);
	}

	public int cColorIrcFromGame(String game) {
		CraftIRCConfigurationNode color;
		Iterator<CraftIRCConfigurationNode> it = colormap.iterator();
		while (it.hasNext()) {
			color = it.next(); 
			if (color.getString("game").equals(game))
				return color.getInt("irc", cColorIrcFromName("foreground")); 
		}
		return cColorIrcFromName("foreground");
	}
	
	public int cColorIrcFromName(String name) {
		CraftIRCConfigurationNode color;
		Iterator<CraftIRCConfigurationNode> it = colormap.iterator();
		while (it.hasNext()) {
			color = it.next(); 
			if (color.getString("name").equalsIgnoreCase(name) && color.getProperty("irc") != null)
				return color.getInt("irc", 1); 
		}
		if (name.equalsIgnoreCase("foreground"))
			return 1;
		else
			return cColorIrcFromName("foreground");
	}
	
	public String cColorGameFromIrc(int irc) {
		CraftIRCConfigurationNode color;
		Iterator<CraftIRCConfigurationNode> it = colormap.iterator();
		while (it.hasNext()) {
			color = it.next(); 
			if (color.getInt("irc", -1) == irc)
				return color.getString("game", cColorGameFromName("foreground"));
		}
		return cColorGameFromName("foreground");
	}
	
	public String cColorGameFromName(String name) {
		CraftIRCConfigurationNode color;
		Iterator<CraftIRCConfigurationNode> it = colormap.iterator();
		while (it.hasNext()) {
			color = it.next(); 
			if (color.getString("name").equalsIgnoreCase(name) && color.getProperty("game") != null)
				return color.getString("game", "�f"); 
		}
		if (name.equalsIgnoreCase("foreground"))
			return "�f";
		else
			return cColorGameFromName("foreground");
	}
	
	public ArrayList<String> cBotChannels(int bot) {
		return new ArrayList<String>(bots.get(bot).getStringList("channames", null));
	}
	
	public String cBotNickname(int bot) {
		return bots.get(bot).getString("nickname", "CraftIRCbot");
	}
	
	public String cBotServer(int bot) {
		return bots.get(bot).getString("server", "irc.esper.net");
	}
	
	public int cBotPort(int bot) {
		return bots.get(bot).getInt("port", 6667);
	}
	
	public String cBotLogin(int bot) {
		return bots.get(bot).getString("userident", "");
	} 
	
	public String cBotPassword(int bot) {
		return bots.get(bot).getString("serverpass", "");
	}
	
	public boolean cBotSsl(int bot) {
		return bots.get(bot).getBoolean("ssl", false);
	}
	
	public int cBotTimeout(int bot) {
		return bots.get(bot).getInt("timeout", 5000);
	}
	
	public int cBotMessageDelay(int bot) {
		return bots.get(bot).getInt("message-delay", 1000);
	}
	
	public String cCommandPrefix(int bot) {
		return bots.get(bot).getString("command-prefix", getConfiguration().getString("settings.command-prefix", "."));
	}
	
	public ArrayList<String> cBotAdminPrefixes(int bot) {
		return new ArrayList<String>(bots.get(bot).getStringList("admin-prefixes", null));
	}
	
	public ArrayList<String> cBotIgnoredUsers(int bot) {
		return new ArrayList<String>(bots.get(bot).getStringList("ignored-users", null));
	}
	
	public String cBotAuthMethod(int bot) {
		return bots.get(bot).getString("auth.method", "nickserv");
	}
	
	public String cBotAuthUsername(int bot) {
		return bots.get(bot).getString("auth.username", "");
	}
	
	public String cBotAuthPassword(int bot) {
		return bots.get(bot).getString("auth.password", "");
	}
	
	public ArrayList<String> cBotOnConnect(int bot) {
		return new ArrayList<String>(bots.get(bot).getStringList("on-connect", null));
	}
	
	public String cChanName(int bot, String channel) {
		return getChanNode(bot, channel).getString("name", "#changeme");
	}
	
	public String cChanPassword(int bot, String channel) {
		return getChanNode(bot, channel).getString("password", "");
	}
	
	public boolean cChanAdmin(int bot, String channel) {
		return getChanNode(bot, channel).getBoolean("admin", false);
	}
	
	public ArrayList<String> cChanOnJoin(int bot, String channel) {
		return new ArrayList<String>(getChanNode(bot, channel).getStringList("on-join", null));
	}
	
	public boolean cChanChatColors(int bot, String channel) {
		return getChanNode(bot, channel).getBoolean("chat-colors", true);
	}
	
	public boolean cChanNameColors(int bot, String channel) {
		return getChanNode(bot, channel).getBoolean("name-colors", true);
	}
	
	public boolean cChanCheckTag(String tag, int bot, String channel) {
		if (tag == null || tag.equals("")) return false;
		if (getConfiguration().getString("settings.tag", "all").equalsIgnoreCase(tag)) return true;
		if (bots.get(bot).getString("tag", "").equalsIgnoreCase(tag)) return true;
		if (getChanNode(bot, channel).getString("tag", "").equalsIgnoreCase(tag)) return true;
		return false;
	}
	
}
