package dev.stealanegg;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

public final class Signs {
    private Signs() {}

    /** Writes up to 4 lines of '&' coloured text on the front of the sign at this block. */
    public static void write(Block b, String... lines) {
        if (!(b.getState() instanceof Sign sign)) return;
        SignSide side = sign.getSide(Side.FRONT);
        for (int i = 0; i < 4; i++) {
            side.line(i, i < lines.length ? Msg.c(lines[i]) : Component.empty());
        }
        sign.setWaxed(true);
        sign.update(true, false);
    }
}
