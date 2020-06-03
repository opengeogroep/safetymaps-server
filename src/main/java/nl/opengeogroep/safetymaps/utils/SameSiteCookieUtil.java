package nl.opengeogroep.safetymaps.utils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author matthijsln
 */
public class SameSiteCookieUtil {
    public static void addCookieWithSameSite(HttpServletResponse response, Cookie cookie, String sameSite) {

        OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofSeconds(cookie.getMaxAge()));
        String cookieExpires = DateTimeFormatter.RFC_1123_DATE_TIME.format(expires);

        String value = String.format("%s=%s; Max-Age=%d; Expires=%s; Path=%s",
                cookie.getName(),
                cookie.getValue(),
                cookie.getMaxAge(),
                cookieExpires,
                cookie.getPath());

        List attributes = new ArrayList();
        if(cookie.getDomain() != null) {
            attributes.add("Domain=" + cookie.getDomain());
        }
        if(cookie.isHttpOnly()) {
            attributes.add("HttpOnly");
        }
        if(cookie.getSecure()) {
            attributes.add("Secure");
        }
        if(sameSite != null) {
            attributes.add("SameSite=" + sameSite);
        }
        if(!attributes.isEmpty()) {
            value += "; " + String.join("; ", attributes);
        }

        response.addHeader("Set-Cookie", value);
    }
}
