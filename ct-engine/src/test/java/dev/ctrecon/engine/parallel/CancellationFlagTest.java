package dev.ctrecon.engine.parallel;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationFlagTest {

    @Test
    void throwsOnlyAfterCancellationWasRequested() {
        CancellationFlag flag = new CancellationFlag();

        assertThatCode(flag::throwIfCancellationRequested).doesNotThrowAnyException();
        flag.requestCancellation();

        assertThat(flag.isCancellationRequested()).isTrue();
        assertThatThrownBy(flag::throwIfCancellationRequested).isInstanceOf(OperationCancelledException.class);
    }

    @Test
    void requestFromAnotherThreadStopsABusyLoop() throws InterruptedException {
        CancellationFlag flag = new CancellationFlag();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        Thread worker = Thread.ofPlatform().start(() -> {
            started.countDown();
            while (!flag.isCancellationRequested()) {
                Thread.onSpinWait();
            }
            stopped.countDown();
        });

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        flag.requestCancellation();

        assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
        worker.join();
    }

    @Test
    void noneIsNeverCancelled() {
        assertThat(CancellationToken.NONE.isCancellationRequested()).isFalse();
    }
}
