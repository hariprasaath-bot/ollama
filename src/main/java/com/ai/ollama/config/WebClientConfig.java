package com.ai.ollama.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(
            @Value("${http.client.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${http.client.response-timeout-ms:0}") long responseTimeoutMs,
            @Value("${http.client.read-timeout-ms:0}") long readTimeoutMs,
            @Value("${http.client.write-timeout-ms:0}") long writeTimeoutMs,
            @Value("${http.client.max-in-memory-size-mb:16}") int maxInMemorySizeMb
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);
        if (responseTimeoutMs > 0) {
            httpClient = httpClient.responseTimeout(Duration.ofMillis(responseTimeoutMs));
        }
        if (readTimeoutMs > 0 || writeTimeoutMs > 0) {
            httpClient = httpClient.doOnConnected(conn -> {
                if (readTimeoutMs > 0) {
                    conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS));
                }
                if (writeTimeoutMs > 0) {
                    conn.addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS));
                }
            });
        }

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(maxInMemorySizeMb * 1024 * 1024))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies);
    }
}
