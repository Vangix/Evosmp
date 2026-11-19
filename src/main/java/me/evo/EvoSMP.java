package me.evo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class EvoSMP extends JavaPlugin implements Listener {

    static final int MAX = 5;
    NamespacedKey key;

    @Override
    public void onEnable() {
        key = new NamespacedKey(this, "evo");
        getServer().getPluginManager().registerEvents(this, this);
    }

    boolean isWeapon(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_SPEAR")
                || m == Material.MACE || m == Material.BOW
                || m == Material.CROSSBOW || m == Material.TRIDENT;
    }

    /** -1 = у предмета нет шкалы */
    int getLevel(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return -1;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(key, PersistentDataType.INTEGER, -1);
    }

    void setLevel(ItemStack item, int level) {
        final int lvl = Math.max(0, Math.min(MAX, level));
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, lvl);
            meta.lore(List.of(
                Component.text("Evo: ", NamedTextColor.GRAY)
                    .append(Component.text("■".repeat(lvl), NamedTextColor.GOLD))
                    .append(Component.text("□".repeat(MAX - lvl), NamedTextColor.DARK_GRAY))
                    .append(Component.text(" " + lvl + "/" + MAX, NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false)
            ));
            if (item.getType() == Material.CROSSBOW) {
                int qc = Math.min(3, (lvl + 1) / 2);
                if (qc > 0) meta.addEnchant(Enchantment.QUICK_CHARGE, qc, true);
                else meta.removeEnchant(Enchantment.QUICK_CHARGE);
            }
        });
    }

    int projLevel(Entity e) {
        return e.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        ItemStack result = e.getCurrentItem();
        if (result == null || !isWeapon(result.getType())) return;
        ItemStack copy = result.clone();
        setLevel(copy, 0);
        e.setCurrentItem(copy);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();

        for (ItemStack it : e.getDrops()) {
            int l = getLevel(it);
            if (l > 0) setLevel(it, l - 1);
        }
        if (e.getKeepInventory()) {
            for (ItemStack it : dead.getInventory().getContents()) {
                int l = getLevel(it);
                if (l > 0) setLevel(it, l - 1);
            }
        }

        Player killer = dead.getKiller();
        if (killer != null && killer != dead) {
            ItemStack hand = killer.getInventory().getItemInMainHand();
            int l = getLevel(hand);
            if (l >= 0 && l < MAX) {
                setLevel(hand, l + 1);
                killer.sendActionBar(Component.text(
                        "Оружие эволюционировало: " + (l + 1) + "/" + MAX, NamedTextColor.GOLD));
            }
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        ItemStack bow = e.getBow();
        int l = getLevel(bow);
        if (l <= 0) return;

        e.getProjectile().getPersistentDataContainer().set(key, PersistentDataType.INTEGER, l);

        if (bow.getType() == Material.BOW && e.getForce() < 1.0f) {
            double boost = Math.min(1.0 / Math.max(e.getForce(), 0.1), 1 + 0.1 * l);
            e.getProjectile().setVelocity(e.getProjectile().getVelocity().multiply(boost));
        }
    }

    @EventHandler
    public void onTridentThrow(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof Trident t && t.getShooter() instanceof Player) {
            int l = getLevel(t.getItemStack());
            if (l > 0) t.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, l);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            int l = getLevel(hand);
            if (l <= 0) return;

            if (hand.getType() == Material.MACE) {
                float fall = p.getFallDistance();
                if (fall > 1.5f) e.setDamage(e.getDamage() + 0.2 * l * fall);
            } else if (hand.getType() != Material.BOW && hand.getType() != Material.CROSSBOW) {
                e.setDamage(e.getDamage() + 0.5 * l);
            }
            return;
        }

        if (e.getDamager() instanceof Trident t) {
            int l = projLevel(t);
            if (l > 0) e.setDamage(e.getDamage() + 0.5 * l);
        } else if (e.getDamager() instanceof AbstractArrow a) {
            int l = projLevel(a);
            if (l > 0) e.setDamage(e.getDamage() * (1 + 0.1 * l));
        }
    }
}
