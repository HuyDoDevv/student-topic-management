package vn.edu.topicregistration.concurrency;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

public final class ConcurrentTestSupport {
    private static final long TIMEOUT_SECONDS = 30;

    private ConcurrentTestSupport() {
    }

    public static <T> List<T> runInTransactions(
            int workerCount, DataSource dataSource, ConnectionTask<T> task) throws Exception {
        if (workerCount < 2) {
            throw new IllegalArgumentException("Concurrency tests need at least two workers");
        }
        CyclicBarrier startBarrier = new CyclicBarrier(workerCount);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<T>> futures = new ArrayList<>(workerCount);
        try {
            for (int worker = 0; worker < workerCount; worker++) {
                int workerIndex = worker;
                futures.add(executor.submit(() -> executeTransaction(
                        dataSource, startBarrier, workerIndex, task)));
            }
            List<T> results = new ArrayList<>(workerCount);
            for (Future<T> future : futures) {
                results.add(get(future));
            }
            return List.copyOf(results);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static <T> T executeTransaction(DataSource dataSource, CyclicBarrier barrier,
            int workerIndex, ConnectionTask<T> task) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                await(barrier);
                T result = task.execute(workerIndex, connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void await(CyclicBarrier barrier) throws Exception {
        try {
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (BrokenBarrierException exception) {
            throw new IllegalStateException("A concurrent test worker failed before the barrier", exception);
        }
    }

    private static <T> T get(Future<T> future) throws Exception {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }

    @FunctionalInterface
    public interface ConnectionTask<T> {
        T execute(int workerIndex, Connection connection) throws Exception;
    }
}
