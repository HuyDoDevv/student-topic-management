package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import vn.edu.topicregistration.TopicRegistrationApplication;
import vn.edu.topicregistration.config.TimeConfig;
import vn.edu.topicregistration.controller.PeriodController;
import vn.edu.topicregistration.dto.PeriodForm;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.security.SecurityConfig;
import vn.edu.topicregistration.seed.LocalDemoSeeder;
import vn.edu.topicregistration.service.PeriodService;
import vn.edu.topicregistration.util.ProjectTime;

class LayeredArchitectureTest {
    private static final String BASE_PACKAGE = "vn.edu.topicregistration";
    private static final Path SOURCE_ROOT = Path.of(
            "src/main/java/vn/edu/topicregistration");
    private static final Set<String> ALLOWED_LAYERS = Set.of(
            "config",
            "controller",
            "dto",
            "entity",
            "exception",
            "repository",
            "security",
            "seed",
            "service",
            "util");

    @Test
    void applicationUsesOneSpringBootEntrypoint() throws IOException {
        assertTrue(TopicRegistrationApplication.class.isAnnotationPresent(
                SpringBootApplication.class));

        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            long entrypoints = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::declaresSpringBootApplication)
                    .count();
            assertEquals(1, entrypoints);
        }
    }

    @Test
    void productionTypesAreGroupedByMvcLayer() throws IOException {
        Map<Class<?>, String> representativeTypes = Map.ofEntries(
                Map.entry(TimeConfig.class, "config"),
                Map.entry(PeriodController.class, "controller"),
                Map.entry(PeriodForm.class, "dto"),
                Map.entry(RegistrationPeriodEntity.class, "entity"),
                Map.entry(ApplicationException.class, "exception"),
                Map.entry(RegistrationPeriodRepository.class, "repository"),
                Map.entry(SecurityConfig.class, "security"),
                Map.entry(LocalDemoSeeder.class, "seed"),
                Map.entry(PeriodService.class, "service"),
                Map.entry(ProjectTime.class, "util"));

        representativeTypes.forEach((type, layer) -> assertEquals(
                BASE_PACKAGE + "." + layer,
                type.getPackageName(),
                () -> type.getSimpleName() + " is in the wrong layer"));

        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(SOURCE_ROOT::relativize)
                    .filter(path -> path.getNameCount() > 1)
                    .forEach(path -> assertTrue(
                            ALLOWED_LAYERS.contains(path.getName(0).toString()),
                            () -> "Unexpected production package: " + path));
        }
    }

    private boolean declaresSpringBootApplication(Path source) {
        try {
            return Files.readString(source).contains("@SpringBootApplication");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + source, exception);
        }
    }
}
