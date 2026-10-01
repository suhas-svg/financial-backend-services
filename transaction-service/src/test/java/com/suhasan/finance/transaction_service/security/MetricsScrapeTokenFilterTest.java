package com.suhasan.finance.transaction_service.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsScrapeTokenFilterTest {

    private static final String TOKEN = "s3cr3t-scrape-token-with-enough-length";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenGrantsOnlyTheScraperRoleOnTheMetricsPath() throws Exception {
        Authentication auth = run(new MetricsScrapeTokenFilter(TOKEN), "/actuator/prometheus", "Bearer " + TOKEN);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_METRICS_SCRAPER");
    }

    @Test
    void tokenIsIgnoredOnEveryOtherPath() throws Exception {
        assertThat(run(new MetricsScrapeTokenFilter(TOKEN), "/actuator/metrics", "Bearer " + TOKEN)).isNull();
        assertThat(run(new MetricsScrapeTokenFilter(TOKEN), "/api/accounts", "Bearer " + TOKEN)).isNull();
    }

    @Test
    void wrongOrMissingTokenDoesNotAuthenticate() throws Exception {
        assertThat(run(new MetricsScrapeTokenFilter(TOKEN), "/actuator/prometheus", "Bearer " + TOKEN + "x")).isNull();
        assertThat(run(new MetricsScrapeTokenFilter(TOKEN), "/actuator/prometheus", null)).isNull();
    }

    @Test
    void unsetOrWeakTokenDisablesTheFilter() throws Exception {
        assertThat(run(new MetricsScrapeTokenFilter(""), "/actuator/prometheus", "Bearer ")).isNull();
        assertThat(run(new MetricsScrapeTokenFilter("short"), "/actuator/prometheus", "Bearer short")).isNull();
    }

    private static Authentication run(MetricsScrapeTokenFilter filter, String path, String authorization) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
