package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class PeriodRulesTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant LECTURER_START = Instant.parse("2026-09-10T01:00:00Z");
    private static final Instant LECTURER_END = Instant.parse("2026-09-10T03:00:00Z");
    private static final Instant STUDENT_START = Instant.parse("2026-09-10T04:00:00Z");
    private static final Instant STUDENT_END = Instant.parse("2026-09-10T06:00:00Z");
    private static final LocalDate REVIEWER_DEADLINE = LocalDate.of(2026, 9, 20);
    private static final LocalDate COUNCIL_DATE = LocalDate.of(2026, 9, 25);

    private final RulesDriver rules = new RulesDriver();

    @Test
    void fourTypesEnforceTheirRequiredAndInapplicableDates() {
        assertDoesNotThrow(() -> rules.create(config("COURSE", null, null)));
        assertDoesNotThrow(() -> rules.create(config("NCKH", null, null)));
        assertDoesNotThrow(() -> rules.create(config("TLCN", REVIEWER_DEADLINE, null)));
        assertDoesNotThrow(() -> rules.create(config("KLTN", REVIEWER_DEADLINE, COUNCIL_DATE)));

        assertThrows(IllegalArgumentException.class,
                () -> rules.create(config("COURSE", REVIEWER_DEADLINE, null)));
        assertThrows(IllegalArgumentException.class,
                () -> rules.create(config("TLCN", null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> rules.create(config("TLCN", REVIEWER_DEADLINE, COUNCIL_DATE)));
        assertThrows(IllegalArgumentException.class,
                () -> rules.create(config("KLTN", REVIEWER_DEADLINE, null)));
    }

    @Test
    void changingTypeBeforeLecturerOpeningClearsOldDatesAndRequiresNewOnes() {
        TestConfig current = config("KLTN", REVIEWER_DEADLINE, COUNCIL_DATE);
        TestConfig courseRequest = current.withTypeAndDates(
                "COURSE", REVIEWER_DEADLINE, COUNCIL_DATE);

        TestConfig normalized = rules.update(
                current, courseRequest, LECTURER_START.minusSeconds(1));

        assertEquals("COURSE", normalized.type());
        assertNull(normalized.reviewerScoreDeadline());
        assertNull(normalized.councilReportDate());

        TestConfig course = config("COURSE", null, null);
        TestConfig incompleteKltn = course.withTypeAndDates("KLTN", null, null);
        assertThrows(IllegalArgumentException.class,
                () -> rules.update(course, incompleteKltn, LECTURER_START.minusSeconds(1)));
    }

    @Test
    void scheduleRequiresGapAndRejectsAdjacentOverlapOrEmptyWindows() {
        assertDoesNotThrow(() -> rules.create(config("COURSE", null, null)
                .withSchedule(LECTURER_START, LECTURER_END,
                        LECTURER_END.plusNanos(1), STUDENT_END)));

        assertThrows(IllegalArgumentException.class,
                () -> rules.create(config("COURSE", null, null)
                        .withSchedule(LECTURER_START, LECTURER_END,
                                LECTURER_END, STUDENT_END)));

        assertThrows(IllegalArgumentException.class,
                () -> rules.create(config("COURSE", null, null)
                        .withSchedule(LECTURER_START, LECTURER_END,
                                LECTURER_END.minusNanos(1), STUDENT_END)));
        assertThrows(IllegalArgumentException.class,
                () -> rules.create(config("COURSE", null, null)
                        .withSchedule(LECTURER_START, LECTURER_START,
                                STUDENT_START, STUDENT_END)));
        assertThrows(IllegalArgumentException.class,
                () -> rules.create(config("COURSE", null, null)
                        .withSchedule(LECTURER_START, LECTURER_END,
                                STUDENT_START, STUDENT_START)));
    }

    @Test
    void stateIncludesEachStartAndExcludesEachEnd() {
        TestConfig period = config("COURSE", null, null);

        assertEquals("BEFORE_LECTURER", rules.state(period, LECTURER_START.minusNanos(1)));
        assertEquals("LECTURER_OPEN", rules.state(period, LECTURER_START));
        assertEquals("BETWEEN_WINDOWS", rules.state(period, LECTURER_END));
        assertEquals("STUDENT_OPEN", rules.state(period, STUDENT_START));
        assertEquals("REGISTRATION_CLOSED", rules.state(period, STUDENT_END));
    }

    @Test
    void storedScheduleControlsLocksAndOnlyUnexpiredEndsCanBeExtended() {
        TestConfig current = config("COURSE", null, null);
        TestConfig beforeOpening = current.withName("Tên mới")
                .withSchedule(LECTURER_START.plusSeconds(60), LECTURER_END.plusSeconds(60),
                        STUDENT_START.plusSeconds(60), STUDENT_END.plusSeconds(60));
        assertDoesNotThrow(() -> rules.update(
                current, beforeOpening, LECTURER_START.minusSeconds(1)));

        assertThrows(IllegalArgumentException.class,
                () -> rules.update(current, current.withName("Tên bị khóa"), LECTURER_START));
        assertThrows(IllegalArgumentException.class,
                () -> rules.update(current,
                        current.withSchedule(LECTURER_START.plusSeconds(60), LECTURER_END,
                                STUDENT_START, STUDENT_END),
                        LECTURER_START));

        TestConfig lecturerExtension = current.withSchedule(
                LECTURER_START, LECTURER_END.plusSeconds(30), STUDENT_START, STUDENT_END);
        assertDoesNotThrow(() -> rules.update(current, lecturerExtension, LECTURER_START));
        assertThrows(IllegalArgumentException.class,
                () -> rules.update(current,
                        current.withSchedule(LECTURER_START, LECTURER_END.minusSeconds(30),
                                STUDENT_START, STUDENT_END),
                        LECTURER_START));
        assertThrows(IllegalArgumentException.class,
                () -> rules.update(current, lecturerExtension, LECTURER_END));

        TestConfig studentExtension = current.withSchedule(
                LECTURER_START, LECTURER_END, STUDENT_START, STUDENT_END.plusSeconds(30));
        assertDoesNotThrow(() -> rules.update(current, studentExtension, STUDENT_START));
        assertThrows(IllegalArgumentException.class,
                () -> rules.update(current, studentExtension, STUDENT_END));
    }

    @Test
    void conditionalDatesCanOnlyMoveLaterBeforeTheirStoredCalendarDay() {
        TestConfig current = config("KLTN", REVIEWER_DEADLINE, COUNCIL_DATE);
        Instant dayBeforeReviewer = Instant.parse("2026-09-19T00:00:00Z");
        TestConfig extension = current.withTypeAndDates(
                "KLTN", REVIEWER_DEADLINE.plusDays(1), COUNCIL_DATE.plusDays(1));

        assertDoesNotThrow(() -> rules.update(current, extension, dayBeforeReviewer));
        assertThrows(IllegalArgumentException.class,
                () -> rules.update(current,
                        current.withTypeAndDates(
                                "KLTN", REVIEWER_DEADLINE.minusDays(1), COUNCIL_DATE),
                        dayBeforeReviewer));

        Instant reviewerDayStartsInVietnam = REVIEWER_DEADLINE
                .atStartOfDay(BUSINESS_ZONE).toInstant();
        assertThrows(IllegalArgumentException.class,
                () -> rules.update(current, extension, reviewerDayStartsInVietnam));
    }

    private static TestConfig config(
            String type, LocalDate reviewerScoreDeadline, LocalDate councilReportDate) {
        return new TestConfig(
                "Đợt HK1", type,
                LECTURER_START, LECTURER_END, STUDENT_START, STUDENT_END,
                reviewerScoreDeadline, councilReportDate);
    }

    private record TestConfig(
            String name,
            String type,
            Instant lecturerStart,
            Instant lecturerEnd,
            Instant studentStart,
            Instant studentEnd,
            LocalDate reviewerScoreDeadline,
            LocalDate councilReportDate) {

        TestConfig withName(String value) {
            return new TestConfig(
                    value, type, lecturerStart, lecturerEnd, studentStart, studentEnd,
                    reviewerScoreDeadline, councilReportDate);
        }

        TestConfig withTypeAndDates(
                String value, LocalDate reviewerDeadline, LocalDate councilDate) {
            return new TestConfig(
                    name, value, lecturerStart, lecturerEnd, studentStart, studentEnd,
                    reviewerDeadline, councilDate);
        }

        TestConfig withSchedule(
                Instant lecturerFrom, Instant lecturerTo,
                Instant studentFrom, Instant studentTo) {
            return new TestConfig(
                    name, type, lecturerFrom, lecturerTo, studentFrom, studentTo,
                    reviewerScoreDeadline, councilReportDate);
        }
    }

    /**
     * Keeps the red test executable before T022 introduces the production domain API.
     * A missing or incompatible API fails as an assertion instead of a compiler error.
     */
    private static final class RulesDriver {
        private static final String RULES_CLASS =
                "vn.edu.topicregistration.periods.domain.PeriodRules";

        TestConfig create(TestConfig request) {
            Object result = invoke(
                    "validateForCreate",
                    new Class<?>[] { configurationClass() },
                    productionConfig(request));
            return testConfig(result);
        }

        TestConfig update(TestConfig current, TestConfig request, Instant now) {
            Object result = invoke(
                    "validateAndNormalizeUpdate",
                    new Class<?>[] {
                            configurationClass(), configurationClass(), Instant.class, ZoneId.class
                    },
                    productionConfig(current), productionConfig(request), now, BUSINESS_ZONE);
            return testConfig(result);
        }

        String state(TestConfig period, Instant now) {
            return ((Enum<?>) invoke(
                    "stateAt",
                    new Class<?>[] { configurationClass(), Instant.class },
                    productionConfig(period), now)).name();
        }

        private Object productionConfig(TestConfig value) {
            try {
                Constructor<?> constructor = configurationClass().getDeclaredConstructor(
                        String.class, topicTypeClass(),
                        Instant.class, Instant.class, Instant.class, Instant.class,
                        LocalDate.class, LocalDate.class);
                return constructor.newInstance(
                        value.name(), enumValue(topicTypeClass(), value.type()),
                        value.lecturerStart(), value.lecturerEnd(),
                        value.studentStart(), value.studentEnd(),
                        value.reviewerScoreDeadline(), value.councilReportDate());
            } catch (ReflectiveOperationException exception) {
                throw missingContract(exception);
            }
        }

        private TestConfig testConfig(Object value) {
            return new TestConfig(
                    (String) accessor(value, "name"),
                    ((Enum<?>) accessor(value, "topicType")).name(),
                    (Instant) accessor(value, "lecturerStart"),
                    (Instant) accessor(value, "lecturerEnd"),
                    (Instant) accessor(value, "studentStart"),
                    (Instant) accessor(value, "studentEnd"),
                    (LocalDate) accessor(value, "reviewerScoreDeadline"),
                    (LocalDate) accessor(value, "councilReportDate"));
        }

        private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
            try {
                Method method = rulesClass().getDeclaredMethod(methodName, parameterTypes);
                return method.invoke(null, args);
            } catch (InvocationTargetException exception) {
                if (exception.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (exception.getCause() instanceof Error error) {
                    throw error;
                }
                throw missingContract(exception);
            } catch (ReflectiveOperationException exception) {
                throw missingContract(exception);
            }
        }

        private Object accessor(Object target, String name) {
            try {
                return target.getClass().getDeclaredMethod(name).invoke(target);
            } catch (ReflectiveOperationException exception) {
                throw missingContract(exception);
            }
        }

        private Class<?> rulesClass() {
            return load(RULES_CLASS);
        }

        private Class<?> configurationClass() {
            return load(RULES_CLASS + "$PeriodConfiguration");
        }

        private Class<?> topicTypeClass() {
            return load(RULES_CLASS + "$TopicType");
        }

        private static Class<?> load(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException exception) {
                throw missingContract(exception);
            }
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private static Object enumValue(Class<?> type, String name) {
            return Enum.valueOf((Class<? extends Enum>) type, name);
        }

        private static AssertionError missingContract(ReflectiveOperationException cause) {
            return new AssertionError(
                    "T022 must implement the PeriodRules contract exercised by T018", cause);
        }
    }
}
