package me.TreeOfSelf.bedrocksim;

public record Input(float moveX, float moveZ, float yaw, float pitch, boolean jumping, boolean sprintHeld, boolean startSprinting, boolean stopSprinting, boolean sneaking, boolean startSwimming, boolean stopSwimming, boolean startGliding, boolean stopGliding, boolean ascend, boolean descend) {
    public Input(float moveX, float moveZ, float yaw, float pitch, boolean jumping, boolean sprintHeld, boolean startSprinting, boolean stopSprinting, boolean sneaking, boolean startSwimming, boolean stopSwimming, boolean startGliding, boolean stopGliding) {
        this(moveX, moveZ, yaw, pitch, jumping, sprintHeld, startSprinting, stopSprinting, sneaking, startSwimming, stopSwimming, startGliding, stopGliding, false, false);
    }

    public Input(float moveX, float moveZ, float yaw, float pitch, boolean jumping, boolean sprintHeld, boolean startSprinting, boolean stopSprinting, boolean sneaking) {
        this(moveX, moveZ, yaw, pitch, jumping, sprintHeld, startSprinting, stopSprinting, sneaking, false, false, false, false);
    }
}
