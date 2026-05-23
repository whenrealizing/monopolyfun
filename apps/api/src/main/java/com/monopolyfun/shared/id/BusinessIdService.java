package com.monopolyfun.shared.id;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class BusinessIdService {
    private static final String PLATFORM_PREFIX = "MF";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private static final char[] CHECK_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final long MAX_DAILY_SEQUENCE = 999_999L;

    private final BusinessIdSequenceRepository sequenceRepository;

    public BusinessIdService(BusinessIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    public BusinessIds next(BusinessIdType type) {
        // 中文注释：业务创建入口只调用这里，内部主键和用户可见编号保持同一套类型配置。
        return new BusinessIds(internalId(type), displayNo(type));
    }

    public String internalId(BusinessIdType type) {
        requireType(type);
        return type.internalPrefix() + "-" + UUID.randomUUID();
    }

    public String displayNo(BusinessIdType type) {
        requireType(type);
        LocalDate bizDate = LocalDate.now(BUSINESS_ZONE);
        long sequence = sequenceRepository.nextValue(type, bizDate);
        if (sequence > MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("Daily business id sequence exceeded for " + type.name());
        }
        // 中文注释：展示编号固定平台、日期、业务类型和日内序号，客服检索与对账按这个稳定格式处理。
        String base = PLATFORM_PREFIX + bizDate.format(DATE_FORMATTER) + type.displayCode() + "%06d".formatted(sequence);
        return base + checkChar(base);
    }

    private void requireType(BusinessIdType type) {
        if (type == null) {
            throw new IllegalArgumentException("Business id type is required");
        }
    }

    private char checkChar(String base) {
        // 中文注释：校验位由完整编号主体计算，用户手输编号时能快速发现常见录入错误。
        int weightedSum = 0;
        for (int index = 0; index < base.length(); index++) {
            weightedSum += base.charAt(index) * (index + 1);
        }
        return CHECK_CHARS[Math.floorMod(weightedSum, CHECK_CHARS.length)];
    }
}
