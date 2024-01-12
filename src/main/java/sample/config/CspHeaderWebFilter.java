package sample.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
public class CspHeaderWebFilter implements WebFilter {

  @Value("${prizmdoc.hybridViewing.enabled:#{null}}")
  private boolean enableHybridViewing;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    StringBuilder contentSecurityPolicy = new StringBuilder("script-src 'self'");
    if(enableHybridViewing){
      contentSecurityPolicy.append("; worker-src blob:");
    }
    exchange.getResponse()
        .getHeaders()
        .add("Content-Security-Policy", contentSecurityPolicy.toString());
    return chain.filter(exchange);
  }
}