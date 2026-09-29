package vn.edu.topicregistration.security;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import vn.edu.topicregistration.repository.UserAccountRepository;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain applicationSecurity(
            HttpSecurity http,
            UserAccountRepository accounts,
            Clock clock,
            AccountDetailsService accountDetailsService,
            PasswordEncoder passwordEncoder) throws Exception {
        LoginRateLimiter loginRateLimiter = new LoginRateLimiter(clock);
        DaoAuthenticationProvider provider = accountAuthenticationProvider(
                accountDetailsService, passwordEncoder);
        http.authenticationManager(new ProviderManager(provider))
                .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.GET,
                            "/login", "/error", "/css/**", "/js/**", "/vendor/**", "/img/**")
                    .permitAll()
                    .anyRequest().authenticated())
                .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .successHandler(loginRateLimiter)
                    .failureHandler(loginRateLimiter)
                    .permitAll())
                .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
                .sessionManagement(session -> session
                    .sessionFixation(fixation -> fixation.migrateSession()))
                .addFilterBefore(loginRateLimiter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        new AccountStateFilter(accounts),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static DaoAuthenticationProvider accountAuthenticationProvider(
            AccountDetailsService accountDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(accountDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> {
            if (!user.isAccountNonLocked()) {
                throw new LockedException("Account is locked");
            }
            if (!user.isAccountNonExpired()) {
                throw new AccountExpiredException("Account is expired");
            }
        });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("Account is disabled");
            }
            if (!user.isCredentialsNonExpired()) {
                throw new CredentialsExpiredException("Credentials are expired");
            }
        });
        return provider;
    }
}
