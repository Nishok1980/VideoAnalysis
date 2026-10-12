package in.nishok.videoanalysis.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Local binding plus host/origin checks and a custom-header CSRF boundary. */
@Component
public class LocalRequestFilter extends OncePerRequestFilter
{
    @Override
    protected void doFilterInternal(HttpServletRequest request,
        HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException
    {
        String host = request.getHeader("Host");
        String origin = request.getHeader("Origin");
        String port = ":" + request.getLocalPort();
        boolean validHost = ("127.0.0.1" + port).equals(host)
            || ("localhost" + port).equals(host);
        boolean validOrigin = origin == null
            || ("http://" + host).equals(origin);
        boolean read = request.getMethod().equals("GET")
            || request.getMethod().equals("HEAD");
        if (!validHost || !validOrigin
            || (!read && !"1".equals(request.getHeader("X-VideoAnalysis"))))
        {
            response.sendError(403, "Open this application on localhost");
            return;
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' blob:; media-src 'self'; connect-src 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        chain.doFilter(request, response);
    }
}
