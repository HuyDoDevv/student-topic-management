package vn.edu.topicregistration.shared.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.seed")
public record DemoSeedProperties(boolean enabled, String password) {
}
