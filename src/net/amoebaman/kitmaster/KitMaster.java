package net.amoebaman.kitmaster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;


import net.amoebaman.kitmaster.controllers.InventoryController;
import net.amoebaman.kitmaster.enums.Attribute;
import net.amoebaman.kitmaster.enums.ClearKitsContext;
import net.amoebaman.kitmaster.enums.GiveKitContext;
import net.amoebaman.kitmaster.enums.GiveKitResult;
import net.amoebaman.kitmaster.enums.PermsResult;
import net.amoebaman.kitmaster.handlers.BookHandler;
import net.amoebaman.kitmaster.handlers.CustomItemHandler;
import net.amoebaman.kitmaster.handlers.CustomPotionHandler;
import net.amoebaman.kitmaster.handlers.FireworkEffectHandler;
import net.amoebaman.kitmaster.handlers.FireworkHandler;
import net.amoebaman.kitmaster.handlers.HistoryHandler;
import net.amoebaman.kitmaster.handlers.KitHandler;
import net.amoebaman.kitmaster.handlers.SignHandler;
import net.amoebaman.kitmaster.handlers.TimeStampHandler;
import net.amoebaman.kitmaster.objects.Kit;
import net.amoebaman.kitmaster.utilities.ClearKitsEvent;
import net.amoebaman.kitmaster.utilities.GiveKitEvent;
import net.amoebaman.kitmaster.utilities.Metrics;
import net.amoebaman.kitmaster.utilities.Updater;
import net.amoebaman.kitmaster.utilities.Updater.UpdateType;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

//TODO Javadoc for EVERYTHING

/**
 * 
 * The main class for KitMaster.
 * 
 * Contains static methods for generally managing kits.
 * 
 * @author Dennison
 *
 */
public class KitMaster extends JavaPlugin implements Listener{
	
	private static PluginLogger log;
	
	public static String mainDirectory, kitsDirectory, dataDirectory;
	public static File configFile, customDataFile, kitsFile, signsFile, timestampsFile, historyFile;
	
	protected static boolean vaultEnabled, updateEnabled;
	protected static Permission perms;
	protected static Economy economy;
	protected static Chat chat;
	protected static Metrics metrics;
	protected static Updater update;
	
	public final static boolean DEBUG_PERMS = false;
	public final static boolean DEBUG_KITS = false;
	
	private static int taskID;
	
	@Override
	public void onEnable(){
		log = new PluginLogger(this);
		
		mainDirectory = getDataFolder().getPath();
		kitsDirectory = mainDirectory + "/kits";
		dataDirectory = mainDirectory + "/data";
		new File(mainDirectory).mkdirs();
		new File(kitsDirectory).mkdirs();
		new File(dataDirectory).mkdirs();
		
		configFile = new File(mainDirectory + "/config.yml");
		customDataFile = new File(mainDirectory + "/custom-data.yml"); 
		kitsFile = new File(mainDirectory + "/kits.yml");
		signsFile = new File(dataDirectory + "/signs.yml");
		timestampsFile = new File(dataDirectory + "/timestamps.yml");
		historyFile = new File(dataDirectory + "/history.yml");
		
		try{
			/*
			 * Kits and kit-related items
			 */
			reloadKits();
			/*
			 * Kit selection signs
			 */
			if(!signsFile.exists())
				signsFile.createNewFile();
			SignHandler.load(signsFile);
			log.info("Loaded kit selection sign locations from " + signsFile.getPath());
			/*
			 * Kit selection timestamps
			 */
			if(!timestampsFile.exists())
				timestampsFile.createNewFile();
			TimeStampHandler.load(timestampsFile);
			log.info("Loaded player kit timestamps from " + timestampsFile.getPath());
			/*
			 * Kit selection history
			 */
			if(!historyFile.exists())
				historyFile.createNewFile();
			HistoryHandler.load(historyFile);
			log.info("Loaded player kit history from " + historyFile.getPath());
		}
		catch(Exception e){e.printStackTrace();}
		
		/*
		 * If allowed, automatically update
		 */
		updateEnabled = getConfig().getBoolean("update.checkForUpdate");
		if(updateEnabled){
			try{
				getLogger().info("Checking for updates...");
				UpdateType type = getConfig().getBoolean("update.autoInstallUpdate") ? UpdateType.DEFAULT : UpdateType.NO_DOWNLOAD;
				update = new Updater(this, "kitmaster", this.getFile(), type, true);
				switch(update.getResult()){
					case FAIL_BADSLUG:
					case FAIL_NOVERSION:
						getLogger().severe("Failed to check for updates due to bad code.  Contact the developer: " + update.getResult().name());
						break;
					case FAIL_DBO:
						getLogger().severe("An error occurred while checking for updates.");
						break;
					case FAIL_DOWNLOAD:
						getLogger().severe("An error occurred downloading the update.");
						break;
					case UPDATE_AVAILABLE:
						getLogger().warning("An update is available for download on BukkitDev.");
						break;
					default: }
			}
			catch(Exception e){
				getLogger().severe("Error occurred while trying to update");
				e.printStackTrace();
			}
		}
		
		/*
		 * Metrics
		 */
		try{
			metrics = new Metrics(this);
			metrics.start();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		
		initVault();	
		KitMasterEventHandler.init(this);
		KitMasterCommandHandler.init(this);
		taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new InfiniteEffects(), 15, 15);
	}
	
	@Override
	public void onDisable() {
		Bukkit.getScheduler().cancelTask(taskID);
		if(getConfig().getBoolean("clearKits.onDisable", true))
			for(OfflinePlayer player : HistoryHandler.getPlayers())
				if(player instanceof Player)
					clearAll((Player) player, true, ClearKitsContext.PLUGIN_DISABLE);
		try{
			SignHandler.save(signsFile);
			TimeStampHandler.save(timestampsFile);
			HistoryHandler.save(historyFile);
		}
		catch(Exception e){ e.printStackTrace(); }
	}
	
	/**
	 * Reloads all customized item data sets from their files, and then reloads all kits from their files.
	 * This method will NOT remove kits from memory if they have been removed from the files, a reload is
	 * necessary to accomplish this.
	 */
	public static void reloadKits(){
		/*
		 * Configuration
		 */
		try {
			if(!configFile.exists())
				configFile.createNewFile();
			plugin().getConfig().load(configFile);
			plugin().getConfig().options().copyDefaults(true);
			plugin().getConfig().save(configFile);
			log.info("Loaded configuration from " + configFile.getPath());
		}
		catch (Exception e) {
			log.severe("Error while loading configuration from " + configFile.getPath());
			e.printStackTrace();
		}
		/*
		 * Custom data
		 */
		try{
			if(!customDataFile.exists()){
				customDataFile.createNewFile();
				customDataFile.setWritable(true);
				BufferedReader reader = new BufferedReader(new InputStreamReader(KitMaster.class.getResourceAsStream("/custom-data.yml")));
				BufferedWriter writer = new BufferedWriter(new FileWriter(customDataFile));
				while(reader.ready()){
					writer.write(reader.readLine());
					writer.newLine();
				}
				reader.close();
				writer.close();
			}
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(customDataFile);
			
			BookHandler.yaml = yaml.isConfigurationSection("books") ? yaml.getConfigurationSection("books") : yaml.createSection("books");
			CustomItemHandler.yaml = yaml.isConfigurationSection("items") ? yaml.getConfigurationSection("items") : yaml.createSection("items");
			CustomPotionHandler.yaml = yaml.isConfigurationSection("potions") ? yaml.getConfigurationSection("potions") : yaml.createSection("potions");
			FireworkEffectHandler.yaml = yaml.isConfigurationSection("bursts") ? yaml.getConfigurationSection("bursts") : yaml.createSection("bursts");
			FireworkHandler.yaml = yaml.isConfigurationSection("fireworks") ? yaml.getConfigurationSection("fireworks") : yaml.createSection("fireworks");
			
			log.info("Loaded custom data from " + customDataFile.getPath());
		}
		catch(Exception e){
			log.severe("Error while loading custom data from " + customDataFile.getPath());
			e.printStackTrace();
		}
		/*
		 * Kits
		 */
		try{
			if(!kitsFile.exists()){
				kitsFile.createNewFile();
				kitsFile.setWritable(true);
				BufferedReader reader = new BufferedReader(new InputStreamReader(plugin().getClass().getResourceAsStream("/kits.yml")));
				BufferedWriter writer = new BufferedWriter(new FileWriter(kitsFile));
				while(reader.ready()){
					writer.write(reader.readLine());
					writer.newLine();
				}
				reader.close();
				writer.close();
			}
			KitHandler.loadKits(kitsFile);
			log.info("Loaded all kit files from " + kitsFile.getPath());
			KitHandler.loadKits(new File(kitsDirectory));
			log.info("Loaded all kit files from " + kitsDirectory);
		}
		catch (Exception e) {
			log.severe("Error while loading kits");
			e.printStackTrace();
		}
	}
	
	/**
	 * Saves all customized item data to its flat file.
	 */
	public static void saveCustomData(){
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.createSection("books", BookHandler.yaml.getValues(true));
		yaml.createSection("items", CustomItemHandler.yaml.getValues(true));
		yaml.createSection("potions", CustomPotionHandler.yaml.getValues(true));
		yaml.createSection("bursts", FireworkEffectHandler.yaml.getValues(true));
		yaml.createSection("fireworks", FireworkHandler.yaml.getValues(true));
		try{
			yaml.save(customDataFile);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Retreives the PluginLogger that KitMaster uses for debugging.
	 * @return KitMaster's PluginLogger
	 */
	public static PluginLogger logger(){ return log; }
	
	/**
	 * Retreives the FileConfiguration that KitMaster uses.
	 * @return KitMaster's FileConfiguration
	 */
	public static FileConfiguration config(){ return plugin().getConfig(); }
	
	/**
	 * Retreives the instance of the KitMaster plugin.
	 * @return KitMaster's plugin instance
	 */
	public static Plugin plugin(){ return Bukkit.getPluginManager().getPlugin("KitMaster"); }
	
	/**
	 * Checks to see whether Vault was enabled at the time KitMaster was loaded.
	 * @return
	 */
	public static boolean isVaultEnabled(){ return vaultEnabled; }
	
	/**
	 * Gives a player a kit.  This method will consider and apply all attributes of the given kit, including timeouts, permissions, and inheritance.  If the kit has a parent kit, a recursive call will be made to this method <i>prior</i> to the application of <code>kit</code>, with <code>GiveKitContext.PARENT_GIVEN</code>..
	 * @param player The player to give the kit to.
	 * @param kit The kit that will be given.
	 * @param context The context or reason for giving this kit.
	 * @return a GiveKitResult signifying the success or reason for failure of giving the kit.
	 */
	public static GiveKitResult giveKit(Player player, Kit kit, GiveKitContext context){
		/*
		 * We can't give a player a null kit
		 * Return a result that reflects this
		 */
		if(kit == null)
			return GiveKitResult.FAIL_NULL_KIT;
		if(DEBUG_KITS)
			log.info("Attempting to give " + player.getName() + " the " + kit.name + " kit");
		/*
		 * Clone the kit to prevent accidental damage to the base kit
		 */
		kit = kit.clone();
		/*
		 * Check if the player has permission to take this kit in the given manner
		 * Ignore these checks if the context overrides them
		 */
		if(!context.overrides){
			PermsResult getKitPerms = KitHandler.getKitPerms(player, kit);
			switch(getKitPerms){
				case COMMAND_ONLY:
					if(context == GiveKitContext.SIGN_TAKEN){
						player.sendMessage(ChatColor.ITALIC + "You can't take the " + kit.name + " kit from signs");
						return GiveKitResult.FAIL_NO_PERMS;
					}
					break;
				case SIGN_ONLY:
					if(context == GiveKitContext.COMMAND_TAKEN){
						player.sendMessage(ChatColor.ITALIC + "You can't take the " + kit.name + " kit by command");
						return GiveKitResult.FAIL_NO_PERMS;
					}
					break;
				case INHERIT_COMMAND_ONLY:
					if(context == GiveKitContext.SIGN_TAKEN){
						player.sendMessage(ChatColor.ITALIC + "You can't take " + kit.getParent().name + " kits from signs");
						return GiveKitResult.FAIL_NO_PERMS;
					}
				case INHERIT_SIGN_ONLY:
					if(context == GiveKitContext.COMMAND_TAKEN){
						player.sendMessage(ChatColor.ITALIC + "You can't take " + kit.getParent().name + " kits by command");
						return GiveKitResult.FAIL_NO_PERMS;
					}
					break;
				case NONE:
					player.sendMessage(ChatColor.ITALIC + "You can't take the " + kit.name + " kit");
					return GiveKitResult.FAIL_NO_PERMS;
				case INHERIT_NONE:
					player.sendMessage(ChatColor.ITALIC + "You can't take " + kit.getParent().name + " kits");
					return GiveKitResult.FAIL_NO_PERMS;
				default:
			}
		}
		/*
		 * Perform operations for the parent kit
		 * Obviously these don't need to happen if there is no parent kit
		 */
		Kit parentKit = kit.getParent();
		if(parentKit != null)
			/*
			 * Check timeouts for the parent kit
			 * Don't perform these checks if the context overrides them or the player has an override permission
			 */
			if(!context.overrides && !player.hasPermission("kitmaster.notimeout")){
				switch(TimeStampHandler.timeoutCheck(player, parentKit)){
					case FAIL_TIMEOUT:
						player.sendMessage(ChatColor.ITALIC + "You need to wait " + TimeStampHandler.timeoutLeft(player, parentKit) + " more seconds before using a " + parentKit.name + " kit");
						return GiveKitResult.FAIL_TIMEOUT;
					case FAIL_SINGLE_USE:
						player.sendMessage(ChatColor.ITALIC + "You can only use a " + parentKit.name + " kit once");
						return GiveKitResult.FAIL_SINGLE_USE;
					default: }
			}
		/*
		 * Check timeouts for the current kit
		 * Don't perform these checks if the context overrides them or the player has an override permission
		 */
		if(!context.overrides && !player.hasPermission("kitmaster.notimeout")){
			switch(TimeStampHandler.timeoutCheck(player, kit)){
				case FAIL_TIMEOUT:
					player.sendMessage(ChatColor.ITALIC + "You need to wait " + TimeStampHandler.timeoutLeft(player, kit) + " more seconds before using the " + kit.name + " kit");
					return GiveKitResult.FAIL_TIMEOUT;
				case FAIL_SINGLE_USE:
					player.sendMessage(ChatColor.ITALIC + "You can only use the " + kit.name + " kit once");
					return GiveKitResult.FAIL_SINGLE_USE;
				default: }
		}
		/*
		 * Check if the player can afford the kit
		 * Don't perform these checks if the economy is not enabled, or if the contexts overrides them or the player has an override permission
		 */
		if(economy != null)
			if(economy.getBalance(player.getName()) < kit.doubleAttribute(Attribute.COST) && !player.hasPermission("kitmaster.nocharge")){
				player.sendMessage(ChatColor.ITALIC + "You need " + kit.doubleAttribute(Attribute.COST) + " " + economy.currencyNameSingular() + " to take the " + kit.name + " kit");
				return GiveKitResult.FAIL_COST;
			}
		/*
		 * Check if the player has taken any kits that restrict further kit usage
		 */
		for(Kit other : HistoryHandler.getHistory(player))
			if(other.booleanAttribute(Attribute.RESTRICT_KITS)){
				player.sendMessage(ChatColor.ITALIC + "You've already taken a kit that doesn't allow you to take further kits");
				return GiveKitResult.FAIL_RESTRICTED;
			}
		/*
		 * Create and call a GiveKitEvent so that other plugins can modify or attempt to cancel the kit
		 * If the event comes back cancelled and the context doesn't override it, end here
		 */
		GiveKitEvent kitEvent = new GiveKitEvent(player, kit, context);
		kitEvent.callEvent();
		if (kitEvent.isCancelled() && !context.overrides)
			return GiveKitResult.FAIL_CANCELLED;
		/*
		 * Apply the kit's clearing properties
		 * Don't perform this operation if the kit is a parent
		 */
		if(context != GiveKitContext.PARENT_GIVEN)
			applyKitClears(player, kit);
		/*
		 * Apply the parent kit
		 */
		if(parentKit != null)
			giveKit(player, parentKit, GiveKitContext.PARENT_GIVEN);
		/*
		 * Add the kit's items to the player's inventory
		 */
		InventoryController.addItemsToInventory(player, kitEvent.getKit().items, parentKit != null && kit.booleanAttribute(Attribute.UPGRADE));
		/*
		 * Apply the kit's potion effects to the player
		 */
		player.addPotionEffects(kitEvent.getKit().effects);
		/*
		 * Grant the kit's permissions to the player
		 * Don't perform this operation if the permission handle is not enabled
		 */
		if(perms != null)
			for(String node : kit.permissions)
				perms.playerAdd(player, node);
		/*
		 * Apply the kit's economic attributes
		 * Don't perform this operation if the economy handle is not enabled, or if the player has  an override permission
		 */
		if(economy != null && !player.hasPermission("kitmaster.nocharge")){
			economy.bankWithdraw(player.getName(), kit.doubleAttribute(Attribute.COST));
			economy.bankDeposit(player.getName(), kit.doubleAttribute(Attribute.CASH));
		}
		/*
		 * Record that this kit was taken
		 * Stamp the time, and add the kit to the player's history
		 */
		TimeStampHandler.setTimeStamp(kit.booleanAttribute(Attribute.GLOBAL_TIMEOUT) ? null : player, kit);
		HistoryHandler.addToHistory(player, kit);
		/*
		 * Notify the player of their good fortune
		 */
		if(context == GiveKitContext.COMMAND_TAKEN || context == GiveKitContext.SIGN_TAKEN)
			player.sendMessage(ChatColor.ITALIC + kit.name + " kit taken");
		if(context == GiveKitContext.COMMAND_GIVEN || context == GiveKitContext.PLUGIN_GIVEN || context == GiveKitContext.PLUGIN_GIVEN_OVERRIDE)
			player.sendMessage(ChatColor.ITALIC + "You were given the " + kit.name + " kit");
		/*
		 * Return the success of the mission
		 */
		return GiveKitResult.SUCCESS;
	}
	
	/**
	 * Clears all attributes for all kits in history and clears the history for a player.
	 * @param player The target player.
	 */
	public static void clearKits(Player player){
		clearAll(player, true, ClearKitsContext.PLUGIN_ORDER);
	}
	
	private static void applyKitClears(Player player, Kit kit){
		if(kit.booleanAttribute(Attribute.CLEAR_ALL) || (kit.booleanAttribute(Attribute.CLEAR_INVENTORY) && kit.booleanAttribute(Attribute.CLEAR_EFFECTS) && kit.booleanAttribute(Attribute.CLEAR_PERMISSIONS))){
			clearAll(player, true, ClearKitsContext.KIT_ATTRIBUTE);
			HistoryHandler.resetHistory(player);
		}
		else{
			if(kit.booleanAttribute(Attribute.CLEAR_INVENTORY))
				clearInventory(player, true, ClearKitsContext.KIT_ATTRIBUTE);
			if(kit.booleanAttribute(Attribute.CLEAR_EFFECTS))
				clearEffects(player, true, ClearKitsContext.KIT_ATTRIBUTE);
			if(kit.booleanAttribute(Attribute.CLEAR_PERMISSIONS) && perms != null)
				clearPermissions(player, true, ClearKitsContext.KIT_ATTRIBUTE);
		}
	}
	
 	protected static void clearAll(Player player, boolean callEvent, ClearKitsContext context){
		ClearKitsEvent event = new ClearKitsEvent(player, context);
		if(callEvent)
			event.callEvent();
		if(!event.isCancelled()){
			if(event.clearsInventory())
				clearInventory(player, false, context);
			if(event.clearsEffects())
				clearEffects(player, false, context);
			if(event.clearsPermissions())
				clearPermissions(player, false, context);
		}
		HistoryHandler.resetHistory(player);
	}
	
	private static void clearInventory(Player player, boolean callEvent, ClearKitsContext context){
		ClearKitsEvent event = new ClearKitsEvent(player, true, false, false, context);
		if(callEvent)
			event.callEvent();
		if(!event.isCancelled() && event.clearsInventory()){
			player.getInventory().clear();
			player.getInventory().setArmorContents(null);
		}
	}
	
	private static void clearEffects(Player player, boolean callEvent, ClearKitsContext context){
		ClearKitsEvent event = new ClearKitsEvent(player, false, true, false, context);
		if(callEvent)
			event.callEvent();
		if(!event.isCancelled() && event.clearsEffects()){
			for(PotionEffect effect : player.getActivePotionEffects())
				player.removePotionEffect(effect.getType());
		}
	}
	
	private static void clearPermissions(Player player, boolean callEvent, ClearKitsContext context){
		ClearKitsEvent event = new ClearKitsEvent(player, false, false, true, context);
		if(callEvent)
			event.callEvent();
		if(!event.isCancelled() && event.clearsPermissions()){
			if(perms != null)
				for(Kit last : HistoryHandler.getHistory(player))
					for(String node : last.permissions)
						perms.playerRemove(player, node);
		}
	}
	
	private static void initVault(){
		vaultEnabled = Bukkit.getPluginManager().isPluginEnabled("Vault");
		if(!vaultEnabled)
			return;
		RegisteredServiceProvider<Permission> tempPerm = Bukkit.getServicesManager().getRegistration(Permission.class);
		if(tempPerm != null){
			perms = tempPerm.getProvider();
			if(perms != null)
				log.info("Hooked into Permissions manager: " + perms.getName());
		}
		RegisteredServiceProvider<Economy> tempEcon = Bukkit.getServicesManager().getRegistration(Economy.class);
		if(tempEcon != null){
			economy = tempEcon.getProvider();
			if(economy != null)
				log.info("Hooked into Economy manager: " + economy.getName());
		}
		RegisteredServiceProvider<Chat> tempChat = Bukkit.getServicesManager().getRegistration(Chat.class);
		if(tempChat != null){
			chat = tempChat.getProvider();
			if(chat != null)
				log.info("Hooked into Chat manager: " + chat.getName());
		}
	}
	
	/**
	 * Private <i>Runnable</i> that is used to handle infinitely renewed potion effects.
	 * @author Dennison
	 */
	private static class InfiniteEffects implements Runnable{
		
		public void run() {
			for(World world : Bukkit.getWorlds())
				for(Player player : world.getPlayers())
					for(Kit kit : HistoryHandler.getHistory(player))
						if(kit != null && kit.booleanAttribute(Attribute.INFINITE_EFFECTS))
							for(PotionEffect effect : kit.effects){
								for(PotionEffect active : player.getActivePotionEffects())
									if(effect.getType().getId() == active.getType().getId() && effect.getAmplifier() >= active.getAmplifier())
										player.removePotionEffect(active.getType());
								player.addPotionEffect(effect);
							}
		}
	}
	
	
}