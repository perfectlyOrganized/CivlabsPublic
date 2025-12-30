package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Combat.ArmorEquipAttributes;
import com.minecraftcivilizations.specialization.Listener.Player.ReviveListener;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 * @author  alectriciti, jfrogy
 */
public class DefineCustomItems implements Listener {
    NamespacedKey MACE_KEY = new NamespacedKey("specialization", "is_mace");

    public Bandage bandage;
    public DefineCustomItems(Specialization plugin) {
        this.bandage = new Bandage("custombandage", "Bandage");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }


    // intercept the take from the result slot and replace with your modified item
    @EventHandler(priority = EventPriority.HIGHEST) //Highest will run AFTER the Xp is given
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (event.isCancelled()) return;
        if (event.getResult() != Event.Result.ALLOW) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        ItemStack modified = current.clone();
        ItemMeta meta = modified.getItemMeta();

        if (current.getType().name().contains("_SWORD")) {
            // final tweak applied when player actually takes the item
            masterwork_sword.wrapItemStack(modified, (Player) event.getWhoClicked());
            // setCurrentItem changes what the player receives from the result slot
            event.setCurrentItem(modified);
        }
    }

    CustomItem masterwork_sword = new CustomWeapon("masterwork_sword");

    CustomItem wooden_shears = new CustomItem( "wooden_shears", "§rWooden shears", Material.SHEARS, "wooden_shears", true ) {
        @Override
        public void init() {
            NamespacedKey RECIPE_KEY = new NamespacedKey(Specialization.getInstance(), "wooden_shears_recipe");
            ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItemStack(1));

            recipe.shape("I ", " I", "  ");
            RecipeChoice resource = new RecipeChoice.MaterialChoice(
                    Material.OAK_PLANKS,
                    Material.ACACIA_PLANKS,
                    Material.BIRCH_PLANKS,
                    Material.SPRUCE_PLANKS,
                    Material.JUNGLE_PLANKS,
                    Material.DARK_OAK_PLANKS,
                    Material.MANGROVE_PLANKS,
                    Material.PALE_OAK_PLANKS,
                    Material.BAMBOO_PLANKS
            );
            recipe.setIngredient('I', resource);
            Bukkit.addRecipe(recipe, true);
        }
        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("wooden_shears"));
            meta.setCustomModelDataComponent(cmd);
            if (meta instanceof Damageable damageMeta) {
                damageMeta.setMaxDamage(1);
            }
            itemStack.setItemMeta(meta);
        }
    };
    CustomItem flint_shears = new CustomItem("flint_shears", "§rFlint shears", Material.SHEARS, "flint_shears", true) {
        @Override
        public void init() {
            NamespacedKey RECIPE_KEY = new NamespacedKey(Specialization.getInstance(), "flint_shears_recipe");
            ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItemStack(1));
            recipe.shape("I ", " I", "  ");

            recipe.setIngredient('I', Material.FLINT);
            Bukkit.addRecipe(recipe, true);
        }
        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("flint_shears"));
            meta.setCustomModelDataComponent(cmd);
            if (meta instanceof Damageable damageMeta) {
                damageMeta.setMaxDamage(9);
            }
            itemStack.setItemMeta(meta);
        }
    };
    CustomItem copper_axe = new CustomItem("copper_axe", "§rCopper Axe", Material.STONE_AXE, "copper_axe", true) {
        @Override
        public void init() {
            NamespacedKey RECIPE_KEY = new NamespacedKey(Specialization.getInstance(), "copper_axe_recipe");
            ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItemStack(1));
            recipe.shape("BI ", "IS ", " S ");

            recipe.setIngredient('I', Material.COPPER_INGOT);
            recipe.setIngredient('B', Material.COPPER_BLOCK);
            recipe.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(recipe, true);
        }
        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("copper_axe"));
            meta.setCustomModelDataComponent(cmd);

            ToolComponent tool = meta.getTool();
            tool.addRule(Tag.MINEABLE_AXE, 5.0f, true);
            meta.setTool(tool);

            if (meta instanceof Damageable damageMeta) {
                damageMeta.setMaxDamage(170);
            }
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(Attribute.ATTACK_DAMAGE.getKey(), 7, AttributeModifier.Operation.ADD_NUMBER));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(Attribute.ATTACK_SPEED.getKey(), -2.8, AttributeModifier.Operation.ADD_NUMBER));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
    };
    CustomItem copper_pickaxe = new CustomItem("copper_pickaxe", "§rCopper Pickaxe", Material.STONE_PICKAXE, "copper_pickaxe", true) {
        @Override
        public void init() {
            NamespacedKey RECIPE_KEY = new NamespacedKey(Specialization.getInstance(), "copper_pickaxe_recipe");
            ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItemStack(1));
            recipe.shape("IBI", " S ", " S ");

            recipe.setIngredient('I', Material.COPPER_INGOT);
            recipe.setIngredient('B', Material.COPPER_BLOCK);
            recipe.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(recipe, true);
        }
        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("copper_pickaxe"));
            meta.setCustomModelDataComponent(cmd);

            ToolComponent tool = meta.getTool();
            tool.addRule(Tag.MINEABLE_PICKAXE, 5.0f, true);
            tool.addRule(Material.STONE, 7.5f, true);
            meta.setTool(tool);

            if (meta instanceof Damageable damageMeta) {
                damageMeta.setMaxDamage(170);
            }
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(Attribute.ATTACK_SPEED.getKey(), -2.8, AttributeModifier.Operation.ADD_NUMBER));
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(Attribute.ATTACK_DAMAGE.getKey(), 2.5, AttributeModifier.Operation.ADD_NUMBER));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
    };
    CustomItem copper_sword = new CustomItem("copper_sword", "§rCopper Sword", Material.STONE_SWORD, "copper_sword", true) {
        @Override
        public void init() {
            NamespacedKey RECIPE_KEY = new NamespacedKey(Specialization.getInstance(), "copper_sword_recipe");
            ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItemStack(1));
            recipe.shape(" I ", " B ", " S ");

            recipe.setIngredient('I', Material.COPPER_INGOT);
            recipe.setIngredient('B', Material.COPPER_BLOCK);
            recipe.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(recipe, true);
        }
        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("copper_sword"));
            meta.setCustomModelDataComponent(cmd);

            if (meta instanceof Damageable damageMeta) {
                damageMeta.setMaxDamage(170);
            }
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(Attribute.ATTACK_SPEED.getKey(), -2.4, AttributeModifier.Operation.ADD_NUMBER));
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(Attribute.ATTACK_DAMAGE.getKey(), 4.5, AttributeModifier.Operation.ADD_NUMBER));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
    };
    CustomItem copper_hoe = new CustomItem("copper_hoe", "§rCopper Hoe", Material.STONE_HOE, "copper_hoe", true) {
        @Override
        public void init() {
            NamespacedKey RECIPE_KEY = new NamespacedKey(Specialization.getInstance(), "copper_hoe_recipe");
            ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItemStack(1));
            recipe.shape("IB ", " S ", " S ");

            recipe.setIngredient('I', Material.COPPER_INGOT);
            recipe.setIngredient('B', Material.COPPER_BLOCK);
            recipe.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(recipe, true);
        }
        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("copper_hoe"));

            ToolComponent tool = meta.getTool();
            tool.addRule(Tag.MINEABLE_HOE, 0.4f, true);
            meta.setCustomModelDataComponent(cmd);
            meta.setTool(tool);

            if (meta instanceof Damageable damageMeta) {
                damageMeta.setMaxDamage(170);
            }

            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(Attribute.ATTACK_SPEED.getKey(), -2.8, AttributeModifier.Operation.ADD_NUMBER));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
    };
    CustomItem copper_shovel = new CustomItem("copper_shovel", "§rCopper Shovel", Material.STONE_SHOVEL, "copper_shovel", true) {
        @Override
        public void init() {
            NamespacedKey RECIPE_KEY = new NamespacedKey(Specialization.getInstance(), "copper_shovel_recipe");
            ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItemStack(1));
            recipe.shape(" B ", " S ", " S ");

            recipe.setIngredient('B', Material.COPPER_BLOCK);
            recipe.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(recipe, true);
        }
        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("copper_shovel"));
            meta.setCustomModelDataComponent(cmd);

            ToolComponent tool = meta.getTool();
            tool.addRule(Tag.MINEABLE_SHOVEL, 5.0f, true);
            tool.addRule(Tag.SAND, 7.5f, true);
            tool.addRule(Tag.DIRT, 7.5f, true);
            meta.setTool(tool);

            if (meta instanceof Damageable damageMeta) {
                damageMeta.setMaxDamage(170);
            }
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(Attribute.ATTACK_SPEED.getKey(), -2.8, AttributeModifier.Operation.ADD_NUMBER));
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(Attribute.ATTACK_DAMAGE.getKey(), 2.5, AttributeModifier.Operation.ADD_NUMBER));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
    };

    // durability: 55 80 	75 	65
    CustomItem copper_helmet = new CustomItem("copper_helmet", "§rCopper Helmet", Material.CHAINMAIL_HELMET, "copper_helmet", true) {
        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("copper_helmet"));
            meta.setCustomModelDataComponent(cmd);
            //meta.setItemModel(new NamespacedKey(Specialization.getInstance(), "copper_helmet"));
            //EquippableComponent equippableComponent = meta.getEquippable();
           // equippableComponent.setModel(new NamespacedKey(Specialization.getInstance(), "copper_helmet"));
            //meta.setEquippable(equippableComponent);
            if (meta instanceof Damageable damageMeta) {
                damageMeta.setMaxDamage(55);
            }
            itemStack.setItemMeta(meta);
        }
    };
    
        CustomItem hammer = new CustomItem("hammer", "§rHammer", Material.MACE, "hammer", true) {
            @Override
            public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
                meta.getPersistentDataContainer().set(MACE_KEY, PersistentDataType.BYTE, (byte) 1);
                CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
                cmd.setStrings(List.of("hammer"));

                meta.setCustomModelDataComponent(cmd);
                itemStack.setItemMeta(meta);
            }
        };

        // Example Sword
        CustomItem cool_sword = new CustomItem("cool_sword", "Cool Sword", Material.DIAMOND_SWORD, "cool_sword", false) {
            @Override
            public void init() {
            }

            @Override
            public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
                meta.setEnchantmentGlintOverride(true);
            }

            @Override
            public void onInteract(PlayerInteractEvent event, ItemStack itemStack) {
                event.getPlayer().getWorld().spawnParticle(
                        org.bukkit.Particle.CLOUD,
                        event.getPlayer().getLocation(), 100, 0.2f, 0.2f, 0.2f
                );
                event.getPlayer().playSound(event.getPlayer(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1, 1);
            }
        };
//
//        // === Generic Blessed Food (light regen) ===
//        BlessedFood blessed_food = new BlessedFood(
//                "blessed_food",
//                "§eBlessed Food",
//                Material.COOKED_BEEF,
//                PotionEffectType.REGENERATION,
//                20 * 6, 1, 200,
//                List.of(Material.COOKED_BEEF, Material.SUGAR)
//        );

        // === Hearty Soup (stronger regen, shapeless) ===
        BlessedFood hearty_soup = new BlessedFood(
                "hearty_soup",
                "§6Hearty Soup",
                Material.BEETROOT_SOUP,
                PotionEffectType.REGENERATION,
                20 * 5, 1, 50,
                List.of(Material.FERMENTED_SPIDER_EYE, Material.BOWL)
        ) {
            @Override
            public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
                meta.setLore(List.of(
                        "§7A warm soup imbued with divine vitality.",
                        "§eRestores health and grants some regeneration."
                ));
                itemStack.setItemMeta(meta);
            }
        };
//
//        // === Radiant Bread (speed boost, shapeless) ===
//        BlessedFood radiant_bread = new BlessedFood(
//                "radiant_bread",
//                "§fRadiant Bread",
//                Material.BREAD,
//                PotionEffectType.SPEED,
//                20 * 15, 1, 300,
//                List.of(Material.WHEAT, Material.HONEY_BOTTLE)
//        ) {
//            @Override
//            public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
//                meta.setLore(List.of(
//                        "§7A loaf infused with radiant energy.",
//                        "§eGrants a burst of speed when eaten."
//                ));
//                itemStack.setItemMeta(meta);
//            }
//        };
}
