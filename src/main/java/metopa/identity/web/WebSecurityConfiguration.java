package metopa.identity.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
class WebSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider
    ) throws Exception {

        http
                .authenticationProvider(authenticationProvider)

                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users"
                        ).permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/csrf"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/installments/*"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/pages/*/content"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/works/*"
                        ).permitAll()

                        .anyRequest().authenticated()

                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                (request, response, authenticationException) ->
                                        writeProblem(
                                                response,
                                                HttpStatus.UNAUTHORIZED,
                                                "Authentication required",
                                                "Authentication is required to access this resource."
                                        )
                        )
                )

                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")

                        .successHandler(
                                (request, response, authentication) ->
                                        response.setStatus(
                                                HttpStatus.OK.value()
                                        )
                        )

                        .failureHandler(
                                (request, response, exception) ->
                                        writeProblem(
                                                response,
                                                HttpStatus.UNAUTHORIZED,
                                                "Authentication failed",
                                                "Invalid username or password."
                                        )
                        )

                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(
                                (request, response, authentication) ->
                                        response.setStatus(
                                                HttpStatus.OK.value()
                                        )
                        )
                );

        return http.build();
    }

    private static void writeProblem(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail
    ) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.getWriter().write("""
                {
                  "title": "%s",
                  "status": %d,
                  "detail": "%s"
                }
                """.formatted(
                title,
                status.value(),
                detail
        ));
    }
}