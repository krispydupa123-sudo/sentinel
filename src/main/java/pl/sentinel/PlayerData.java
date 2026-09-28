package pl.sentinel;

import java.util.*;

final class PlayerData {
    long joinTime = System.currentTimeMillis();
    long lastTeleport, lastVelocity, lastSpecial, lastIce, lastHit;
    int airTicks;
    double airRise, speedBuf, noFallBuf, scaffoldBuf, multiBuf;
    UUID lastTarget;
    boolean punished;

    final Map<String, Double> vl = new HashMap<>();
    final Map<String, Long> lastAlert = new HashMap<>();
    final ArrayDeque<Long> clicks = new ArrayDeque<>();
    final ArrayDeque<Long> breaks = new ArrayDeque<>();
    final ArrayDeque<Long> places = new ArrayDeque<>();
    final ArrayDeque<Long> hits = new ArrayDeque<>();

    static int prune(ArrayDeque<Long> q, long now, long window) {
        while (!q.isEmpty() && now - q.peekFirst() > window) q.pollFirst();
        return q.size();
    }
}
