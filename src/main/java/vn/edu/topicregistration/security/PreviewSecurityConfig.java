package vn.edu.topicregistration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/** Allows the UI catalogue to be reviewed without a database or demo accounts. */
@Configuration
@Profile("preview")
public class PreviewSecurityConfig {

    @Bean
    SecurityFilterChain previewSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    UserDetailsService previewHasNoAccounts() {
        return username -> {
            throw new UsernameNotFoundException("Preview mode has no accounts");
        };
    }
}
