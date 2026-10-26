package me.TreeOfSelf.pandageyserac;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

final class Cost {
    private static final long REPORT_NANOS = 60_000_000_000L;
    private static final LongAdder nanos = new LongAdder(), inputNanos = new LongAdder(), packets = new LongAdder(), inputs = new LongAdder();
    private static final AtomicLong worstInput = new AtomicLong(), lastReport = new AtomicLong(System.nanoTime());

    private Cost() {}

    static void add(long took, boolean input) {
        nanos.add(took);
        packets.increment();
        if (input) {
            inputNanos.add(took);
            inputs.increment();
            worstInput.accumulateAndGet(took, Math::max);
        }
        long now = System.nanoTime(), last = lastReport.get();
        if (now - last >= REPORT_NANOS && lastReport.compareAndSet(last, now)) report(now - last);
    }

    private static void report(long window) {
        long n = nanos.sumThenReset(), in = inputNanos.sumThenReset(), p = packets.sumThenReset(), i = inputs.sumThenReset(), worst = worstInput.getAndSet(0);
        double secs = window / 1e9;
        PandaGeyserAC.log().info(String.format("cost: %d packets, %d inputs (%.1f/s) in %.0f s, %.2f ms CPU per second, %.1f us per input (worst %.1f ms)",
                p, i, i / secs, secs, n / 1e6 / secs, i == 0 ? 0 : in / 1e3 / i, worst / 1e6));
    }
}
