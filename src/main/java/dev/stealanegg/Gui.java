package dev.stealanegg;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

public final class Gui implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public Gui(int rows, String title) { inv = Bukkit.createInventory(this, rows * 9, Util.c(title)); }

    public void set(int slot, ItemStack it, Consumer<InventoryClickEvent> a) {
        inv.setItem(slot, it);
        if (a != null) actions.put(slot, a);
    }

    public void click(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    @Override public Inventory getInventory() { return inv; }

    public static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Util.c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Util.c(s));
        im.lore(l);
        it.setItemMeta(im);
        return it;
    }
}
