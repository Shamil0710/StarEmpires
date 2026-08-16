package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.ProjectileShape;

import java.util.Arrays;
import java.util.Objects;

/**
 * Dense deterministic Stage-17.5E storage for large populations of simple kinetic bodies.
 *
 * <p>The pool deliberately uses structure-of-arrays storage rather than one Ashley entity or one
 * permanently allocated {@link ProjectileBody} object per round. Individual projectile identity and
 * physical state remain authoritative; immutable {@code ProjectileBody} objects are materialized only
 * when a caller explicitly requests one.</p>
 *
 * <p>Projectile IDs must be appended in strictly increasing order. This gives the pool one stable
 * deterministic iteration order without a secondary sorting pass or hidden player/AI distinction.</p>
 */
public final class KineticProjectilePool {
    private static final int DEFAULT_CAPACITY = 256;

    private long[] projectileIds;
    private long[] sourceEntityIds;
    private long[] spawnTicks;
    private String[] materialIds;
    private ProjectileShape[] shapes;
    private double[] lengthsM;
    private double[] diametersM;
    private double[] massesKg;
    private double[] xM;
    private double[] yM;
    private double[] velocityXMps;
    private double[] velocityYMps;
    private int size;

    /** Creates an empty pool with the default initial capacity. */
    public KineticProjectilePool() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates an empty pool with an explicit initial capacity.
     *
     * @param initialCapacity positive number of projectile slots to allocate initially
     */
    public KineticProjectilePool(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive");
        }
        projectileIds = new long[initialCapacity];
        sourceEntityIds = new long[initialCapacity];
        spawnTicks = new long[initialCapacity];
        materialIds = new String[initialCapacity];
        shapes = new ProjectileShape[initialCapacity];
        lengthsM = new double[initialCapacity];
        diametersM = new double[initialCapacity];
        massesKg = new double[initialCapacity];
        xM = new double[initialCapacity];
        yM = new double[initialCapacity];
        velocityXMps = new double[initialCapacity];
        velocityYMps = new double[initialCapacity];
    }

    /** @return number of authoritative kinetic bodies currently stored */
    public int size() {
        return size;
    }

    /** @return current allocated dense storage capacity */
    public int capacity() {
        return projectileIds.length;
    }

    /**
     * Appends one authoritative body to the dense store.
     *
     * @param body immutable body to copy into structure-of-arrays storage
     */
    public void add(ProjectileBody body) {
        ProjectileBody checked = Objects.requireNonNull(body, "body");
        if (size > 0 && checked.projectileId() <= projectileIds[size - 1]) {
            throw new IllegalArgumentException("projectile IDs must be appended in strictly increasing order");
        }
        ensureCapacity(size + 1);
        projectileIds[size] = checked.projectileId();
        sourceEntityIds[size] = checked.sourceEntityId();
        spawnTicks[size] = checked.spawnTick();
        materialIds[size] = checked.materialId();
        shapes[size] = checked.shape();
        lengthsM[size] = checked.lengthM();
        diametersM[size] = checked.diameterM();
        massesKg[size] = checked.massKg();
        xM[size] = checked.xM();
        yM[size] = checked.yM();
        velocityXMps[size] = checked.velocityXMps();
        velocityYMps[size] = checked.velocityYMps();
        size++;
    }

    /**
     * Materializes one immutable projectile view on demand.
     *
     * @param index deterministic dense-order index
     * @return immutable authoritative body snapshot
     */
    public ProjectileBody bodyAt(int index) {
        checkIndex(index);
        return new ProjectileBody(
                projectileIds[index],
                sourceEntityIds[index],
                spawnTicks[index],
                materialIds[index],
                shapes[index],
                lengthsM[index],
                diametersM[index],
                massesKg[index],
                xM[index],
                yM[index],
                velocityXMps[index],
                velocityYMps[index]);
    }

    /**
     * Finds one body by its stable ID using the pool's sorted deterministic identity order.
     *
     * @param projectileId stable projectile identity
     * @return dense index, or {@code -1} if not present
     */
    public int indexOf(long projectileId) {
        if (projectileId <= 0L || size == 0) {
            return -1;
        }
        int index = Arrays.binarySearch(projectileIds, 0, size, projectileId);
        return index >= 0 ? index : -1;
    }

    /**
     * Removes one body while preserving deterministic ascending-ID iteration order.
     *
     * @param projectileId stable projectile identity
     * @return {@code true} when a stored body was removed
     */
    public boolean remove(long projectileId) {
        int index = indexOf(projectileId);
        if (index < 0) {
            return false;
        }
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(projectileIds, index + 1, projectileIds, index, moved);
            System.arraycopy(sourceEntityIds, index + 1, sourceEntityIds, index, moved);
            System.arraycopy(spawnTicks, index + 1, spawnTicks, index, moved);
            System.arraycopy(materialIds, index + 1, materialIds, index, moved);
            System.arraycopy(shapes, index + 1, shapes, index, moved);
            System.arraycopy(lengthsM, index + 1, lengthsM, index, moved);
            System.arraycopy(diametersM, index + 1, diametersM, index, moved);
            System.arraycopy(massesKg, index + 1, massesKg, index, moved);
            System.arraycopy(xM, index + 1, xM, index, moved);
            System.arraycopy(yM, index + 1, yM, index, moved);
            System.arraycopy(velocityXMps, index + 1, velocityXMps, index, moved);
            System.arraycopy(velocityYMps, index + 1, velocityYMps, index, moved);
        }
        size--;
        materialIds[size] = null;
        shapes[size] = null;
        return true;
    }

    /**
     * Advances every stored projectile ballistically in stable ascending-ID order.
     *
     * <p>No camera/render state is accepted by this method, so render visibility cannot affect the
     * authoritative trajectory.</p>
     *
     * @param deltaSeconds positive deterministic simulation interval
     */
    public void advanceAll(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0d) {
            throw new IllegalArgumentException("deltaSeconds must be finite and positive");
        }
        for (int index = 0; index < size; index++) {
            xM[index] += velocityXMps[index] * deltaSeconds;
            yM[index] += velocityYMps[index] * deltaSeconds;
        }
    }

    private void ensureCapacity(int required) {
        if (required <= projectileIds.length) {
            return;
        }
        int next = projectileIds.length;
        while (next < required) {
            int grown = next + Math.max(1, next >>> 1);
            if (grown < next) {
                throw new IllegalStateException("projectile pool capacity overflow");
            }
            next = grown;
        }
        projectileIds = Arrays.copyOf(projectileIds, next);
        sourceEntityIds = Arrays.copyOf(sourceEntityIds, next);
        spawnTicks = Arrays.copyOf(spawnTicks, next);
        materialIds = Arrays.copyOf(materialIds, next);
        shapes = Arrays.copyOf(shapes, next);
        lengthsM = Arrays.copyOf(lengthsM, next);
        diametersM = Arrays.copyOf(diametersM, next);
        massesKg = Arrays.copyOf(massesKg, next);
        xM = Arrays.copyOf(xM, next);
        yM = Arrays.copyOf(yM, next);
        velocityXMps = Arrays.copyOf(velocityXMps, next);
        velocityYMps = Arrays.copyOf(velocityYMps, next);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("projectile index " + index + " outside [0," + size + ")");
        }
    }
}
