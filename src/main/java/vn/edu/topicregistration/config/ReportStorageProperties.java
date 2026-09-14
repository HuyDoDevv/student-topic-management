package vn.edu.topicregistration.config;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.report-storage")
public record ReportStorageProperties(Path root) {
    public ReportStorageProperties {
        Objects.requireNonNull(root, "Report storage root is required");
    }
}
