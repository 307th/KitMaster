package com.amoebaman.kitmaster.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import com.amoebaman.kitmaster.enums.Attribute;
import com.amoebaman.kitmaster.handlers.KitHandler;

public class Kit implements Cloneable{
	
	/**
	 * The name of the kit.
	 */
	public final String name;
	
	/**
	 * The set of items that the kit will give.
	 */
	public final ArrayList<ItemStack> items = new ArrayList<ItemStack>();
	
	/**
	 * The set of potion effects that the kit will apply.
	 */
	public final ArrayList<PotionEffect> effects = new ArrayList<PotionEffect>();
	
	/**
	 * The set of (temporary) permissions that the kit will grant.
	 */
	public final ArrayList<String> permissions = new ArrayList<String>();
	
	/**
	 * The set of attributes that have been defined for the kit.
	 */
	public final HashMap<Attribute, Object> attributes = new HashMap<Attribute, Object>();
	
	/**
	 * Constructs a kit from the given specifications.  The lists/maps will be copied onto the kit's lists/maps rather than adopted.
	 * @param name The name of the kit.
	 * @param items The items of the kit.
	 * @param effects The applied potion effects of the kit.
	 * @param permissions The granted permissions of the kit.
	 * @param attributes The defined attributes of the kit.
	 */
	public Kit(String name, List<ItemStack> items, List<PotionEffect> effects, List<String> permissions, HashMap<Attribute, Object> attributes){
		this.name = name;
		this.items.addAll(items);
		this.effects.addAll(effects);
		this.permissions.addAll(permissions);
		this.attributes.putAll(attributes);
	}
	
	public String toString(){
		return name;
	}
	
	/**
	 * Two kits are considered equal so long as their names match.  This is done to prevent two kits from possessing the same name.
	 */
	public boolean equals(Object other){
		if(other instanceof Kit)
			return other.toString().equals(toString());
		return false;
	}
	
	/**
	 * Tests whether the kit's items contain an item.
	 * @param stack The <code>ItemStack</code> to test for.
	 * @return True if the kit contains the given <code>ItemStack</code>
	 */
	public boolean containsItem(ItemStack stack){
		if(stack == null)
			return false;
		for(ItemStack item : items)
			if(item.getType() == stack.getType())
				return true;
		return false;
	}

	/**
	 * Tests whether the kit's items contain an effect.
	 * @param potion The <code>PotionEffect</code> to test for.
	 * @return True if the kit contains the given <code>PotionEffect</code>
	 */
	public boolean containsEffect(PotionEffect potion){
		if(potion == null)
			return false;
		for(PotionEffect effect : effects)
			if(effect.getType() == potion.getType())
				return true;
		return false;
	}
	
	/**
	 * Retrives the value of an attribute as it has been defined for this kit.
	 * @param type The <code>Attribute</code> to retrieve.
	 * @return The value of the attribute, or the attribute's default value if it was not explicitly defined.
	 */
	public Object getAttribute(Attribute type){
		if(attributes.containsKey(type))
			return attributes.get(type);
		return type.def;
	}
	
	/**
	 * Retrives the value of an attribute as a boolean.  See <code>getAttribute(Attribute)</code>
	 */
	public boolean booleanAttribute(Attribute type){ return (Boolean) getAttribute(type); }

	/**
	 * Retrives the value of an attribute as an int.  See <code>getAttribute(Attribute)</code>
	 */
	public int integerAttribute(Attribute type){ return (Integer) getAttribute(type); }

	/**
	 * Retrives the value of an attribute as a double.  See <code>getAttribute(Attribute)</code>
	 */
	public double doubleAttribute(Attribute type){ return (Double) getAttribute(type); }
	
	/**
	 * Retrives the value of an attribute as a String.  See <code>getAttribute(Attribute)</code>
	 */
	public String stringAttribute(Attribute type){ return (String) getAttribute(type); }

	/**
	 * Defines/redefines the value of an attribute for this kit.  If <code>newValue</code> does not match <code>type</code>'s enumerated class type, the operation will fail.
	 * @param type The attribute type to set.
	 * @param newValue The new value for the attribute.
	 * @return False if <code>newValue</code> did not match the designated class of <code>type</code>, true otherwise.
	 */
	public boolean setAttribute(Attribute type, Object newValue){
		try{
			attributes.put(type, type.clazz.cast(newValue));
		}
		catch(ClassCastException cce){
			return false;
		}
		return true;
	}
	
	/**
	 * Gets the kit that this kit regards as its parent.
	 * @return The parent kit, or null if no parent kit is found.
	 */
	public Kit getParent(){
		return KitHandler.getKit((String) attributes.get(Attribute.PARENT));
	}
	
	/**
	 * Gets a new copy of this kit that replaces all undefined attributes of this kit with those defined by its parent.
	 * This call will cascade recursively upwards, applying parents of parents as well.
	 * @return A copy of this kit with its parent's attributes applied.
	 */
	public Kit applyParent(){
		Kit clone = clone();
		Kit parent = clone.getParent();
		if(parent != null)
			for(Attribute type : parent.attributes.keySet())
				if(!clone.attributes.containsKey(type))
					clone.attributes.put(type, parent.getAttribute(type));
		return clone;
	}
	
	/**
	 * Clones the kit for safe modification.
	 * @return A perfect copy of this kit.
	 */
	public Kit clone(){
		return new Kit(name, items, effects, permissions, attributes);
	}
}