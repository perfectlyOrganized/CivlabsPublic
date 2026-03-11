package com.minecraftcivilizations.specialization.Recipe

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillLevel
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager.getCustomPlayer
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerJoinEvent
import java.util.*
import java.util.function.Supplier
import java.util.stream.Collectors


object RecipeBlocker : Listener {
    @EventHandler
    fun onPrepare(e: PrepareItemCraftEvent) {
        val recipe = e.recipe ?: return
        if (recipe is Keyed) {
            if (BLOCKED_RECIPES.contains(recipe.key)) {
                e.inventory.result = null
            }
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        // Small delay to let player load
        Bukkit.getScheduler().runTaskLater(OpenLab.getInstance(), Runnable {
            updateRecipes(event.getPlayer())
        }, 10L)
    }


    private val BLOCKED_RECIPES: MutableSet<NamespacedKey?> = HashSet<NamespacedKey?>()
    private val ALL_RECIPES: MutableSet<NamespacedKey> = HashSet()
    private val recipeRequirements: MutableMap<NamespacedKey?, MutableList<String>?> =
        HashMap<NamespacedKey?, MutableList<String>?>()
    private var cacheLoaded = false

    init {
        BLOCKED_RECIPES.add(NamespacedKey.minecraft("rail"))
    }
    private fun loadAllRecipes() {
        if (ALL_RECIPES.isNotEmpty()) return

        Bukkit.getServer().recipeIterator().forEachRemaining { recipe ->
            if (recipe is Keyed) {
                ALL_RECIPES.add(recipe.key)
            }
        }
        OpenLab.logger.info { "Loaded ${ALL_RECIPES.size} total recipes" }
    }

    private fun ensureCacheLoaded() {
        if (cacheLoaded) return

        recipeRequirements.clear()

        // Track which recipes are locked behind skills (these are the ones in config)
        val lockedRecipesFromConfig = mutableSetOf<NamespacedKey?>()

        for (skillType in SkillType.entries) {
            for (skillLevel in SkillLevel.values) {
                val key = skillType.toString() + "_" + skillLevel
                if (!SpecializationConfig.unlockedRecipesConfig.config.hasPath(key)) continue;
                val recipes = SpecializationConfig.unlockedRecipesConfig.getStringList(key)
                for (recipeStr in recipes) {
                    if (recipeStr == null) continue

                    val recipeKey = NamespacedKey.fromString(recipeStr) ?: continue
                    lockedRecipesFromConfig.add(recipeKey)

                    val requirement = skillType.toString() + ":" + skillLevel.level
                    recipeRequirements.computeIfAbsent(recipeKey) { mutableListOf<String>() }?.add(requirement)
                }
            }
        }

        // BLOCKED_RECIPES should only contain recipes that are in the config
        // (since those are the ones that require skills to unlock)
        BLOCKED_RECIPES.clear()
        BLOCKED_RECIPES.addAll(lockedRecipesFromConfig)
        cacheLoaded = true
    }

    fun reloadCache() {
        cacheLoaded = false
        ensureCacheLoaded()

        for (player in Bukkit.getOnlinePlayers()) {
            updateRecipes(player)
        }
    }

    fun updateRecipes(player: Player) {
        ensureCacheLoaded()
        loadAllRecipes()

        val customPlayer = getCustomPlayer(player.uniqueId) ?: return

        val allowed: MutableSet<NamespacedKey> = HashSet(ALL_RECIPES)
        allowed.addAll(customPlayer.additionUnlockedRecipes)

        for (entry in recipeRequirements.entries) {
            val recipe = entry.key
            val requirements: MutableList<String> = entry.value!!

            var canUse = false
            for (req in requirements) {
                val parts: Array<String?> = req.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val skillType = SkillType.valueOf(parts[0]!!)
                val level = parts[1]!!.toInt()

                if (customPlayer.getSkillLevel(skillType) >= level) {
                    canUse = true
                    break
                }
            }

            if (recipe != null && (canUse || requirements.isEmpty())) {
                allowed.add(recipe)
            }
        }

        player.discoverRecipes(allowed)
    }

    fun getRecipes(skillType: SkillType?, level: Int): MutableSet<NamespacedKey?> {
        val key = skillType.toString() + "_" + SkillLevel.getSkillLevelFromInt(level)
        if (!SpecializationConfig.unlockedRecipesConfig.config.hasPath(key)) return LinkedHashSet()
        return SpecializationConfig.unlockedRecipesConfig
            .getStringList(key)
            .stream()
            .filter { obj: String? -> Objects.nonNull(obj) }
            .map<NamespacedKey?> { string: String? ->
                try {
                    return@map NamespacedKey.fromString(string!!)
                } catch (exception: Exception) {
                    OpenLab.logger.warning("$string could not be converted to a namespace")
                    return@map null
                }
            }
            .filter { obj: NamespacedKey? -> Objects.nonNull(obj) }
            .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))
    }


    fun shouldBlockRecipe(player: Player, recipeKey: NamespacedKey?): Boolean {
        ensureCacheLoaded()

        val customPlayer = getCustomPlayer(player.uniqueId) ?: return true
        if (customPlayer.additionUnlockedRecipes.contains(recipeKey)) return false
        val requirements: MutableList<String> = recipeRequirements[recipeKey] ?: return false

        for (req in requirements) {
            val parts: Array<String?> = req.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val skillType = SkillType.valueOf(parts[0]!!)
            val level = parts[1]!!.toInt()

            if (customPlayer.getSkillLevel(skillType) >= level) return false
        }
        return true
    }

}