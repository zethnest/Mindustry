package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.func.Cons;

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
        addCommand("verbose", this::verbose);
        addCommand("debug", this::debug);
        addCommand("spam", this::spam);
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

    public void fixPower(Context ctx) {
        boolean redundant = ctx.args.contains("redundant");
        griefWarnings.fixer.fixPower(redundant);
        reply("[green]Done");
    }

    public void verbose(Context ctx) {
        if (ctx.args.size() < 2) {
            reply("[scarlet]Not enough arguments");
            reply("Usage: verbose <on|off>");
            return;
        }
        switch (ctx.args.get(1).toLowerCase()) {
            case "on":
                griefWarnings.verbose = true;
                reply("Enabled verbose logging");
                break;
            case "off":
                griefWarnings.verbose = false;
                reply("Disabled verbose logging");
                break;
            default:
                reply("[scarlet]Not enough arguments");
                reply("Usage: verbose <on|off>");
        }
    }

    public void debug(Context ctx) {
        if (ctx.args.size() < 2) {
            reply("[scarlet]Not enough arguments");
            reply("Usage: debug <on|off>");
            return;
        }
        switch (ctx.args.get(1).toLowerCase()) {
            case "on":
                griefWarnings.debug = true;
                reply("Enabled debug logging");
                break;
            case "off":
                griefWarnings.debug = false;
                reply("Disabled debug logging");
                break;
            default:
                reply("[scarlet]Not enough arguments");
                reply("Usage: debug <on|off>");
        }
    }

    public void spam(Context ctx) {
        if (ctx.args.size() < 2) {
            reply("[scarlet]Not enough arguments");
            reply("Usage: spam <on|off>");
            return;
        }
        switch (ctx.args.get(1).toLowerCase()) {
            case "on":
                griefWarnings.verbose = true;
                griefWarnings.debug = true;
                reply("Enabled verbose and debug logging");
                break;
            case "off":
                griefWarnings.verbose = false;
                griefWarnings.debug = false;
                reply("Disabled verbose and debug logging");
                break;
            default:
                reply("[scarlet]Not enough arguments");
                reply("Usage: spam <on|off>");
        }
    }
}
