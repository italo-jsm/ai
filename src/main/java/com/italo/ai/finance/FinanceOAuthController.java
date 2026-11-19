package com.italo.ai.finance;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.finance.oauth.enabled", havingValue = "true")
class FinanceOAuthController {
    private static final String COOKIE = "finance_oauth_browser";
    private final FinanceLinks links;
    private final FinanceOAuthSettings settings;

    FinanceOAuthController(FinanceLinks links, FinanceOAuthSettings settings) {
        this.links = links;
        this.settings = settings;
    }

    @GetMapping(FinanceOAuthSettings.START)
    ResponseEntity<String> start(@RequestParam(name = "ticket", required = false) String ticket) {
        return links.begin(ticket).map(browser -> response(HttpStatus.SEE_OTHER)
                        .location(browser.uri())
                        .header(HttpHeaders.SET_COOKIE, cookie(browser.browserSecret(), FinanceLinks.LINK_TTL))
                        .<String>build())
                .orElseGet(() -> response(HttpStatus.BAD_REQUEST)
                        .body("Link inválido ou expirado. Repita sua pergunta no Telegram para gerar outro."));
    }

    @GetMapping(FinanceOAuthSettings.CALLBACK)
    ResponseEntity<String> callback(@RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = COOKIE, required = false) String browser) {
        return switch (links.complete(state, browser, code, error)) {
            case LINKED -> response(HttpStatus.OK)
                    .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
                    .body("Conta Finance vinculada. Volte ao Telegram e repita sua pergunta.");
            case INVALID -> response(HttpStatus.BAD_REQUEST)
                    .body("Vínculo inválido, cancelado ou expirado. Volte ao Telegram e repita sua pergunta.");
            case UNAVAILABLE -> response(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
                    .body("O Keycloak está indisponível. Tente novamente pelo Telegram daqui a pouco.");
        };
    }

    private ResponseEntity.BodyBuilder response(HttpStatus status) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer");
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(settings.secureCookie())
                .sameSite("Lax").path(FinanceOAuthSettings.CALLBACK).maxAge(maxAge).build().toString();
    }
}
