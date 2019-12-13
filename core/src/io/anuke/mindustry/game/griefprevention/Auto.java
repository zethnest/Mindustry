package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.collection.Queue;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.Time;
import io.anuke.mindustry.entities.traits.BuilderTrait;
import io.anuke.mindustry.entities.traits.BuilderTrait.BuildRequest;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.entities.type.SolidEntity;
import io.anuke.mindustry.entities.type.Unit;
import io.anuke.mindustry.world.Tile;

import java.lang.reflect.Field;

import static io.anuke.mindustry.Vars.*;

/* Auto mode */
public class Auto {
    public enum Mode {
        GotoTile, GotoEntity, AssistEntity, UndoEntity
    }

    public boolean enabled = true;
    public boolean active = false;
    public Mode mode;
    public boolean persist = false;
    public float targetDistance = 0.0f;

    public Tile targetTile;
    public Unit targetEntity;
    public BuilderTrait targetBuildEntity;

    public Vector2 movement;
    public Vector2 velocity;

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
    }

    public void gotoTile(Tile tile, float distance) {
        active = true;
        mode = Mode.GotoTile;
        targetTile = tile;
        targetDistance = distance;
        persist = false;
    }

    public void gotoEntity(Unit unit, float distance, boolean follow) {
        active = true;
        mode = Mode.GotoEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = follow;
    }

    public void assistEntity(BuilderTrait unit, float distance) {
        active = true;
        mode = Mode.AssistEntity;
        targetBuildEntity = unit;
        targetDistance = distance;
        persist = true;
    }

    public void undoEntity(BuilderTrait unit, float distance) {
        active = true;
        mode = Mode.UndoEntity;
        targetBuildEntity = unit;
        targetDistance = distance;
        persist = true;
    }

    public void update() {
        if (!enabled || !active) return;
        float speed = !player.mech.flying
                ? player.mech.boostSpeed
                : player.mech.speed;

        float targetX;
        float targetY;
        switch (mode) {
            case GotoTile:
                if (targetTile == null) {
                    active = false;
                    return;
                }
                targetX = targetTile.getX();
                targetY = targetTile.getY();
                break;
            case GotoEntity:
                if (targetEntity == null) {
                    active = false;
                    return;
                }
                targetX = targetEntity.x;
                targetY = targetEntity.y;
                break;
            case AssistEntity:
            case UndoEntity:
                if (targetBuildEntity == null) {
                    active = false;
                    return;
                }
                targetX = ((Unit) targetBuildEntity).x;
                targetY = ((Unit) targetBuildEntity).y;
                break;
            default:
                throw new RuntimeException("invalid mode");
        }

        if (player.dst(targetX, targetY) < targetDistance) {
            movement.setZero();
            if (!persist) {
                player.isBoosting = false;
                cancel();
            }
        } else {
            player.isBoosting = true;
            movement.set(
                    (targetX - player.x) / Time.delta(),
                    (targetY - player.y) / Time.delta()
            ).limit(speed);
            movement.setAngle(Mathf.slerp(movement.angle(), velocity.angle(), 0.05f));
            velocity.add(movement.scl(Time.delta()));
        }

        if(velocity.len() <= 0.2f && player.mech.flying){
            player.rotation += Mathf.sin(Time.time() + player.id * 99, 10f, 1f);
        }else if(player.target == null){
            player.rotation = Mathf.slerpDelta(player.rotation, velocity.angle(), velocity.len() / 10f);
        }
        player.updateVelocityStatus();

        if (mode == Mode.AssistEntity) {
            BuildRequest targetRequest = targetBuildEntity.buildRequest();
            if (targetRequest != null) {
                Queue<BuildRequest> buildQueue = player.buildQueue();
                buildQueue.clear();
                buildQueue.addFirst(targetRequest);
                player.isBuilding = true;
            }
        } else if (mode == Mode.UndoEntity) {
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

    /** Perform necessary cleanup after stopping */
    public void cancel() {
        active = false;
        persist = false;
        targetTile = null;
        targetEntity = null;
    }
}
