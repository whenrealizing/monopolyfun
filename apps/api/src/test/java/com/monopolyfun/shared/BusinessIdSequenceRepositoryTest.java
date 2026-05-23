package com.monopolyfun;

import com.monopolyfun.shared.id.BusinessIdSequenceRepository;
import com.monopolyfun.shared.id.BusinessIdType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class BusinessIdSequenceRepositoryTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private BusinessIdSequenceRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("truncate table business_id_sequences");
    }

    @Test
    void issuesUniqueSequentialValuesUnderConcurrentAccess() throws Exception {
        LocalDate bizDate = LocalDate.of(2026, 5, 5);
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            tasks.add(() -> repository.nextValue(BusinessIdType.ORDER, bizDate));
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Long> issued = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertEquals(Long.valueOf(1), issued.getFirst());
            assertEquals(Long.valueOf(20), issued.getLast());
            assertEquals(20, issued.stream().distinct().count());
            assertEquals(20L, jdbcTemplate.queryForObject(
                    "select next_value from business_id_sequences where id_type = 'ORDER' and biz_date = date '2026-05-05'",
                    Long.class));
        }
    }
}
