package no.novari.flyt.gateway.instance.error

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

class GlobalExceptionHandlerMvcTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(TestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .addFilters<StandaloneMockMvcBuilder>(
                ExceptionTranslationFilter(Http403ForbiddenEntryPoint()),
            ).build()

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `denied access for a signed-in user gives 403, not 500`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated("user", null, listOf(SimpleGrantedAuthority("ROLE_USER")))

        mockMvc.perform(get("/admin")).andExpect(status().isForbidden)
    }

    @Test
    fun `unsupported method gives 405 with Allow header`() {
        mockMvc
            .perform(get("/instances"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(header().string("Allow", "POST"))
    }

    @RestController
    class TestController {
        // Stands in for @PreAuthorize("hasRole('ADMIN')"), which throws AuthorizationDeniedException.
        @GetMapping("/admin")
        fun admin(): String = throw AccessDeniedException("Access Denied")

        @PostMapping("/instances")
        fun instances(): String = "ok"
    }
}
