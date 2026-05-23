package com.monopolyfun.shared.id;

import java.time.LocalDate;

public interface BusinessIdSequenceRepository {
    long nextValue(BusinessIdType type, LocalDate bizDate);
}
