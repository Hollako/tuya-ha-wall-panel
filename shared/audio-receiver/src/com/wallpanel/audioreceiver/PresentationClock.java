package com.wallpanel.audioreceiver;

/** Frame/time model; independent of Android so wrap, reset and stale cases can be tested. */
final class PresentationClock {
  static final int RATE = 48000;
  long head, timestampFrame, timestampNano;
  double rate = RATE;
  private long previousHead = -1, timestampBaseFrame = -1, timestampBaseNano;
  private boolean stable;
  boolean discontinuity;

  long head(int value) {
    long raw = Integer.toUnsignedLong(value);
    if (previousHead >= 0) {
      long delta = (raw - previousHead) & 0xffffffffL;
      if (delta > 0x80000000L) { stable = false; discontinuity = true; return head; }
      head += delta;
    } else head = raw;
    previousHead = raw;
    return head;
  }
  void invalidate() { stable = false; timestampBaseFrame = -1; timestampNano = 0; }
  boolean timestamp(long rawFrame, long nanos, long now, long submitted) {
    long frame = (head & ~0xffffffffL) | (rawFrame & 0xffffffffL);
    if (frame - head > 0x80000000L) frame -= 0x100000000L;
    else if (head - frame > 0x80000000L) frame += 0x100000000L;
    if (nanos <= 0 || Math.abs(now - nanos) > 2_000_000_000L || frame < 0 || frame > submitted) {
      invalidate(); return false;
    }
    if (timestampNano > 0 && (nanos < timestampNano || frame < timestampFrame)) {
      invalidate(); return false;
    }
    if (timestampBaseFrame >= 0 && nanos - timestampBaseNano >= 1_000_000_000L) {
      double measured = (frame - timestampBaseFrame) * 1e9 / (nanos - timestampBaseNano);
      if (measured < RATE * .995 || measured > RATE * 1.005) { invalidate(); return false; }
      rate = stable ? .8 * rate + .2 * measured : measured;
      stable = true;
      timestampBaseFrame = frame; timestampBaseNano = nanos;
    } else if (timestampBaseFrame < 0) { timestampBaseFrame = frame; timestampBaseNano = nanos; }
    timestampFrame = frame; timestampNano = nanos;
    return true;
  }
  boolean valid(long now) { return !discontinuity && stable && timestampNano > 0 && now - timestampNano < 12_000_000_000L; }
  long played(long now, long submitted) {
    double estimate = valid(now) ? timestampFrame + (now - timestampNano) * rate / 1e9 : head;
    return Math.max(0, Math.min(submitted, (long) estimate));
  }
}
