package no.novari.flyt.gateway.instance.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.client.RestClient

class RestClientConfigurationTest {
    @Test
    fun `fileRestClient is built from the injected RestClient builder`() {
        var injectedBuilderInterceptorCalled = false
        val restClientBuilder =
            RestClient.builder().requestInterceptor { _, _, _ ->
                injectedBuilderInterceptorCalled = true
                MockClientHttpResponse(ByteArray(0), HttpStatus.OK)
            }

        val fileRestClient =
            RestClientConfiguration().fileRestClient(
                restClientBuilder,
                mock<OAuth2AuthorizedClientManager>(),
                mock<ClientHttpRequestFactory>(),
            )

        fileRestClient
            .get()
            .uri("http://file-service/ping")
            .retrieve()
            .toBodilessEntity()

        assertThat(injectedBuilderInterceptorCalled).isTrue()
    }
}
