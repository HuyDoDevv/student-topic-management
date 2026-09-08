package vn.edu.topicregistration.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;

@Configuration
@Profile("!preview")
public class FoundationSecurityConfig {
    @Bean
    SecurityFilterChain foundationSecurity(HttpSecurity http) throws Exception {
        // Only a public, non-business shell exists. Identity policies await Q-18.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/css/**", "/js/**", "/vendor/**", "/img/**").permitAll()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(
                new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)));
        // Keep default CSRF protection. No login, self-registration, or demo credentials.
        return http.build();
    }

    @Bean
    UserDetailsService noAccountsYet() {
        return username -> { throw new UsernameNotFoundException("Identity provisioning is not implemented"); };
    }
}
