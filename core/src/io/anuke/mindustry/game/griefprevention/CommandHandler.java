package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.Core;
import io.anuke.arc.collection.Array;
import io.anuke.arc.func.Cons;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.mindustry.entities.traits.BuilderTrait.BuildRequest;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Teams.BrokenBlock;
import io.anuke.mindustry.game.Teams.TeamData;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.net.Packets.AdminAction;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Build;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.BlockPart;

import static io.anuke.mindustry.Vars.*;

import java.text.NumberFormat;
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
        addCommand("autoban", createToggle("autoban", "automatic bans", v -> griefWarnings.autoban = v));
        addCommand("rebuild", this::rebuild);
        addCommand("auto", this::auto);
        addCommand("nextwave", this::nextwave);
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

    public Array<String> tileInfo(Tile tile) {
        TileInfo info = griefWarnings.tileInfo.get(tile);
        Array<String> out = new Array<>();
        out.add("Tile at " + griefWarnings.formatTile(tile));
        Block currentBlock = tile.block();
        if (currentBlock == null) {
            out.add("[yellow]Nonexistent block");
            return out;
        }
        if (currentBlock instanceof BlockPart) currentBlock = tile.link().block();
        out.add("Current block: " + currentBlock.name);
        if (info == null) {
            out.add("[yellow]No information");
            return out;
        }
        Block previousBlock = info.previousBlock;
        if (info.link != null) info = info.link;
        out.add("Constructed by: " + griefWarnings.formatPlayer(info.constructedBy));
        out.add("Deconstructed by: " + griefWarnings.formatPlayer(info.deconstructedBy));
        if (previousBlock != null) out.add("Block that was here: " + previousBlock.name);
        out.add("Configured [accent]" + info.configureCount + "[] times");
        if (info.interactedPlayers.size > 0) {
            out.add("Players who have interacted with this block:");
            for (Player player : info.interactedPlayers.iterator()) {
                out.add("  - " + griefWarnings.formatPlayer(player));
            }
        } else out.add("No interaction information recorded");
        if (info.lastInteractedBy != null) out.add("Last interacted by: " + griefWarnings.formatPlayer(info.lastInteractedBy));
        if (info.lastRotatedBy != null) out.add("Last rotated by: " + griefWarnings.formatPlayer(info.lastRotatedBy));
        return out;
    }

    /** Get stored information for the tile under the cursor */
    public void tileInfo(Context ctx) {
        Vector2 vec = Core.input.mouseWorld(Core.input.mouseX(), Core.input.mouseY());
        Tile tile = world.tile(world.toTile(vec.x), world.toTile(vec.y));
        Array<String> out = tileInfo(tile);
        if (ctx.args.contains("send")) {
            for (String line : out) griefWarnings.sendMessage(line, false);
        } else {
            reply("====================");
            reply(String.join("\n", out));
        }
    }

    /** Get list of all players and their ids */
    public void players(Context ctx) {
        StringBuilder response = new StringBuilder("Players:");
        for (Player player : playerGroup.all()) response.append("\n" + griefWarnings.formatPlayer(player));
        reply(response.toString());
    }

    /** Get player by either id or full name */
    public Player getPlayer(String name) {
        Player target;
        if (name.startsWith("#")) {
            int id;
            try {
                id = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ex) {
                id = -1;
            }
            target = playerGroup.getByID(id);
        } else {
            target = playerGroup.find(p -> p.name.toLowerCase().equals(name));
        }
        return target;
    }

    /** Votekick overlay to allow /votekick using ids when prefixed by # */
    public void votekick(Context ctx) {
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size())).toLowerCase();
        Player target = getPlayer(name);
        if (target == null) {
            reply("[scarlet]Player not found!");
            return;
        }
        reply("[cyan]Votekicking player:[] " + griefWarnings.formatPlayer(target));
        Call.sendChatMessage("/votekick " + target.name);
    }

    /** Attempt rebuild of destroyed blocks */
    public void rebuild(Context ctx) {
        Team team = player.getTeam();
        TeamData data = state.teams.get(team);
        if (data.brokenBlocks.isEmpty()) {
            reply("Broken blocks queue is empty");
            return;
        }
        for (BrokenBlock broken : data.brokenBlocks) {
            if(Build.validPlace(team, broken.x, broken.y, content.block(broken.block), broken.rotation)) {
                Block block = content.block(broken.block);
                reply("Adding block " + block.name + " at (" + broken.x + ", " + broken.y + ")");
                player.buildQueue().addLast(new BuildRequest(broken.x, broken.y, broken.rotation, block).configure(broken.config));
            }
        }
        reply("Added rebuild to build queue");
    }

    /** Control the auto mode */
    public void auto(Context ctx) {
        if (ctx.args.size() < 2) {
            reply("[scarlet]Not enough arguments");
            reply("Usage: auto <on|off|cancel|gotocore|gotoplayer|goto|distance>");
            return;
        }
        Auto auto = griefWarnings.auto;
        switch (ctx.args.get(1).toLowerCase()) {
            case "on":
                auto.enabled = true;
                reply("enabled auto mode");
                break;
            case "off":
                auto.enabled = false;
                reply("disabled auto mode");
                break;
            case "gotocore":
                Tile core = player.getClosestCore().getTile();
                auto.gotoTile(core, 50f);
                reply("going to tile " + griefWarnings.formatTile(core));
                break;
            case "goto":
                if (ctx.args.size() < 4) {
                    reply("[scarlet]Not enough arguments");
                    reply("Usage: auto goto <x> <y>");
                    return;
                }
                int x;
                int y;
                try {
                    x = Integer.parseInt(ctx.args.get(2));
                    y = Integer.parseInt(ctx.args.get(3));
                } catch (NumberFormatException ex) {
                    reply("[scarlet]Invalid integer provided");
                    return;
                }
                Tile tile = world.tile(x, y);
                auto.gotoTile(tile, 50f);
                reply("going to tile " + griefWarnings.formatTile(tile));
                break;
            case "gotoplayer":
                if (ctx.args.size() < 3) {
                    reply("[scarlet]Not enough arguments");
                    reply("Usage: auto gotoplayer [follow|assist|undo] <player>");
                    return;
                }
                int nameStart = 2;
                boolean follow = false;
                boolean assist = false;
                boolean undo = false;
                String additional = ctx.args.get(nameStart).toLowerCase();
                switch (additional) {
                    case "follow":
                        nameStart++;
                        follow = true;
                        break;
                    case "assist":
                        nameStart++;
                        assist = true;
                        break;
                    case "undo":
                        nameStart++;
                        undo = true;
                        break;
                }
                String name = String.join(" ", ctx.args.subList(nameStart, ctx.args.size())).toLowerCase();
                Player target = getPlayer(name);
                if (target == null) {
                    reply("[scarlet]No such player");
                    return;
                }
                if (assist) auto.assistEntity(target, 100f);
                else if (undo) auto.undoEntity(target, 100f);
                else auto.gotoEntity(target, 100f, follow);
                reply("going to player: " + griefWarnings.formatPlayer(target));
                break;
            case "cancel":
                auto.cancel();
                reply("cancelled");
                break;
            case "distance":
                if (ctx.args.size() < 3) {
                    reply("[scarlet]Not enough arguments");
                    reply("Usage: auto distance <distance>");
                    return;
                }
                float distance;
                try {
                    distance = Float.parseFloat(ctx.args.get(2));
                } catch (NumberFormatException ex) {
                    reply("[scarlet]Invalid number");
                    return;
                }
                auto.targetDistance = distance;
                reply("set target distance to " + distance);
                break;
            default:
                reply("unknown subcommand");
        }
    }

    public void nextwave(Context ctx) {
        if (!player.isAdmin) {
            reply("not admin!");
            return;
        }
        int count = 1;
        if (ctx.args.size() > 1) {
            try {
                count = Integer.parseInt(ctx.args.get(1));
            } catch (NumberFormatException ex) {
                reply("invalid number");
                return;
            }
        }
        for (int i = 0; i < count; i++) Call.onAdminRequest(player, AdminAction.wave);
        reply("done");
    }
}
