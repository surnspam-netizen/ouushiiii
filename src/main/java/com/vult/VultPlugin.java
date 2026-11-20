package com.vult;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * /vult          draws "WELCOME TO / VULT" in dust particles in front of you
 * /vult delete   removes every sign
 *
 * Particles only last about a second, so a repeating task redraws every sign.
 * Signs live in memory only: they are gone after a restart or /reload.
 */
public final class VultPlugin extends JavaPlugin implements TabExecutor {

    private static final double CELL = 0.06;      // size of one pixel in blocks
    private static final int MAX_SIGNS = 20;
    private static final long REDRAW_TICKS = 4L;

    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.WHITE, 0.5f);
    private static final Particle.DustOptions YELLOW = new Particle.DustOptions(Color.fromRGB(255, 217, 0), 0.5f);

    private static final Map<Character, String[]> FONT = new HashMap<>();

    static {
        FONT.put('W', new String[]{"Y...Y", "Y...Y", "Y...Y", "Y.Y.Y", "Y.Y.Y", "YY.YY", "Y...Y"});
        FONT.put('E', new String[]{"YYYYY", "Y....", "Y....", "YYYY.", "Y....", "Y....", "YYYYY"});
        FONT.put('L', new String[]{"Y....", "Y....", "Y....", "Y....", "Y....", "Y....", "YYYYY"});
        FONT.put('C', new String[]{".YYY.", "Y...Y", "Y....", "Y....", "Y....", "Y...Y", ".YYY."});
        FONT.put('O', new String[]{".YYY.", "Y...Y", "Y...Y", "Y...Y", "Y...Y", "Y...Y", ".YYY."});
        FONT.put('M', new String[]{"Y...Y", "YY.YY", "Y.Y.Y", "Y.Y.Y", "Y...Y", "Y...Y", "Y...Y"});
        FONT.put('T', new String[]{"YYYYY", "..Y..", "..Y..", "..Y..", "..Y..", "..Y..", "..Y.."});
        FONT.put('V', new String[]{"Y...Y", "Y...Y", "Y...Y", "Y...Y", ".Y.Y.", ".Y.Y.", "..Y.."});
        FONT.put('U', new String[]{"Y...Y", "Y...Y", "Y...Y", "Y...Y", "Y...Y", "Y...Y", ".YYY."});
        FONT.put(' ', new String[]{"...", "...", "...", "...", "...", "...", "..."});
    }

    /** One placed sign: fixed world positions for every dust particle. */
    private record Sign(List<Location> white, List<Location> yellow) {
    }

    private final List<Sign> signs = new CopyOnWriteArrayList<>();

    @Override
    public void onEnable() {
        if (getCommand("vult") != null) {
            getCommand("vult").setExecutor(this);
            getCommand("vult").setTabCompleter(this);
        }
        getServer().getScheduler().runTaskTimer(this, this::redraw, 20L, REDRAW_TICKS);
    }

    @Override
    public void onDisable() {
        signs.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vult.use")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("delete")) {
            int removed = signs.size();
            signs.clear();
            sender.sendMessage(Component.text("Deleted " + removed + " VULT sign" + (removed == 1 ? "" : "s") + ".",
                    NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can place the sign. Use /vult delete to remove them.",
                    NamedTextColor.RED));
            return true;
        }

        if (signs.size() >= MAX_SIGNS) {
            player.sendMessage(Component.text("Too many signs. Use /vult delete first.", NamedTextColor.RED));
            return true;
        }

        signs.add(build(player));
        player.sendMessage(Component.text("Welcome to ", NamedTextColor.WHITE)
                .append(Component.text("VULT", NamedTextColor.YELLOW)));
        return true;
    }

    // ------------------------------------------------------------- building

    /** Lays the sign out 2 blocks in front of the player, facing them, at eye level. */
    private Sign build(Player player) {
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 0.0001) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        // looking along "forward", the viewer's right-hand side
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        Location centre = player.getEyeLocation().add(forward.clone().multiply(2));

        List<Location> white = new ArrayList<>();
        List<Location> yellow = new ArrayList<>();
        addLine("WELCOME TO", centre, right, 5.0, white);
        addLine("VULT", centre, right, -5.0, yellow);
        return new Sign(white, yellow);
    }

    /**
     * Adds the particle positions for one line of text, centred horizontally.
     * {@code topRow} is how many pixel-rows above the centre the line's top row sits.
     */
    private void addLine(String text, Location centre, Vector right, double topRow, List<Location> out) {
        int totalWidth = 0;
        for (char c : text.toCharArray()) {
            totalWidth += FONT.get(c)[0].length() + 1;
        }
        totalWidth -= 1;

        int offset = 0;
        for (char c : text.toCharArray()) {
            String[] glyph = FONT.get(c);
            for (int row = 0; row < glyph.length; row++) {
                for (int col = 0; col < glyph[row].length(); col++) {
                    if (glyph[row].charAt(col) != 'Y') {
                        continue;
                    }
                    double x = (offset + col + 0.5 - totalWidth / 2.0) * CELL;
                    double y = (topRow - row - 0.5) * CELL;
                    out.add(centre.clone().add(right.clone().multiply(x)).add(0, y, 0));
                }
            }
            offset += glyph[0].length() + 1;
        }
    }

    // ------------------------------------------------------------- drawing

    private void redraw() {
        for (Sign sign : signs) {
            for (Location l : sign.white()) {
                l.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, WHITE);
            }
            for (Location l : sign.yellow()) {
                l.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, YELLOW);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "delete".startsWith(args[0].toLowerCase(Locale.ROOT))
                && sender.hasPermission("vult.use")) {
            return List.of("delete");
        }
        return List.of();
    }
}
