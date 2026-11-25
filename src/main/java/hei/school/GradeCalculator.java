package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class GradeCalculator {

    private static final BigDecimal MAX_GRADE = new BigDecimal("20");
    private static final BigDecimal DAILY_PENALTY = new BigDecimal("0.10");
    private static final BigDecimal MAX_PENALTY = new BigDecimal("0.50");
    private static final int GRADE_SCALE = 1;

    private GradeCalculator() {
    }

    public static long daysLate(LocalDate deadline, LocalDateTime submittedAt) {
        return Math.max(0, ChronoUnit.DAYS.between(deadline, submittedAt.toLocalDate()));
    }

    public static BigDecimal penaltyRate(long daysLate) {
        return BigDecimal.valueOf(Math.max(0, daysLate))
                .multiply(DAILY_PENALTY)
                .min(MAX_PENALTY);
    }

    public static BigDecimal calculate(BigDecimal rawPoints, BigDecimal maxPoints, LocalDate deadline, LocalDateTime submittedAt) {
        BigDecimal zero = BigDecimal.ZERO.setScale(GRADE_SCALE);
        if (rawPoints == null || maxPoints == null || maxPoints.signum() <= 0) {
            return zero;
        }
        BigDecimal baseGrade = rawPoints.multiply(MAX_GRADE).divide(maxPoints, 10, RoundingMode.HALF_UP);
        BigDecimal factor = BigDecimal.ONE.subtract(penaltyRate(daysLate(deadline, submittedAt)));
        return baseGrade.multiply(factor).setScale(GRADE_SCALE, RoundingMode.HALF_UP).max(zero);
    }

    public static BigDecimal calculate(double rawPoints, double maxPoints, LocalDate deadline, LocalDateTime submittedAt) {
        return calculate(BigDecimal.valueOf(rawPoints), BigDecimal.valueOf(maxPoints), deadline, submittedAt);
    }
}
