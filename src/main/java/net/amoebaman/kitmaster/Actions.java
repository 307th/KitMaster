package net.amoebaman.kitmaster;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.classes.HeroClass;

import net.amoebaman.kitmaster.controllers.InventoryController;
import net.amoebaman.kitmaster.controllers.ItemController;
import net.amoebaman.kitmaster.enums.*;
import net.amoebaman.kitmaster.handlers.HistoryHandler;
import net.amoebaman.kitmaster.handlers.KitHandler;
import net.amoebaman.kitmaster.handlers.MessageHandler;
import net.amoebaman.kitmaster.handlers.TimeStampHandler;
import net.amoebaman.kitmaster.objects.Kit;
import net.amoebaman.kitmaster.utilities.ClearKitsEvent;
import net.amoebaman.kitmaster.utilities.GiveKitEvent;

public class Actions {
	
	/**
	 * Gives a player a kit.
	 * This method will consider and apply all attributes of the given kit, including timeouts, permissions, and inheritance.
	 * If the kit has a parent, a recursive call will be made to this method <i>prior</i> to the application of the initial kit.
	 * @param player The player to give the kit to
	 * @param kit The kit to give
	 * @param override True if this operation should ignore checks for permissions, and timeouts
	 * @return A GiveKitResult signifying the success or reason for failure of giving the kit
	 */
	public static GiveKitResult giveKit(Player player, Kit kit, boolean override){
		return giveKit(player, kit, override ? GiveKitContext.PLUGIN_GIVEN_OVERRIDE : GiveKitContext.PLUGIN_GIVEN);
	}
	
	protected static boolean debugNextGiveKit = false;
	
	protected static GiveKitResult giveKit(Player player, Kit kit, GiveKitContext context){
		boolean debug = debugNextGiveKit;
		debugNextGiveKit = false;
		
		/*
		 * We can't give a player a null kit
		 * Return a result that reflects this
		 */
		if(kit == null)
			return GiveKitResult.FAIL_NULL_KIT;
		if(debug)
			KitMaster.logger().info("Attempting to give " + player.getName() + " the " + kit.name + " kit");
		/*
		 * Clone the kit to prevent accidental mutation damage to the base kit
		 */
		kit = kit.clone();
		/*
		 * Check if the player has permission to take this kit in the given manner
		 * Ignore these checks if the context overrides them
		 */
		if(!context.overrides){
			PermsResult perms = KitHandler.getKitPerms(player, kit);
			if(debug)
				KitMaster.logger().info("Permissions result: " + perms);
			switch(perms){
				case COMMAND_ONLY:
					if(context == GiveKitContext.SIGN_TAKEN){
						player.sendMessage(MessageHandler.getMessage("take_kit.perms.fail_sign", kit));
						return GiveKitResult.FAIL_NO_PERMS;
					}
					break;
				case SIGN_ONLY:
					if(context == GiveKitContext.COMMAND_TAKEN){
						player.sendMessage(MessageHandler.getMessage("take_kit.perms.fail_cmd", kit));
						return GiveKitResult.FAIL_NO_PERMS;
					}
					break;
				case INHERIT_COMMAND_ONLY:
					if(context == GiveKitContext.SIGN_TAKEN){
						player.sendMessage(MessageHandler.getMessage("take_kit.perms.fail_parent_sign", kit));
						return GiveKitResult.FAIL_NO_PERMS;
					}
				case INHERIT_SIGN_ONLY:
					if(context == GiveKitContext.COMMAND_TAKEN){
						player.sendMessage(MessageHandler.getMessage("take_kit.perms.fail_parent_cmd", kit));
						return GiveKitResult.FAIL_NO_PERMS;
					}
					break;
				case NONE:
					player.sendMessage(MessageHandler.getMessage("take_kit.perms.fail_all", kit));
					return GiveKitResult.FAIL_NO_PERMS;
				case INHERIT_NONE:
					player.sendMessage(MessageHandler.getMessage("take_kit.perms.fail_parent_all", kit));
					return GiveKitResult.FAIL_NO_PERMS;
				default:
			}
		}
		/*
		 * Perform operations for the parent kit
		 * Obviously these don't need to happen if there is no parent kit
		 */
		Kit parentKit = kit.getParent();
		if(debug)
			KitMaster.logger().info("Parent kit: " + parentKit);
		if(parentKit != null)
			/*
			 * Check timeouts for the parent kit
			 * Don't perform these checks if the context overrides them or the player has an override permission
			 */
			if(!context.overrides && !TimeStampHandler.hasOverride(player, parentKit)){
				if(debug){
					KitMaster.logger().info("Checking parent timestamp: " + TimeStampHandler.getTimeStamp(player, parentKit));
					KitMaster.logger().info("Checking parent timeout: " + TimeStampHandler.timeoutRemaining(player, parentKit));
				}
				switch(TimeStampHandler.timeoutCheck(player, parentKit)){
					case FAIL_TIMEOUT:
						player.sendMessage(MessageHandler.getMessage("take_kit.timeout.fail_parent", kit).replace("%time%", TimeStampHandler.timeoutRemaining(player, parentKit)));
						return GiveKitResult.FAIL_TIMEOUT;
					case FAIL_SINGLE_USE:
						player.sendMessage(MessageHandler.getMessage("take_kit.timeout.fail_su_parent", kit));
						return GiveKitResult.FAIL_SINGLE_USE;
					default: }
			}
		/*
		 * Check timeouts for the current kit
		 * Don't perform these checks if the context overrides them or the player has an override permission
		 */
		if(!context.overrides && !TimeStampHandler.hasOverride(player, kit)){
			if(debug){
				KitMaster.logger().info("Checking timestamp: " + TimeStampHandler.getTimeStamp(player, parentKit));
				KitMaster.logger().info("Checking timeout: " + TimeStampHandler.timeoutRemaining(player, kit));
			}
			switch(TimeStampHandler.timeoutCheck(player, kit)){
				case FAIL_TIMEOUT:
					player.sendMessage(MessageHandler.getMessage("take_kit.timeout.fail_generic", kit).replace("%time%", TimeStampHandler.timeoutRemaining(player, kit)));
					return GiveKitResult.FAIL_TIMEOUT;
				case FAIL_SINGLE_USE:
					player.sendMessage(MessageHandler.getMessage("take_kit.timeout.fail_su_generic", kit));
					return GiveKitResult.FAIL_SINGLE_USE;
				default: }
		}
		/*
		 * Check if the player can afford the kit
		 * Don't perform these checks if the economy is not enabled, or if the contexts overrides them or the player has an override permission
		 */
		if(KitMaster.getEcon() != null)
			if(KitMaster.getEcon().getBalance(player.getName()) < kit.doubleAttribute(Attribute.COST) && !player.hasPermission("kitmaster.nocharge") && !player.hasPermission("kitmaster.nocharge." + kit.name)){
				player.sendMessage(MessageHandler.getMessage("take_kit.econ.fail_cash", kit).replace("%amount%", "" + kit.doubleAttribute(Attribute.COST)).replace("%currency%", KitMaster.getEcon().currencyNamePlural()));
				return GiveKitResult.FAIL_COST;
			}
		/*
		 * Check if the player has taken any kits that restrict further kit usage
		 */
		if(debug)
			KitMaster.logger().info("Checking history: " + HistoryHandler.getHistory(player));
		for(Kit other : HistoryHandler.getHistory(player))
			if(other.booleanAttribute(Attribute.RESTRICT_KITS)){
				player.sendMessage(MessageHandler.getMessage("take_kit.misc.fail_restrict"));
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
		if(kit.booleanAttribute(Attribute.INFINITE_EFFECTS))
			for(int i = 0; i < kit.effects.size(); i++)
				kit.effects.set(i, new PotionEffect(kit.effects.get(i).getType(), Integer.MAX_VALUE, kit.effects.get(i).getAmplifier()));
		player.addPotionEffects(kitEvent.getKit().effects);
		/*
		 * Grant the kit's permissions to the player
		 * Don't perform this operation if the permission handle is not enabled
		 */
		if(KitMaster.getPerms() != null)
			for(String node : kit.permissions)
				KitMaster.getPerms().playerAdd(player, node);
		/*
		 * Apply the kit's economic attributes
		 * Don't perform this operation if the economy handle is not enabled, or if the player has  an override permission
		 */
		if(KitMaster.getEcon() != null && !player.hasPermission("kitmaster.nocharge") && !player.hasPermission("kitmaster.nocharge." + kit.name)){
			KitMaster.getEcon().bankWithdraw(player.getName(), kit.doubleAttribute(Attribute.COST));
			KitMaster.getEcon().bankDeposit(player.getName(), kit.doubleAttribute(Attribute.CASH));
		}
		/*
		 * Grant Heroes tie-ins
		 */
		HeroClass heroClass = KitMaster.HEROES.getClassManager().getClass(kit.stringAttribute(Attribute.HEROES_CLASS));
		Map<String, ConfigurationSection> heroSkills = new HashMap<String, ConfigurationSection>();
		for(String key : kit.sectionAttribute(Attribute.HEROES_SKILLS).getKeys(false)){
			try{
				heroSkills.put(key, kit.sectionAttribute(Attribute.HEROES_SKILLS).getConfigurationSection(key));
			}
			catch(Exception e){ e.printStackTrace(); }
		}
		Map<Material, String[]> heroBinds = new HashMap<Material, String[]>();
		for(String bind : kit.stringListAttribute(Attribute.HEROES_BINDS)){
			String[] split = bind.split(":");
			String[] skills = new String[split.length - 1];
			for(int i = 0; i < skills.length; i++)
				skills[i] = split[i + 1];
			try{
				ItemStack type = ItemController.getBaseStack(split[0]);
				if(type != null)
					heroBinds.put(type.getType(), skills);
			}
			catch(Exception e){ e.printStackTrace(); }
		}
		
		Hero hero = KitMaster.HEROES.getCharacterManager().getHero(player);
		if(heroClass != null)
			hero.setHeroClass(heroClass, false);
		for(String skill : heroSkills.keySet())
			hero.addSkill(skill, heroSkills.get(skill));
		for(Material bind : heroBinds.keySet())
			hero.bind(bind, heroBinds.get(bind));
		
		
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
			player.sendMessage(MessageHandler.getMessage("take_kit.success.generic", kit));
		if(context == GiveKitContext.COMMAND_GIVEN || context == GiveKitContext.PLUGIN_GIVEN || context == GiveKitContext.PLUGIN_GIVEN_OVERRIDE)
			player.sendMessage(MessageHandler.getMessage("take_kit.success.given.receiver", kit));
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
			if(kit.booleanAttribute(Attribute.CLEAR_PERMISSIONS) && KitMaster.getPerms() != null)
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
			if(event.clearsHeroes())
				clearHeroes(player, false, context);
		}
		HistoryHandler.resetHistory(player);
	}
	
	private static void clearInventory(Player player, boolean callEvent, ClearKitsContext context){
		ClearKitsEvent event = new ClearKitsEvent(player, true, false, false, false, context);
		if(callEvent)
			event.callEvent();
		if(!event.isCancelled() && event.clearsInventory()){
			player.getInventory().clear();
			player.getInventory().setArmorContents(null);
		}
	}
	
	private static void clearEffects(Player player, boolean callEvent, ClearKitsContext context){
		ClearKitsEvent event = new ClearKitsEvent(player, false, true, false, false, context);
		if(callEvent)
			event.callEvent();
		if(!event.isCancelled() && event.clearsEffects()){
			for(PotionEffect effect : player.getActivePotionEffects())
				player.removePotionEffect(effect.getType());
		}
	}
	
	private static void clearPermissions(Player player, boolean callEvent, ClearKitsContext context){
		ClearKitsEvent event = new ClearKitsEvent(player, false, false, true, false, context);
		if(callEvent)
			event.callEvent();
		if(!event.isCancelled() && event.clearsPermissions()){
			if(KitMaster.getPerms() != null)
				for(Kit last : HistoryHandler.getHistory(player))
					for(String node : last.permissions)
						KitMaster.getPerms().playerRemove(player, node);
		}
	}
	
	private static void clearHeroes(Player player, boolean callEvent, ClearKitsContext context){
		ClearKitsEvent event = new ClearKitsEvent(player, false, false, false, true, context);
		if(callEvent)
			event.callEvent();
		if(!event.isCancelled() && event.clearsHeroes()){
			Hero hero = KitMaster.HEROES.getCharacterManager().getHero(player);
			hero.setHeroClass(KitMaster.HEROES.getClassManager().getDefaultClass(), false);
			for(Kit last : HistoryHandler.getHistory(player))
				for(String skill : last.sectionAttribute(Attribute.HEROES_SKILLS).getKeys(false))
					hero.removeSkill(skill);
		}
	}
	
}
