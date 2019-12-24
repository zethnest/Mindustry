package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.collection.Queue;
import io.anuke.arc.math.Angles;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.Interval;
import io.anuke.arc.util.Time;
import io.anuke.mindustry.content.Blocks;
import io.anuke.mindustry.entities.traits.BuilderTrait;
import io.anuke.mindustry.entities.traits.BuilderTrait.BuildRequest;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.entities.type.SolidEntity;
import io.anuke.mindustry.entities.type.TileEntity;
import io.anuke.mindustry.entities.type.Unit;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.ItemType;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.sandbox.ItemSource.ItemSourceEntity;
import io.anuke.mindustry.world.modules.ItemModule;

import java.lang.reflect.Field;

import static io.anuke.mindustry.Vars.*;

/* Auto mode */
public class Auto {
    public enum Mode {
        GotoTile, GotoEntity, AssistEntity, UndoEntity
    }

    public boolean enabled = true;
    public boolean movementActive = false;
    public Mode mode;
    public boolean persist = false;
    public float targetDistance = 0.0f;

    public Tile targetTile;
    public Unit targetEntity;
    public Tile targetItemSource;
    public Tile autoDumpTarget;

    public float targetEntityLastRotation;

    public Interval timer = new Interval(1);
    public static final int votekickWaitTimer = 0;

    public Vector2 movement;
    public Vector2 velocity;

    public boolean movementControlled = false;
    public boolean shootControlled = false;

    public boolean wasAutoShooting = false;

    public Field itemSourceEntityOutputItemField;

    public Auto() {
        try {
            Class<Player> playerClass = Player.class;
            Field playerMovementField = playerClass.getDeclaredField("movement");
            playerMovementField.setAccessible(true);
            movement = (Vector2) playerMovementField.get(player);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException("reflective access failed on Player.movement");
        }
        try {
            Class<SolidEntity> solidEntityClass = SolidEntity.class;
            Field playerVelocityField = solidEntityClass.getDeclaredField("velocity");
            playerVelocityField.setAccessible(true);
            velocity = (Vector2) playerVelocityField.get(player);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException("reflective access failed on SolidEntity.velocity");
        }

        try {
            Class<ItemSourceEntity> itemSourceEntityClass = ItemSourceEntity.class;
            itemSourceEntityOutputItemField = itemSourceEntityClass.getDeclaredField("outputItem");
            itemSourceEntityOutputItemField.setAccessible(true);
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("reflective access failed on ItemSourceEntity.outputItem");
        }
    }

    public void gotoTile(Tile tile, float distance) {
        movementActive = true;
        mode = Mode.GotoTile;
        targetTile = tile;
        targetDistance = distance;
        persist = false;
    }

    public void gotoEntity(Unit unit, float distance, boolean follow) {
        movementActive = true;
        mode = Mode.GotoEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = follow;
    }

    public void assistEntity(Unit unit, float distance) {
        movementActive = true;
        mode = Mode.AssistEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = true;
    }

    public void undoEntity(Unit unit, float distance) {
        movementActive = true;
        mode = Mode.UndoEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = true;
    }

    public boolean manageItemSource(Tile tile) {
        if (tile == null) {
            targetItemSource = null;
            return true;
        }
        if (tile.block() != Blocks.itemSource) return false;
        targetItemSource = tile;
        return true;
    }

    public boolean setAutoDumpTransferTarget(Tile tile) {
        if (tile == null) {
            autoDumpTarget = null;
            return true;
        }
        if (!tile.block().hasItems || !tile.interactable(player.getTeam())) return false;
        autoDumpTarget = tile;
        return true;
    }

    public void update() {
        if (!enabled) return;

        updateItemSourceTracking();
        updateAutoDump();
        updateMovement();
    }

    public void updateAutoDump() {
        Tile tile = autoDumpTarget;
        if (tile == null || !tile.block().hasItems || !tile.interactable(player.getTeam())) {
            // tile doesn't accept items, reset the thing
            autoDumpTarget = null;
            return;
        }
        ItemStack stack = player.item();
        // add 10 to timer to not spam
        if (player.timer == null || !player.timer.check(Player.timerTransfer, 50)) return;
        if (stack.amount > 0 &&
                tile.block().acceptStack(stack.item, stack.amount, tile, player) > 0 &&
                !player.isTransferring) {
            Call.transferInventory(player, tile);
        }
    }

    public void updateItemSourceTracking() {
        if (targetItemSource == null) return;
        if (targetItemSource.block() != Blocks.itemSource) {
            targetItemSource = null;
            return;
        }
        TileEntity core = player.getClosestCore();
        if (core == null) return;
        ItemModule items = core.items;

        Item least = null;
        int count = Integer.MAX_VALUE;
        for (int i = 0; i < content.items().size; i++) {
            Item currentItem = content.item(i);
            if (currentItem.type != ItemType.material) continue;
            int currentCount = items.get(currentItem);
            if (currentCount < count) {
                least = currentItem;
                count = currentCount;
            }
        }
        ItemSourceEntity entity = targetItemSource.ent();
        Item currentConfigured;
        try {
            currentConfigured = (Item)itemSourceEntityOutputItemField.get(entity);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("reflective access failed on ItemSourceEntity.outputItem");
        }
        if (least != null && least != currentConfigured) targetItemSource.configure(least.id);
    }

    public void updateMovement() {
        if (!movementActive) return;
        float speed = !player.mech.flying
                ? player.mech.boostSpeed
                : player.mech.speed;

        float targetX;
        float targetY;
        switch (mode) {
            case GotoTile:
                if (targetTile == null) {
                    movementActive = false;
                    return;
                }
                targetX = targetTile.getX();
                targetY = targetTile.getY();
                break;
            case GotoEntity:
            case AssistEntity:
            case UndoEntity:
                if (targetEntity == null) {
                    movementActive = false;
                    return;
                }
                targetX = targetEntity.x;
                targetY = targetEntity.y;
                break;
            default:
                throw new RuntimeException("invalid mode");
        }

        movementControlled = false;
        if (player.dst(targetX, targetY) < targetDistance) {
            movement.setZero();
            if (!persist) {
                player.isBoosting = false;
                cancelMovement();
            }
        } else {
            player.isBoosting = true;
            movement.set(
                    (targetX - player.x) / Time.delta(),
                    (targetY - player.y) / Time.delta()
            ).limit(speed);
            movement.setAngle(Mathf.slerp(movement.angle(), velocity.angle(), 0.05f));
            velocity.add(movement.scl(Time.delta()));
            movementControlled = true;
        }

        shootControlled = false;
        if (mode == Mode.AssistEntity) {
            boolean shouldContinue = true;
            if (targetEntity instanceof Player && shouldContinue) {
                Player targetPlayer = (Player)targetEntity;
                // crappy is shooting logic
                if (!targetPlayer.getTimer().check(targetPlayer.getShootTimer(false), targetPlayer.getWeapon().reload * 1.25f)) {
                    player.buildQueue().clear();
                    player.isBuilding = false;
                    player.isShooting = true;
                    wasAutoShooting = true;
                    shootControlled = true;

                    player.rotation = Mathf.slerpDelta(player.rotation, targetEntityLastRotation, 0.1f * player.mech.getRotationAlpha(player));
                    float rotationDeg = targetEntityLastRotation * Mathf.degreesToRadians;
                    player.pointerX = player.getX() + 200 * Mathf.cos(rotationDeg);
                    player.pointerY = player.getY() + 200 * Mathf.sin(rotationDeg);
                    shouldContinue = false;
                } else if (wasAutoShooting) {
                    player.isShooting = false;
                    wasAutoShooting = false;
                }
            }
            if (targetEntity instanceof BuilderTrait && shouldContinue) {
                BuilderTrait targetBuildEntity = (BuilderTrait)targetEntity;
                BuildRequest targetRequest = targetBuildEntity.buildRequest();
                if (targetRequest != null) {
                    Queue<BuildRequest> buildQueue = player.buildQueue();
                    buildQueue.clear();
                    buildQueue.addFirst(targetRequest);
                    player.isBuilding = true;
                    player.isShooting = false;
                    shouldContinue = false;
                }
            }
        } else if (mode == Mode.UndoEntity) {
            if (targetEntity instanceof BuilderTrait) {
                BuilderTrait targetBuildEntity = (BuilderTrait) targetEntity;
                // TODO: handle configures
                BuildRequest targetRequest = targetBuildEntity.buildRequest();
                if (targetRequest != null) {
                    BuildRequest undo;
                    if (targetRequest.breaking) {
                        Tile target = world.tile(targetRequest.x, targetRequest.y);
                        undo = new BuildRequest(targetRequest.x, targetRequest.y, target.rotation(), target.block());
                    } else undo = new BuildRequest(targetRequest.x, targetRequest.y);
                    player.buildQueue().addLast(undo);
                    player.isBuilding = true;
                }
            }
        }

        if(velocity.len() <= 0.2f && player.mech.flying){
            player.rotation += Mathf.sin(Time.time() + player.id * 99, 10f, 1f);
        }else if(player.target == null){
            player.rotation = Mathf.slerpDelta(player.rotation, velocity.angle(), velocity.len() / 10f);
        }
        player.updateVelocityStatus();
    }

    /** Perform necessary cleanup after stopping */
    public void cancelMovement() {
        movementActive = false;
        persist = false;
        targetTile = null;
        targetEntity = null;
        movementControlled = false;
        shootControlled = false;
        wasAutoShooting = false;
    }

    public void handlePlayerShoot(Player target, float offsetX, float offsetY, float rotation) {
        if (target == targetEntity) targetEntityLastRotation = rotation;
    }

    public boolean interceptMessage(String message, String sender, Player playersender) {
        // message is annoying
        if (message.startsWith("[scarlet]You must wait ") && sender == null) {
            return !timer.get(votekickWaitTimer, 90);
        }
        return false;
    }

    public void votekick(String identifier) {
        Player p = griefWarnings.commandHandler.getPlayer(identifier);
        if (p == null) return;
        Call.sendChatMessage("/votekick " + p.name);
    }
}
