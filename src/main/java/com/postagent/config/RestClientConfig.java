package com.postagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean(name = "restClient")
    public RestClient.Builder restClient() {
//        HttpClient httpClient = HttpClient.newBuilder()
//                .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", 7890)))
//                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(requestFactory);
	}
}