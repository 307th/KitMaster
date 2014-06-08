package net.amoebaman.kitmaster.objects;

import net.amoebaman.kitmaster.KitMaster;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


/**
 * Represents a piece of armor
 * 
 * @author Dennison
 */
public class Armor {

	/** The type of the armor */
	private ArmorType type;

	/** The level of the armor */
	private ArmorLevel lvl;

	/** The interally saved ItemStack */
	private ItemStack item;

	/**
	 * Constructs an Armor with the given type and level
	 * @param type the type of armor
	 * @param lvl the level of armor
	 */
	public Armor(ArmorType type, ArmorLevel lvl){
		this.type = type;
		this.lvl = lvl;
		item = getRawItem();
	}

	/**
	 * Constructs an Armor from the Material that it should represent
	 * @param mat the Material
	 */
	public Armor(Material mat){
		if(mat != null){
			if(mat.name().contains("HELMET"))
				type = ArmorType.HELMET;
			if(mat.name().contains("CHESTPLATE"))
				type = ArmorType.CHESTPLATE;
			if(mat.name().contains("LEGGINGS"))
				type = ArmorType.LEGGINGS;
			if(mat.name().contains("BOOTS"))
				type = ArmorType.BOOTS;

			if(mat.name().contains("DIAMOND"))
				lvl = ArmorLevel.DIAMOND;
			if(mat.name().contains("IRON"))
				lvl = ArmorLevel.IRON;
			if(mat.name().contains("CHAINMAIL"))
				lvl = ArmorLevel.CHAINMAIL;
			if(mat.name().contains("GOLD"))
				lvl = ArmorLevel.GOLD;
			if(mat.name().contains("LEATHER"))
				lvl = ArmorLevel.LEATHER;
			if(mat.name().contains("WOOL")){
				type = ArmorType.HELMET;
				lvl = ArmorLevel.WOOL;
			}
			if(mat.name().contains("SKULL")){
				type = ArmorType.HELMET;
				lvl = ArmorLevel.SKULL;
			}
			item = getRawItem();
		}

	}

	/**
	 * Constructs an Armor from an ItemStack of the Material that it should represent
	 * @param stack the ItemStack
	 */
	public Armor(ItemStack stack){
		this(stack == null ? null : stack.getType());
		item = stack;
	}	

	/**
	 * Gets the ItemStack that is currently in a player's armor slot
	 * @param player the player to check
	 * @param type the type of armor to get
	 * @return the ItemStack
	 */
	public static ItemStack getExisting(Player player, ArmorType type){
		switch(type){
		case HELMET: return player.getInventory().getHelmet();
		case CHESTPLATE: return player.getInventory().getChestplate();
		case LEGGINGS: return player.getInventory().getLeggings();
		case BOOTS: return player.getInventory().getBoots();
		default: return null;
		}
	}

	/**
	 * Tests whether or not this Armor is superior to another
	 * @param other the other Armor
	 * @return true if this Armor is superior to the other
	 */
	public boolean isBetterThan(Armor other){
		if(!isValid())
			return true;
		if(!other.isValid())
			return true;
		return lvl.ordinal() >= other.lvl.ordinal();
	}

	/**
	 * Puts this Armor in its appropriate slot in a Player's inventory
	 * @param player the Player to give this Armor to
	 */
	public void putInSlot(Player player){
		switch(type){
		case HELMET: player.getInventory().setHelmet(item); break;
		case CHESTPLATE: player.getInventory().setChestplate(item); break;
		case LEGGINGS: player.getInventory().setLeggings(item); break;
		case BOOTS: player.getInventory().setBoots(item); break;
		default: }
	}

	/**
	 * Tests whether or not this Armor represents a real Material
	 * @return true if this Armor is valid
	 */
	public boolean isValid(){
		if(!KitMaster.config().getBoolean("inventory.woolHats") && lvl == ArmorLevel.WOOL)
			return false;
		if(!KitMaster.config().getBoolean("inventory.skullHats") && lvl == ArmorLevel.SKULL)
			return false;
		return type != null && lvl != null && item != null;
	}

	/**
	 * Tests whether or not a valid Armor can be constructed from this Material
	 * @return true if a construction call with this Material will produce a valid Armor
	 */
	public static boolean isValid(Material mat){
		return new Armor(mat).isValid();
	}

	/**
	 * Gets the Material that would represent this Armor
	 * @return the Material
	 */
	public Material getMaterial(){
		if(type == null || lvl == null)
			return null;
		switch(lvl){
		case CHAINMAIL:
			switch(type){
			case HELMET: return Material.CHAINMAIL_HELMET;
			case CHESTPLATE: return Material.CHAINMAIL_CHESTPLATE;
			case LEGGINGS: return Material.CHAINMAIL_LEGGINGS;
			case BOOTS: return Material.CHAINMAIL_BOOTS;
			default: return Material.AIR;
			}
		case DIAMOND:
			switch(type){
			case HELMET: return Material.DIAMOND_HELMET;
			case CHESTPLATE: return Material.DIAMOND_CHESTPLATE;
			case LEGGINGS: return Material.DIAMOND_LEGGINGS;
			case BOOTS: return Material.DIAMOND_BOOTS;
			default: return Material.AIR;
			}
		case GOLD:
			switch(type){
			case HELMET: return Material.GOLD_HELMET;
			case CHESTPLATE: return Material.GOLD_CHESTPLATE;
			case LEGGINGS: return Material.GOLD_LEGGINGS;
			case BOOTS: return Material.GOLD_BOOTS;
			default: return Material.AIR;
			}
		case IRON:
			switch(type){
			case HELMET: return Material.IRON_HELMET;
			case CHESTPLATE: return Material.IRON_CHESTPLATE;
			case LEGGINGS: return Material.IRON_LEGGINGS;
			case BOOTS: return Material.IRON_BOOTS;
			default: return Material.AIR;
			}
		case LEATHER:
			switch(type){
			case HELMET: return Material.LEATHER_HELMET;
			case CHESTPLATE: return Material.LEATHER_CHESTPLATE;
			case LEGGINGS: return Material.LEATHER_LEGGINGS;
			case BOOTS: return Material.LEATHER_BOOTS;
			default: return Material.AIR;
			}
		case SKULL: return Material.SKULL;
		case WOOL: return Material.WOOL;
		default: return Material.AIR;
		}
	}

	public ArmorType getType(){ return type; }

	public ArmorLevel getLevel(){ return lvl; }

	public ItemStack getItem(){ return item; }

	/**
	 * Gets an ItemStack containing one of the Material that this Armor represents, without metadata
	 * @return the ItemStack
	 */
	public ItemStack getRawItem(){
		if(getMaterial() == null)
			return null;
		return new ItemStack(getMaterial());
	}

	/** The levels of armor.  Higher ordinals correspond to more powerful armor. */
	public enum ArmorLevel{
		/** Mob heads, optional */
		SKULL,
		/** Wool hats, optional */
		WOOL,
		/** Leather armor */
		LEATHER,
		/** Golden (butter) armor */
		GOLD,
		/** Chainmail armor */
		CHAINMAIL,
		/** Iron armor */
		IRON,
		/** Diamond armor */
		DIAMOND
	};

	/** The types of armor */
	public enum ArmorType{
		/** Helmets */
		HELMET,
		/** Chestplates */
		CHESTPLATE,
		/** Leggings */
		LEGGINGS,
		/** Boots */
		BOOTS
	};

}