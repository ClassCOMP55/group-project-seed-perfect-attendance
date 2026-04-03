import acm.graphics.GCanvas;
import acm.graphics.GOval;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A visible energy ball fired by ranged enemies and bosses.
 *
 * Travel model:
 *   - Path is a straight line based on the original aim vector.
 *   - Speed ramps up exponentially over time ("linear/expo" in debug text).
 *   - Reflection reverses the same vector, so the ball retraces a believable return path.
 */
public class Projectile extends Entity {

    private static final String FALLBACK_SPRITE =
        "assets/visuals/skeley-mob-1/normalized/skeley-mob-1-idle-front.gif";

    /** Smaller hitbox than a full entity so dodging still feels fair. */
    private static final int PROJ_HITBOX_SIZE = 16;
    private static final double PROJ_HITBOX_HALF = PROJ_HITBOX_SIZE / 2.0;

    /** Slightly smaller visual than the hitbox so collisions feel readable. */
    private static final int PROJ_VISUAL_SIZE = 14;
    private static final double PROJ_VISUAL_HALF = PROJ_VISUAL_SIZE / 2.0;

    /** Straight line path, exponential speed ramp. */
    private static final String MOTION_PROFILE_LABEL = "linear/expo";
    private static final double START_SPEED = 120.0;
    private static final double MAX_SPEED = 280.0;
    private static final double EXP_ACCEL_RATE = 3.0;
    private static final double COLLISION_STEP_PX = 6.0;
    private static final int MAX_TRAIL_POINTS = 14;

    private static final Color BALL_FILL = new Color(255, 170, 74);
    private static final Color BALL_OUTLINE = new Color(255, 236, 194);
    private static final Color REFLECT_FILL = new Color(90, 196, 255);
    private static final Color REFLECT_OUTLINE = new Color(220, 246, 255);

    /** Approximate facing for overlays and reflect logic. */
    private Direction direction;

    /** Fixed normalized travel vector. Reversed when reflected. */
    private double dirX;
    private double dirY;

    /** Shared targeting state. */
    private boolean isReflected;
    private final Entity owner;

    /** Debug-visible motion stats. */
    private final double spawnX;
    private final double spawnY;
    private double ageSeconds;
    private double currentSpeed;
    private double distanceTravelled;
    private final List<double[]> trailPoints = new ArrayList<>();

    /** Procedural ball visual so the projectile is visible without external art. */
    private final GOval ballVisual;

    public Projectile(double startX, double startY,
                      double toX, double toY,
                      TileMap tileMap, Entity owner) {
        super(startX, startY, FALLBACK_SPRITE, tileMap, 1, MAX_SPEED);

        double aimDx = toX - startX;
        double aimDy = toY - startY;
        double aimDist = Math.sqrt(aimDx * aimDx + aimDy * aimDy);
        if (aimDist < 0.001) {
            this.dirX = 0.0;
            this.dirY = 1.0;
        } else {
            this.dirX = aimDx / aimDist;
            this.dirY = aimDy / aimDist;
        }

        this.direction = Direction.fromDelta(dirX, dirY);
        this.owner = owner;
        this.isReflected = false;
        this.ageSeconds = 0.0;
        this.currentSpeed = START_SPEED;
        this.distanceTravelled = 0.0;

        double spawnOffset = 0.0;
        if (owner != null && owner.getHitbox() != null) {
            spawnOffset = Math.max(owner.getHitbox().width, owner.getHitbox().height) / 2.0
                + PROJ_HITBOX_HALF + 2.0;
        }
        this.spawnX = startX + dirX * spawnOffset;
        this.spawnY = startY + dirY * spawnOffset;
        this.x = spawnX;
        this.y = spawnY;

        this.hitbox = new Hitbox(
            x - PROJ_HITBOX_HALF,
            y - PROJ_HITBOX_HALF,
            PROJ_HITBOX_SIZE,
            PROJ_HITBOX_SIZE
        );

        this.ballVisual = new GOval(
            x - PROJ_VISUAL_HALF,
            y - PROJ_VISUAL_HALF,
            PROJ_VISUAL_SIZE,
            PROJ_VISUAL_SIZE
        );
        this.ballVisual.setFilled(true);
        applyVisualState();

        // Projectile visuals are drawn manually via GOval, not via Entity's animator/sprite.
        this.sprite = null;
        this.animator.clearFrames();
        recordTrailPoint(x, y);
    }

    @Override
    public void update(double dt) {
        if (!isAlive()) {
            return;
        }

        ageSeconds += Math.max(0.0, dt);
        currentSpeed = computeSpeed(ageSeconds);

        double dx = dirX * currentSpeed * dt;
        double dy = dirY * currentSpeed * dt;
        moveWithCollision(dx, dy);

        hitbox.updatePosition(x - PROJ_HITBOX_HALF, y - PROJ_HITBOX_HALF);
        syncBallVisual();
        recordTrailPoint(x, y);
    }

    private double computeSpeed(double age) {
        double accel = 1.0 - Math.exp(-EXP_ACCEL_RATE * Math.max(0.0, age));
        return START_SPEED + (MAX_SPEED - START_SPEED) * accel;
    }

    /** Advances in small steps so fast shots cannot tunnel through walls. */
    private void moveWithCollision(double dx, double dy) {
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= 0.0001) {
            return;
        }

        int steps = Math.max(1, (int) Math.ceil(distance / COLLISION_STEP_PX));
        double stepDx = dx / steps;
        double stepDy = dy / steps;

        for (int i = 0; i < steps; i++) {
            double nextX = x + stepDx;
            double nextY = y + stepDy;
            if (!canOccupy(nextX, nextY)) {
                takeDamage(health);
                return;
            }
            x = nextX;
            y = nextY;
            distanceTravelled += Math.sqrt(stepDx * stepDx + stepDy * stepDy);
        }

        direction = Direction.fromDelta(dirX, dirY);
    }

    private boolean canOccupy(double centerX, double centerY) {
        if (tileMap == null) {
            return true;
        }

        double left = centerX - PROJ_HITBOX_HALF + 1.0;
        double right = centerX + PROJ_HITBOX_HALF - 1.0;
        double top = centerY - PROJ_HITBOX_HALF + 1.0;
        double bottom = centerY + PROJ_HITBOX_HALF - 1.0;

        return pointPassable(left, top)
            && pointPassable(right, top)
            && pointPassable(left, bottom)
            && pointPassable(right, bottom);
    }

    private boolean pointPassable(double px, double py) {
        return tileMap.containsPixel(px, py) && tileMap.isPassable(px, py);
    }

    private void applyVisualState() {
        if (isReflected) {
            ballVisual.setFillColor(REFLECT_FILL);
            ballVisual.setColor(REFLECT_OUTLINE);
        } else {
            ballVisual.setFillColor(BALL_FILL);
            ballVisual.setColor(BALL_OUTLINE);
        }
    }

    private void syncBallVisual() {
        ballVisual.setLocation(x - PROJ_VISUAL_HALF, y - PROJ_VISUAL_HALF);
    }

    private void recordTrailPoint(double px, double py) {
        if (!trailPoints.isEmpty()) {
            double[] last = trailPoints.get(trailPoints.size() - 1);
            double ddx = px - last[0];
            double ddy = py - last[1];
            if (ddx * ddx + ddy * ddy < 4.0) {
                return;
            }
        }
        trailPoints.add(new double[]{ px, py });
        while (trailPoints.size() > MAX_TRAIL_POINTS) {
            trailPoints.remove(0);
        }
    }

    public boolean checkHit(Entity target, boolean targetIsPlayer) {
        if (target == null || !target.isAlive() || !isAlive()) {
            return false;
        }

        boolean shouldCheck = (targetIsPlayer && !isReflected)
                           || (!targetIsPlayer && isReflected);
        if (!shouldCheck) {
            return false;
        }

        if (hitbox.overlaps(target.getHitbox())) {
            target.takeDamage(1);
            takeDamage(health);
            return true;
        }
        return false;
    }

    public void reflect() {
        if (isReflected) {
            return;
        }
        dirX = -dirX;
        dirY = -dirY;
        direction = Direction.fromDelta(dirX, dirY);
        isReflected = true;
        applyVisualState();
        recordTrailPoint(x, y);
    }

    @Override
    public void draw(GCanvas canvas) {
        if (canvas == null || !isAlive()) {
            return;
        }
        syncBallVisual();
        if (ballVisual.getParent() == null) {
            canvas.add(ballVisual);
        } else {
            ballVisual.sendToFront();
        }
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (ballVisual != null) {
            ballVisual.move(panX, panY);
        }
    }

    @Override
    public void removeSpriteFromCanvas(GCanvas canvas) {
        if (canvas != null && ballVisual != null) {
            canvas.remove(ballVisual);
        }
        super.removeSpriteFromCanvas(canvas);
    }

    /** @return current travel direction, approximated to the nearest cardinal */
    public Direction getDirection() { return direction; }

    /** @return true if this projectile has been reflected by a SwordSwing */
    public boolean isReflected() { return isReflected; }

    /** @return the entity that originally fired this projectile */
    public Entity getOwner() { return owner; }

    /** @return world X component of the current velocity in px/s */
    public double getVelocityX() { return dirX * currentSpeed; }

    /** @return world Y component of the current velocity in px/s */
    public double getVelocityY() { return dirY * currentSpeed; }

    /** @return current projectile speed in px/s */
    public double getCurrentSpeed() { return currentSpeed; }

    /** @return seconds since spawn */
    public double getAgeSeconds() { return ageSeconds; }

    /** @return cumulative distance traveled in pixels */
    public double getDistanceTravelled() { return distanceTravelled; }

    /** @return spawn X used by debug overlays */
    public double getSpawnX() { return spawnX; }

    /** @return spawn Y used by debug overlays */
    public double getSpawnY() { return spawnY; }

    /** @return text label describing the motion profile */
    public String getMotionProfileLabel() { return MOTION_PROFILE_LABEL; }

    /** @return copy of recent path points for debug overlays */
    public List<double[]> getTrailPoints() { return new ArrayList<>(trailPoints); }
}
