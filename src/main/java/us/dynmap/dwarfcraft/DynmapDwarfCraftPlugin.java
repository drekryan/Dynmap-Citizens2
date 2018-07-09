package us.dynmap.dwarfcraft;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.Jessy1237.DwarfCraft.DwarfCraft;
import com.Jessy1237.DwarfCraft.models.DwarfSkill;
import com.Jessy1237.DwarfCraft.models.DwarfTrainer;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class DynmapDwarfCraftPlugin extends JavaPlugin {
    private static Logger log;

    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    DwarfCraft dwarfCraft;
    private MarkerIcon deficon;
    private MarkerSet trainerset;
    HashMap<Integer, DwarfSkill> skills;
    boolean reload = false;
    
    FileConfiguration cfg;
    
    private Set<String> existingtrainers = new HashSet<>();

    private void processTrainer(MarkerSet set, DwarfTrainer trainer) {
        processTrainer(set, trainer, null);
    }
    
    private void processTrainer(MarkerSet set, DwarfTrainer trainer, Set<String> toremove) {
        UUID uuid = trainer.getEntity().getUniqueId();
        String id = "npc_" + Long.toHexString(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        Entity ent = null;
        if (trainer.getEntity().isSpawned()) {
            ent = trainer.getEntity().getEntity();
        }
        if (ent == null) {  // If null, see if we need to remove it
            if (existingtrainers.contains(id)) {    // Found?
                Marker m = set.findMarker(id);
                if (m != null) {
                    m.deleteMarker();
                }
                existingtrainers.remove(id);
            }
        }
        else {
            Location loc = ent.getLocation();
            Marker m = set.findMarker(id);
            DwarfSkill skill = skills.get( trainer.getSkillTrained() );
            int minLevel = trainer.getMinSkill();

            if (minLevel < 0) minLevel = 0;
            if (m == null) {
                m = set.createMarker(id, trainer.getName(), false, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), deficon, false);
                m.setDescription( "Skill: " + skill.getDisplayName() + " <br> Min Level: " + minLevel + " <br>Max Level: " + trainer.getMaxSkill() );
                existingtrainers.add(id);
            }
            else {
                m.setLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                m.setLabel(trainer.getName());
                m.setDescription( "Skill: " + skill.getDisplayName() + " <br> Min Level: " + minLevel + " <br>Max Level: " + trainer.getMaxSkill() );
            }
            if (toremove != null) {
                toremove.remove(id);
            }
        }
    }
    
    private void updateAllNPCs(MarkerSet trainerset) {
        HashMap<Integer, DwarfTrainer> reg = dwarfCraft.getDataManager().trainerList;
        HashSet<String> toremove = new HashSet<String>(existingtrainers);
        if (reg != null) {
            for (DwarfTrainer trainer : reg.values()) {
                processTrainer(trainerset, trainer, toremove);
            }
        }
        for (String s : toremove) {
            Marker m = trainerset.findMarker(s);
            if (m != null) {
                m.deleteMarker();
            }
            existingtrainers.remove(s);
        }
    }

    @Override
    public void onLoad() {
        log = this.getLogger();
    }

    long updperiod;
    long playerupdperiod;
    boolean stop;
    
    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }
    
    private class OurServerListener implements Listener {
        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if(name.equals("dynmap") || name.equals("Essentials")) {
                if(dynmap.isEnabled() && dwarfCraft.isEnabled())
                    activate();
            }
        }
    }
    
    public void onEnable() {
        info("initializing");
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if(dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapAPI) dynmap; /* Get API */
        /* Get DwarfCraft */
        Plugin p = pm.getPlugin("DwarfCraft");
        if(p == null) {
            severe("Cannot find DwarfCraft!");
            return;
        }
        dwarfCraft = (DwarfCraft) p;

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);

        /* If both enabled, activate */
        if(dynmap.isEnabled() && dwarfCraft.isEnabled()) {
            activate();
        }
    }

    private class MarkerUpdate implements Runnable {
        public void run() {
            if (!stop) {
                updateAllNPCs(trainerset);
                getServer().getScheduler().scheduleSyncDelayedTask(DynmapDwarfCraftPlugin.this, this, updperiod);
            }
        }
    }
    
    private void activate() {
        /* Now, get markers API */
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading Dynmap marker API!");
            return;
        }

        /* Load configuration */
        if(reload) {
            reloadConfig();
        }
        else {
            reload = true;
        }
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */
        deficon = markerapi.getMarkerIcon("bookshelf");
        trainerset = markerapi.getMarkerSet("DwarfCraft");
        if (trainerset == null) {
            trainerset = markerapi.createMarkerSet("DwarfCraft", "DwarfCraft Trainers", null, false);
        }

        skills = dwarfCraft.getConfigManager().getAllSkills();
        updateAllNPCs( trainerset );

        /* Set up update job - based on period */
        double per = cfg.getDouble("update.period", 5.0);
        if(per < 2.0) per = 2.0;
        updperiod = (long)(per*20.0);
        stop = false;
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 5*20);

        info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        stop = true;
    }

}
