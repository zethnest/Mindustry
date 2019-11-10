package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.func.Cons;

import static io.anuke.mindustry.Vars.*;

import java.util.HashMap;

// introducing the worst command system known to mankind
public class CommandHandler {
    public class Context {
        public String[] args;

        public Context(String[] args) {
            this.args = args;
        }
    }

    public HashMap<String, Cons<Context>> commands = new HashMap<>();

    public CommandHandler() {
        addCommand("fixpower", this::fixPower);
        addCommand("verbose", this::verbose);
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
        command.get(new Context(args));
        return true;
    }

    public void fixPower(Context ctx) {
        griefWarnings.fixer.fixPower();
        reply("[green]Done");
    }

    public void verbose(Context ctx) {
        if (ctx.args.length < 2) {
            reply("[scarlet]Not enough arguments");
            reply("Usage: verbose <on|off>");
            return;
        }
        switch (ctx.args[1].toLowerCase()) {
            case "on":
            case "true":
            case "enable":
                griefWarnings.verbose = true;
                reply("Enabled verbose logging");
                break;
            case "off":
            case "false":
            case "disable":
                griefWarnings.verbose = false;
                reply("Disabled verbose logging");
                break;
            default:
                reply("[scarlet]Not enough arguments");
                reply("Usage: verbose <on|off>");
        }
    }
}
