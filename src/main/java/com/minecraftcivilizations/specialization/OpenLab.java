package com.minecraftcivilizations.specialization;
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining.FarmerMinigame;
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining.MinerTressureChance;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.server.level.EntityPlayer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;

import com.minecraftcivilizations.specialization.Combat.Mobs.HuntPlayerMobGoal;
import com.minecraftcivilizations.specialization.GUI.GUIManager;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.ComponentUtils;
import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Difficulty;
import co.aikar.commands.PaperCommandManager;
import com.minecraftcivilizations.specialization.Combat.*;
import com.minecraftcivilizations.specialization.Command.*;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.Data.DataManager;
import com.minecraftcivilizations.specialization.Listener.Blocks.ReinforcementProtectionListener;
import com.minecraftcivilizations.specialization.Listener.BurnListener;
import com.minecraftcivilizations.specialization.Listener.Player.*;
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining.BreakBlockListener;
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining.PlayerMineListener;
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.PlaceBlockListener;
import com.minecraftcivilizations.specialization.Listener.Player.Interactions.*;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.CraftingListener;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.FurnaceListener;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.StonecutterListener;
import com.minecraftcivilizations.specialization.Listener.RepairingListener;
import com.minecraftcivilizations.specialization.Listener.TimeSyncListener;
import com.minecraftcivilizations.specialization.Listener.XpTransferBookListener;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.player.LocalNameGenerator;
import com.minecraftcivilizations.specialization.player.PreJoinEventListener;
import com.minecraftcivilizations.specialization.Recipe.RecipeBlocker;
import com.minecraftcivilizations.specialization.Recipe.Recipes;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.SmartEntity.SmartEntityManager;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.StaffTools.DebugListenCommand;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class OpenLab extends JavaPlugin {

    public final static String TITLE = "<#334422>[<#445533>CivLabs</#445533>]";
    public static Logger logger;
    public ReviveListener reviveListener;
    //Holder for transient player data such as cooldowns
    PaperCommandManager commandManager;
    @Getter
    public static LocalNameGenerator localNameGenerator;
    private Debug debug;
    private PhantomRideListener phantomRideListener;
    public static AudienceProvider adventure;
    //    private EmoteListener emoteListener;
    @Getter
    public LocalChat localChat;
    //follow this pattern from now on
    @Getter
    private SmartEntityManager smart_entity_manager;
    @Getter
    public CustomItemManager customItemManager;
    @Getter
    private CombatManager combatManager;
    @Getter
    private BlacksmithArmorTrim armorTrimSystem;
    @Getter
    private PVPManager pvpManager;
    private XPMonitoringCommand xpMonitoringCommand;

    @Getter
    private FoodDurationTicker foodDurationTicker;
    @Getter
    private PlayerDownedListener playerDownedListener;
    @Getter
    public HuntPlayerMobGoal huntPlayerMobGoalSystem;
    private EmoteManager emoteManager;
    @Getter
    private PlayerClickListener playerClickListener;
    @Getter
    public static GUIManager guiManager;
    public static void notify(Player player, String msg) {
        message(player, msg);
    }

    public static void message(Player player, String msg) {
        PlayerUtil.message(player, msg, 0);
    }

    public static void message(Player player, Component msg) {
        PlayerUtil.message(player, msg, 0);
    }


    public static OpenLab getInstance() {
        return getPlugin(OpenLab.class);
    }

    @Override
    public void onEnable() {
        logger = getLogger();
        Skill.InitCacheXPLevelFormula();
        debug = new Debug(this);
        saveResource("first_names.txt", true);
        saveResource("last_names.txt", true);
        SpecializationConfig.initialize();
        // TODO PDC-xp-hotfix
        //  Skill.InitializeSkillKeys(this);

        adventure = BukkitAudiences.create(this);

        guiManager = new GUIManager();
        playerClickListener = new PlayerClickListener();
        localChat = new LocalChat();
        playerDownedListener = new PlayerDownedListener(this);
        reviveListener = new ReviveListener(playerDownedListener);
        smart_entity_manager = new SmartEntityManager(this);
        customItemManager = new CustomItemManager(this);
        customItemManager.initializeCustomItems();
        phantomRideListener = new PhantomRideListener(this);
        xpMonitoringCommand = new XPMonitoringCommand();
        emoteManager = new EmoteManager(customItemManager, this);
        pvpManager = new PVPManager(playerDownedListener, this);
        armorTrimSystem = new BlacksmithArmorTrim();
        foodDurationTicker = new FoodDurationTicker();
        huntPlayerMobGoalSystem = new HuntPlayerMobGoal(this);
//      emoteListener = new EmoteListener(this);

        getServer().getMessenger().registerIncomingPluginChannel(this, "civlabs:weathersync", new TimeSyncListener());

        //commands registered here
        setupCommands();
        getServer().getPluginManager().registerEvents(CustomPlayerManager.INSTANCE, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(playerClickListener, this);
        getServer().getPluginManager().registerEvents(new PlayerMineListener(), this);
        getServer().getPluginManager().registerEvents(new BreakBlockListener(), this);
        getServer().getPluginManager().registerEvents(new PlaceBlockListener(), this);
        getServer().getPluginManager().registerEvents(new RightClickListener(), this);
        getServer().getPluginManager().registerEvents(new MinerTressureChance(), this);

        getServer().getPluginManager().registerEvents(new BurnListener(), this);
        getServer().getPluginManager().registerEvents(new ExplodeListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractEntityListener(), this);
        combatManager = new CombatManager(this); // Guardsman Damage Output
        new FoodInteractionListener(this);
        getServer().getPluginManager().registerEvents(new HungerSystem(this, emoteManager), this);
        getServer().getPluginManager().registerEvents(new LeashListener(), this);
        getServer().getPluginManager().registerEvents(new BedListener(), this);
        getServer().getPluginManager().registerEvents(new ReinforcementProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new PreJoinEventListener(), this);
        getServer().getPluginManager().registerEvents(new StonecutterListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingListener(this), this);
        getServer().getPluginManager().registerEvents(new FurnaceListener(), this);

        //new TownManager();
        getServer().getPluginManager().registerEvents(new CrossBowListener(), this);
        getServer().getPluginManager().registerEvents(new LocalChat(), this);
        getServer().getPluginManager().registerEvents(new PatDown(), this);
        getServer().getPluginManager().registerEvents(new XpTransferBookListener(), this);
        getServer().getPluginManager().registerEvents(new RepairingListener(), this);
        getServer().getPluginManager().registerEvents(phantomRideListener, this);
        getServer().getPluginManager().registerEvents(playerDownedListener, this);
        getServer().getPluginManager().registerEvents(reviveListener, this);
        getServer().getPluginManager().registerEvents(RecipeBlocker.INSTANCE, this);
        getServer().getPluginManager().registerEvents(FarmerMinigame.INSTANCE, this);
        getServer().getPluginManager().registerEvents(foodDurationTicker, this);
        getServer().getPluginManager().registerEvents(huntPlayerMobGoalSystem, this);
        //town data does not need to wait anymore
        //TownManager.scanAllPlayersForTownsAsync();

        //overworld game rules
//        World nether = Bukkit.getWorlds().get(1);

        for(World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.SPAWN_RADIUS, 350);
            world.setDifficulty(Difficulty.HARD);
            world.setGameRule(GameRule.REDUCED_DEBUG_INFO, true);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
            world.setGameRule(GameRule.WATER_SOURCE_CONVERSION, false);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.DO_LIMITED_CRAFTING, true);
        }

        //global game rules
        Bukkit.getWorlds().forEach(w -> w.setGameRule(GameRule.NATURAL_REGENERATION, false));
        Bukkit.getWorlds().forEach(w -> w.setGameRule(GameRule.DO_TRADER_SPAWNING, false));

        Recipes.init();
        XpGainMonitor.init();

        //Bukkit.updateRecipes();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (CustomPlayerManager.INSTANCE.hasCustomPlayer(uuid)) {
                CustomPlayerManager.INSTANCE.load(player.getUniqueId());
            }
        }

        combatManager.initialize();

        DataManager.startSaver(this);
        ReinforcementManager.startReinforcement();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (Player p : Bukkit.getOnlinePlayers()) {
            phantomRideListener.PhantomStateSave(p);
        }
        emoteManager.shutdown();
        smart_entity_manager.shutdown();
        DataManager.getScheduler().shutdown();
        CustomPlayerManager.INSTANCE.saveAll();
        XpGainMonitor.saveConfigToDisk();
    }

    private void setupCommands() {

        try {
            localNameGenerator = new LocalNameGenerator(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Cleans up optional names held in temp reserves if a name was chosen
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            localNameGenerator.cleanupExpiredTemps();
        }, 0L, 1 * 60 * 20L); // every 1 minute


        commandManager = new PaperCommandManager(this);

        // --- TAB COMPLETIONS ---
        commandManager.getCommandCompletions().registerCompletion("classes", c ->
                Arrays.stream(SkillType.values())
                        .map(Enum::name)
                        .collect(Collectors.toList())
        );

        // somewhere during plugin init
        commandManager.getCommandCompletions().registerCompletion("monitorTypes", c ->
                Arrays.asList("threshold", "cooldown")
        );


        commandManager.registerCommand(new ClassCommand());
        commandManager.registerCommand(new SetXpCommand());
        commandManager.registerCommand(new SetLoreCommand());
        commandManager.registerCommand(new SuicideCommand(playerDownedListener, pvpManager, this));
        commandManager.registerCommand(new RestoreHealthCommand());
        commandManager.registerCommand(new NotifyRestartCommand());
        commandManager.registerCommand(new RecipesCommand());
        commandManager.registerCommand(new PurgeGoldenApplesCommand());
        commandManager.registerCommand(new RandomNameBulkTestCommand());
        commandManager.registerCommand(new RerollNameCommand(localNameGenerator));
        commandManager.registerCommand(new NameChoiceCommand(localNameGenerator));
//        commandManager.registerCommand(new XPLeaderboardCommand()); // (Loads player configs and causes XP Loss) TODO DO NOT ENABLE UNTIL FIXED
        commandManager.registerCommand(new CustomItemCommand(customItemManager));
        commandManager.registerCommand(new SudoChatCommand(localChat));
        commandManager.registerCommand(new XPMonitoringCommand());
        commandManager.registerCommand(new RecipeRefreshCommand());
        commandManager.registerCommand(emoteManager);
        commandManager.registerCommand(new ReloadConfigExecutor());
        new DebugListenCommand(commandManager);


    }


    public void applyCustomName(Player player, Component name) {
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE
                .getCustomPlayer(player.getUniqueId());

        // Update the CustomPlayer's stored name
        customPlayer.setName(name);

        // JUST use Bukkit methods - they work on Arclight
        String displayName = ComponentUtils.serializeComponentAsStringWithStrip(name);
        player.setDisplayName(displayName);
        player.setPlayerListName(displayName);

        player.setMetadata("nickname", new FixedMetadataValue(
                OpenLab.getInstance(), displayName));

        // Force client refresh - this updates name tags


        try {
            EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();

            // Update GameProfile (risky but works)
            GameProfile profile = ((CraftPlayer) player).getProfile();
            Field nameField = GameProfile.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(profile, displayName);
            // Update NMS listName properly
            IChatBaseComponent listComponent = IChatBaseComponent.ChatSerializer.a(
                    "{\"text\":\"" + displayName + "\"}"
            );
            entityPlayer.listName = listComponent;
        } catch (Exception e) {
            // Log but don't crash - Bukkit methods already did the main work
            getLogger().warning("Failed to update NMS name fields: " + e.getMessage());
        }
        Bukkit.getScheduler().runTask(OpenLab.getInstance(), () -> {
            for (Player all : Bukkit.getOnlinePlayers()) {
                all.hidePlayer(OpenLab.getInstance(), player);
                all.showPlayer(OpenLab.getInstance(), player);
            }
        });
    }

    public Debug getDebugUtils() {
        return debug;
    }
}
