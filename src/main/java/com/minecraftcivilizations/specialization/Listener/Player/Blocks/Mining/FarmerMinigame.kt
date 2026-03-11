package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillLevel
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager
import com.minecraftcivilizations.specialization.util.EffectsUtil
import com.minecraftcivilizations.specialization.util.ItemStackUtils
import net.minecraft.world.item.ItemUtils
import org.apache.logging.log4j.core.util.Integers
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import org.bukkit.scheduler.BukkitTask
import java.util.Random
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

object FarmerMinigameManager : Listener {
    val games: HashMap<UUID, FarmerMinigame> = HashMap()

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val game = games[player.uniqueId] ?: return
        game.isActive = false;
    }
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val game = games[player.uniqueId] ?: return
        if (event.view.title != "Collect the farmables") return
        event.isCancelled = true
        val item = event.currentItem ?: return

        when (item.type) {
            Material.BONE_MEAL, game.crop -> {
                game.score++;
                event.currentItem = ItemStack(Material.GREEN_WOOL, 1)
                player.playSound(player.location, Sound.ITEM_BONE_MEAL_USE, 0.7F, 1.0F)
                Bukkit.getScheduler().runTaskLater(OpenLab.getInstance(), Runnable {
                    if (event.currentItem?.type == Material.GREEN_WOOL) event.currentItem = null
                }, 10)
            }
            Material.CLOCK -> {
                game.speed = (game.speed/1.25).toInt()
                event.currentItem = null
            }
            Material.ROTTEN_FLESH, Material.BONE, Material.CREEPER_HEAD, Material.ZOMBIE_HEAD -> {
                game.lives--
                event.currentItem = ItemStack(Material.RED_WOOL, 1)
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_HURT, 0.7F, 1.0F)
                Bukkit.getScheduler().runTaskLater(OpenLab.getInstance(), Runnable {
                    if (event.currentItem?.type == Material.RED_WOOL) event.currentItem = null
                }, 10)
            }
            Material.TNT -> {
                game.end()
                event.currentItem = ItemStack(Material.RED_WOOL, 1)
                player.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 1.0F)
            }
            else -> {}
        }
    }
    fun start(player: Player, crop: Material) {
        games[player.uniqueId] = FarmerMinigame(crop, player.uniqueId)
    }
}
class FarmerMinigame {
    val owner: UUID
    var score = 0
    var speed = 20
        set(value) {
            if (value > 10) {
                field = value
                repeatingTask.cancel()
                repeatingTask = Bukkit.getScheduler().runTaskTimer(
                    OpenLab.getInstance(),
                    Runnable {
                        if (isActive) {
                            spawnItem(items.random())
                        }
                    },
                    10L,
                    1L * value
                )
            }
        }
    var lives = 3
        set(value) {
            if (value > 0) {
                field = value
            } else {
                end()
            }
        }
    val crop: Material
    var isActive = true
    private val task: BukkitTask
    var repeatingTask: BukkitTask
    var inventory: Inventory
    val items: MutableCollection<Material> = mutableListOf(Material.BONE_MEAL, Material.CLOCK,
        Material.ROTTEN_FLESH, Material.BONE, Material.CREEPER_HEAD, Material.ZOMBIE_HEAD,Material.ZOMBIE_HEAD, Material.TNT,)
    constructor(crop: Material, uuid: UUID) {
        owner = uuid

        this.crop = crop
        items.add(crop)
        items.add(crop)

        task = Bukkit.getScheduler().runTaskLater(
            OpenLab.getInstance(),
            Runnable {
                if (isActive) {
                    end()
                }
            },
            20 * 30L
        )
        inventory = Bukkit.createInventory(null,27, "Collect the farmables")

        repeatingTask = Bukkit.getScheduler().runTaskTimer(
            OpenLab.getInstance(),
            Runnable {
                if (isActive) {
                    spawnItem(items.random())
                }
            },
            0,
            1L * speed
        )
        val player = Bukkit.getPlayer(owner) ?: return

        player.openInventory(inventory)
    }
    fun end() {
        isActive = false
        val player = Bukkit.getPlayer(owner) ?: return
        val customPlayer = CustomPlayerManager.getCustomPlayerOrThrow(player)
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1f)
        repeatingTask.cancel()
        task.cancel()


        val level = customPlayer.getSkillLevel(SkillType.FARMER)
        val tierWeights: Map<String, Int> = SpecializationConfig.farmerConfig.getConfigList("FARMER_TREASURE_TIERS").associate { config ->
            config.getString("skill_level") to config.getInt("chance")
        }

        val maxTier = SkillLevel.Companion.getSkillLevelFromInt(level)
        val tier = MinerTressureChance.selectWeightedTier(tierWeights, maxTier)

        val lootKey = NamespacedKey("openlabs", "treasure/tiers/${tier.lowercase()}-farmer-treasure")
        val lootTable = Bukkit.getLootTable(lootKey) ?: return OpenLab.logger.warning { "Missing ${tier}-farmer-treasure loot table" }

        val lootContext = LootContext.Builder(player.location)
            .killer(player)
            .luck(0f)
            .lootedEntity(player)
            .build()
        inventory = Bukkit.createInventory(null, 27, "Reward: ${tier.lowercase()}")
        player.closeInventory()
        player.openInventory(inventory)
        inventory.setItem(13, ItemStackUtils.getItemStack(crop.key, (score * ThreadLocalRandom.current().nextDouble(3.0)).toInt()))
        lootTable.fillInventory(inventory, Random(), lootContext);
        val centerLocation = player.location.clone().add(0.5, 0.5, 0.5)
        EffectsUtil.spawnLootEffect(centerLocation, 200)
    }
    fun spawnItem(material: Material) {
        val item = ItemStack(material)
        val slot = ThreadLocalRandom.current().nextInt(27)
        val count = usedSlotsInInventory(inventory)
        if (count > 3) {
            removeRandomItem(inventory)
        }
        inventory.setItem(slot, item)
    }
    fun removeRandomItem(inventory: Inventory) {
        val slotsWithItems = mutableListOf<Int>()
        for (i in inventory.contents.indices) {
            val item = inventory.getItem(i)
            if (item != null && item.type != Material.AIR) {
                slotsWithItems.add(i)
            }
        }
        if (slotsWithItems.isEmpty()) return

        val randomSlot = slotsWithItems.random()
        inventory.setItem(randomSlot, null)
    }
    fun usedSlotsInInventory(inventory: Inventory): Int {
        var count = 0
        for (item in inventory.contents) {
            if (item != null) count ++
        }

        return count
    }
}