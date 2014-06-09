package net.amoebaman.kitmaster.objects;

import org.bukkit.enchantments.Enchantment;
import com.rit.sucy.CustomEnchantment;

/**
 * Stores and Enchantment and its level
 * 
 * @author Dennison
 */
public class Enchant {
	
	/** The enchantment type */
	public Enchantment enc;
	
	/** The enchantment level */
	public int lvl;

	/**
	 * Constructs an EnchantmentWithLevel with the given Enchantment and level
	 * @param enc the Enchantment
	 * @param lvl the level of the Enchantment
	 */
	public Enchant(Enchantment enc, int lvl){
		this.enc = enc;
		this.lvl = lvl;
	}
	
	public String toString(){
		return String.valueOf(enc) + ":" + lvl;
	}
}
