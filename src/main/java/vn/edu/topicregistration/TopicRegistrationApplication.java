package vn.edu.topicregistration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import vn.edu.topicregistration.seed.DemoSeedProperties;
import vn.edu.topicregistration.config.ReportStorageProperties;

@SpringBootApplication
@EnableConfigurationProperties({DemoSeedProperties.class, ReportStorageProperties.class})
public class TopicRegistrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(TopicRegistrationApplication.class, args);
    }
}
