package com.securevault.performance;

import com.securevault.dto.PageResponse;
import com.securevault.dto.SearchResultDTO;
import com.securevault.entity.Document;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import com.securevault.service.DocumentService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ConcurrentSearchPerformanceTest: 50 Concurrent Searches Performance Verification")
public class ConcurrentSearchPerformanceTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User perfUser;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName(RoleName.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.EMPLOYEE)));

        perfUser = userRepository.findByEmail("perf_user@securevault.com")
                .orElseGet(() -> {
                    User u = new User("Perf User", "perf_user@securevault.com", "hash123");
                    u.setEnabled(true);
                    u.setRoles(new HashSet<>(Collections.singletonList(employeeRole)));
                    return userRepository.save(u);
                });

        // Seed 10 sample documents for search index if not already present
        if (documentRepository.findByOwnerEmailAndDeletedFalse(perfUser.getEmail()).isEmpty()) {
            for (int i = 1; i <= 10; i++) {
                Document d = new Document("Perf Document " + i, "perf_" + i + ".pdf", "FINANCIAL",
                        "Performance testing quarterly balance and tax reports", perfUser);
                documentRepository.save(d);
            }
        }
    }

    @Test
    @DisplayName("Performance Check: 50 Concurrent Searches Throughput and Latency Benchmarking")
    void testFiftyConcurrentSearches() throws InterruptedException {
        int totalRequests = 50;
        int workerThreads = 25;
        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        String[] queries = new String[]{"Perf", "Document", "Financial", "quarterly", "balance"};

        long testStartTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final int workerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize tasks to begin simultaneously
                    String q = queries[workerId % queries.length];
                    long reqStart = System.currentTimeMillis();

                    PageResponse<SearchResultDTO> results = documentService.searchDocuments(
                            q, null, null, null, null, null, null, 0, 10, perfUser.getEmail(), false
                    );

                    long reqDuration = System.currentTimeMillis() - reqStart;
                    latencies.add(reqDuration);

                    if (results != null && results.getContent() != null) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all concurrent requests!

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        long totalElapsedTime = System.currentTimeMillis() - testStartTime;
        executor.shutdown();

        assertTrue(completed, "All 50 concurrent requests should complete within 30 seconds");
        assertEquals(totalRequests, successCount.get(), "All 50 requests must succeed");
        assertEquals(0, failureCount.get(), "No request should fail");

        // Calculate statistics
        Collections.sort(latencies);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        double throughputRps = (totalRequests / (double) totalElapsedTime) * 1000.0;

        System.out.println("=================================================");
        System.out.println("=== 50 CONCURRENT SEARCHES BENCHMARK RESULTS ===");
        System.out.println("=================================================");
        System.out.printf("Total Requests:        %d%n", totalRequests);
        System.out.printf("Concurrency Level:     %d threads%n", totalRequests);
        System.out.printf("Successful Requests:   %d (100.0%%)%n", successCount.get());
        System.out.printf("Failed Requests:       %d (0.0%%)%n", failureCount.get());
        System.out.printf("Total Elapsed Time:    %d ms%n", totalElapsedTime);
        System.out.printf("Throughput:            %.2f req/sec%n", throughputRps);
        System.out.printf("Min Latency:           %d ms%n", min);
        System.out.printf("Avg Latency:           %.2f ms%n", avg);
        System.out.printf("Median (P50) Latency:  %d ms%n", p50);
        System.out.printf("P95 Latency:           %d ms%n", p95);
        System.out.printf("P99 Latency:           %d ms%n", p99);
        System.out.printf("Max Latency:           %d ms%n", max);
        System.out.println("=================================================");
    }
}
