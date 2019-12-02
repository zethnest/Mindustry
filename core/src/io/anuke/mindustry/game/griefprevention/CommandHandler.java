package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.Core;
import io.anuke.arc.collection.Array;
import io.anuke.arc.func.Cons;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.BlockPart;

import static io.anuke.mindustry.Vars.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

// introducing the worst command system known to mankind
public class CommandHandler {
    public class Context {
        public List<String> args;

        public Context(List<String> args) {
            this.args = args;
        }
    }

    public HashMap<String, Cons<Context>> commands = new HashMap<>();

    public CommandHandler() {
        addCommand("fixpower", this::fixPower);
        addCommand("verbose", createToggle("verbose", "verbose logging", v -> griefWarnings.verbose = v));
        addCommand("debug", createToggle("debug", "debug logging", v -> griefWarnings.debug = v));
        addCommand("spam", createToggle("spam", "verbose and debug logging", v -> {
            griefWarnings.verbose = v;
            griefWarnings.debug = v;
        }));
        addCommand("broadcast", createToggle("broadcast", "broadcast of messages", v -> griefWarnings.broadcast = v));
        addCommand("tileinfo", this::tileInfo);
        addCommand("players", this::players);
        addCommand("votekick", this::votekick);
        addCommand("tileinfohud", createToggle("tileinfohud", "tile information hud", v -> griefWarnings.tileInfoHud = v));
    }

    public void addCommand(String name, Cons<Context> handler) {
        commands.put(name, handler);
    }

    public void reply(String message) {
        ui.chatfrag.addMessage(message, null);
    }

    public boolean runCommand(String message) {
        if (!message.startsWith("/")) return false;
        String[] args = message.split(" ");
        args[0] = args[0].substring(1);
        Cons<Context> command = commands.get(args[0].toLowerCase());
        if (command == null) return false;
        command.get(new Context(Arrays.asList(args)));
        return true;
    }

    /**
     * Reconnect every power node to everything it can connect to, intended to
     * be used after power griefing incidents.
     * If "redundant" is present as an argument, connect the block even if it is
     * already part of the same power graph.
     */
    public void fixPower(Context ctx) {
        boolean redundant = ctx.args.contains("redundant");
        griefWarnings.fixer.fixPower(redundant);
        reply("[green]Done");
    }

    public Cons<Context> createToggle(String name, String description, Cons<Boolean> consumer) {
        return ctx -> {
            if (ctx.args.size() < 2) {
                reply("[scarlet]Not enough arguments");
                reply("Usage: " + name + " <on|off>");
                return;
            }
            switch (ctx.args.get(1).toLowerCase()) {
                case "on":
                case "true":
                    consumer.get(true);
                    reply("Enabled " + description);
                    break;
                case "off":
                case "false":
                    consumer.get(false);
                    reply("Disabled " + description);
                    break;
                default:
                    reply("[scarlet]Not enough arguments");
                    reply("Usage: " + name + " <on|off>");
                    return;
            }
            griefWarnings.saveSettings();
        };
    }

    public String tileInfo(Tile tile) {
        TileInfo info = griefWarnings.tileInfo.get(tile);
        if (info != null && info.link != null) info = info.link;
        Array<String> out = new Array<>();
        out.add("Tile at " + griefWarnings.formatTile(tile));
        Block currentBlock = tile.block();
        if (currentBlock == null) {
            out.add("[yellow]Nonexistent block");
            return String.join("\n", out);
        }
        if (currentBlock instanceof BlockPart) currentBlock = tile.link().block();
        out.add("Current block: " + currentBlock.name);
        if (info == null) {
            out.add("[yellow]No information");
            return String.join("\n", out);
        }
        out.add("Constructed by: " + griefWarnings.formatPlayer(info.constructedBy));
        out.add("Deconstructed by: " + griefWarnings.formatPlayer(info.deconstructedBy));
        if (info.previousBlock != null) out.add("Block that was here: " + info.previousBlock.name);
        out.add("Configured [accent]" + info.configureCount + "[] times");
        if (info.interactedPlayers.size > 0) {
            out.add("Players who have interacted with this block:");
            for (Player player : info.interactedPlayers.iterator()) {
                out.add("  - " + griefWarnings.formatPlayer(player));
            }
        } else out.add("No interaction information recorded");
        if (info.lastRotatedBy != null) out.add("Last rotated by: " + griefWarnings.formatPlayer(info.lastRotatedBy));
        return String.join("\n", out);
    }

    /** Get stored information for the tile under the cursor */
    public void tileInfo(Context ctx) {
        Vector2 vec = Core.input.mouseWorld(Core.input.mouseX(), Core.input.mouseY());
        Tile tile = world.tile(world.toTile(vec.x), world.toTile(vec.y));
        reply("====================");
        reply(tileInfo(tile));
    }

    /** Get list of all players and their ids */
    public void players(Context ctx) {
        StringBuilder response = new StringBuilder("Players:");
        for (Player player : playerGroup.all()) response.append("\n" + griefWarnings.formatPlayer(player));
        reply(response.toString());
    }

    /** Votekick overlay to allow /votekick using ids when prefixed by # */
    public void votekick(Context ctx) {
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size())).toLowerCase();
        Player target;
        if (name.startsWith("#")) {
            int id = Integer.parseInt(name.substring(1));
            target = playerGroup.getByID(id);
        } else {
            target = playerGroup.find(p -> p.name.toLowerCase().equals(name));
        }
        if (target == null) {
            reply("[scarlet]Player not found!");
            return;
        }
        reply("[cyan]Votekicking player:[] " + griefWarnings.formatPlayer(target));
        Call.sendChatMessage("/votekick " + target.name);
    }
}
