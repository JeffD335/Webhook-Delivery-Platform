package dev.webhook.platform.delivery.application;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryWorkerPoolTest {

    private ExecutorService executorService;

    @AfterEach
    void shutdownExecutor() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void processBatchOnce_usesDistinctWorkerIdsAndSummarizesResults() {
        DeliveryWorkerFactory factory = mock(DeliveryWorkerFactory.class);
        executorService = Executors.newFixedThreadPool(3);
        DeliveryWorker worker1 = mock(DeliveryWorker.class);
        DeliveryWorker worker2 = mock(DeliveryWorker.class);
        DeliveryWorker worker3 = mock(DeliveryWorker.class);
        UUID succeededDeliveryId = UUID.randomUUID();
        UUID failedDeliveryId = UUID.randomUUID();

        ProcessResult succeeded = ProcessResult.succeeded(succeededDeliveryId);
        ProcessResult noWork = ProcessResult.noPendingDelivery();
        ProcessResult failed = ProcessResult.failed(failedDeliveryId);

        when(factory.create("worker-1")).thenReturn(worker1);
        when(factory.create("worker-2")).thenReturn(worker2);
        when(factory.create("worker-3")).thenReturn(worker3);
        when(worker1.processOne()).thenReturn(succeeded);
        when(worker2.processOne()).thenReturn(noWork);
        when(worker3.processOne()).thenReturn(failed);

        DeliveryWorkerPoolResult result = new DeliveryWorkerPool(factory, executorService, 3).processBatchOnce();

        assertThat(result.workerCount()).isEqualTo(3);
        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.noWorkCount()).isEqualTo(1);
        assertThat(result.succeededCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.results()).containsExactly(succeeded, noWork, failed);

        verify(factory).create("worker-1");
        verify(factory).create("worker-2");
        verify(factory).create("worker-3");
        verify(worker1).processOne();
        verify(worker2).processOne();
        verify(worker3).processOne();
    }

    @Test
    void processBatchOnce_runsWorkersConcurrently() {
        DeliveryWorkerFactory factory = mock(DeliveryWorkerFactory.class);
        executorService = Executors.newFixedThreadPool(3);
        DeliveryWorker worker1 = mock(DeliveryWorker.class);
        DeliveryWorker worker2 = mock(DeliveryWorker.class);
        DeliveryWorker worker3 = mock(DeliveryWorker.class);
        CountDownLatch allWorkersStarted = new CountDownLatch(3);

        when(factory.create("worker-1")).thenReturn(worker1);
        when(factory.create("worker-2")).thenReturn(worker2);
        when(factory.create("worker-3")).thenReturn(worker3);
        when(worker1.processOne()).thenAnswer(invocation -> waitForOtherWorkers(allWorkersStarted));
        when(worker2.processOne()).thenAnswer(invocation -> waitForOtherWorkers(allWorkersStarted));
        when(worker3.processOne()).thenAnswer(invocation -> waitForOtherWorkers(allWorkersStarted));

        DeliveryWorkerPoolResult result = new DeliveryWorkerPool(factory, executorService, 3).processBatchOnce();

        assertThat(result.workerCount()).isEqualTo(3);
        assertThat(result.succeededCount()).isEqualTo(3);
    }

    private ProcessResult waitForOtherWorkers(CountDownLatch allWorkersStarted) throws InterruptedException {
        allWorkersStarted.countDown();
        assertThat(allWorkersStarted.await(1, TimeUnit.SECONDS)).isTrue();
        return ProcessResult.succeeded(UUID.randomUUID());
    }
}
