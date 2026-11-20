package no.novari.flyt.gateway.instance.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.web.client.RestClient
import java.time.Duration

@AutoConfiguration
class RestClientConfiguration {
    // client_credentials tokens belong to the app, not to an HTTP session, so they are kept in the
    // OAuth2AuthorizedClientService. One manager serves all registrations below.
    @Bean("internalClientAuthorizedClientManager")
    fun internalClientAuthorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService,
        requestFactoryBuilder: ClientHttpRequestFactoryBuilder<*>,
        requestFactorySettings: HttpClientSettings,
    ): OAuth2AuthorizedClientManager {
        // The default token client has no timeouts. Same converters and error handler as its default, plus ours.
        val tokenResponseClient = RestClientClientCredentialsTokenResponseClient()
        tokenResponseClient.setRestClient(
            RestClient
                .builder()
                .requestFactory(requestFactoryBuilder.build(requestFactorySettings.withDefaultTimeouts()))
                .configureMessageConverters { converters ->
                    converters.addCustomConverter(FormHttpMessageConverter())
                    converters.addCustomConverter(OAuth2AccessTokenResponseHttpMessageConverter())
                }.defaultStatusHandler(OAuth2ErrorResponseErrorHandler())
                .build(),
        )
        val manager =
            AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService)
        manager.setAuthorizedClientProvider(
            OAuth2AuthorizedClientProviderBuilder
                .builder()
                .clientCredentials { it.accessTokenResponseClient(tokenResponseClient) }
                .build(),
        )
        return manager
    }

    // Large files take time, so file-service has fixed long timeouts that spring.http.clients.* does not change.
    @Bean
    fun fileRestClient(
        restClientBuilder: RestClient.Builder,
        @Qualifier("internalClientAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        requestFactoryBuilder: ClientHttpRequestFactoryBuilder<*>,
        requestFactorySettings: HttpClientSettings,
    ): RestClient =
        internalClient(
            restClientBuilder,
            authorizedClientManager,
            requestFactoryBuilder.build(requestFactorySettings.withTimeouts(FILE_TIMEOUT, FILE_TIMEOUT)),
            "file-service",
        ).build()

    @Bean
    fun intgrConfRestClient(
        restClientBuilder: RestClient.Builder,
        @Qualifier("internalClientAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        requestFactoryBuilder: ClientHttpRequestFactoryBuilder<*>,
        requestFactorySettings: HttpClientSettings,
        @Value(
            "\${novari.flyt.intgr-conf-service-url:http://fint-flyt-intgr-conf-service:8080\${server.servlet.context-path:}}",
        )
        baseUrl: String,
    ): RestClient =
        internalClient(
            restClientBuilder,
            authorizedClientManager,
            requestFactoryBuilder.build(requestFactorySettings.withDefaultTimeouts()),
            "intgr-conf-service",
        ).baseUrl("${baseUrl.trimEnd('/')}/api/intern-klient")
            .build()

    @Bean
    fun historyRestClient(
        restClientBuilder: RestClient.Builder,
        @Qualifier("internalClientAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        requestFactoryBuilder: ClientHttpRequestFactoryBuilder<*>,
        requestFactorySettings: HttpClientSettings,
        @Value(
            "\${novari.flyt.history-service-url:http://fint-flyt-history-service:8080\${server.servlet.context-path:}}",
        )
        baseUrl: String,
    ): RestClient =
        internalClient(
            restClientBuilder,
            authorizedClientManager,
            requestFactoryBuilder.build(requestFactorySettings.withDefaultTimeouts()),
            "history-service",
        ).baseUrl("${baseUrl.trimEnd('/')}/api/intern-klient")
            .build()

    private fun internalClient(
        restClientBuilder: RestClient.Builder,
        authorizedClientManager: OAuth2AuthorizedClientManager,
        requestFactory: ClientHttpRequestFactory,
        clientRegistrationId: String,
    ): RestClient.Builder {
        val interceptor = OAuth2ClientHttpRequestInterceptor(authorizedClientManager)
        interceptor.setClientRegistrationIdResolver { clientRegistrationId }
        // The default resolves the incoming caller, which would fetch one token per source application.
        interceptor.setPrincipalResolver { APP_PRINCIPAL }
        return restClientBuilder
            .requestFactory(requestFactory)
            .requestInterceptor(interceptor)
    }

    // The calls block the incoming request, so they must not fall back to the HTTP client's minutes-long defaults.
    // spring.http.clients.connect-timeout and read-timeout in the app override these.
    private fun HttpClientSettings.withDefaultTimeouts(): HttpClientSettings =
        withTimeouts(connectTimeout() ?: DEFAULT_CONNECT_TIMEOUT, readTimeout() ?: DEFAULT_READ_TIMEOUT)

    private companion object {
        val APP_PRINCIPAL =
            AnonymousAuthenticationToken(
                "anonymous",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"),
            )
        val FILE_TIMEOUT: Duration = Duration.ofSeconds(300)
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
