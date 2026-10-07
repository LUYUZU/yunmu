package com.yunmu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    /**
     * 发往 Python ML 服务的鉴权 Token（与 Python 端 YUNMU_API_TOKEN 保持一致）。
     * 为空时不附加鉴权头（便于本地无鉴权联调）。
     */
    @Value("${yunmu.python-service.auth-token:}")
    private String mlServiceToken;

    /**
     * LLM 属于「慢接口」：Python 侧单次上游调用超时 60s，并对瞬时故障最多重试 3 次，
     * 最坏情况接近 3 分钟。因此 Java 读取超时必须显著大于 Python 侧的预算，
     * 否则会在 Python 还没重试完就先抛 ResourceAccessException，
     * 用户只能看到一句无信息量的「LLM 网关调用失败」。
     */
    @Value("${yunmu.llm.read-timeout-ms:180000}")
    private int llmReadTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory());
        addAuthInterceptor(restTemplate);
        return restTemplate;
    }

    /**
     * 专供 LLM 网关使用的 RestTemplate（长读超时）。
     * 与通用 restTemplate 分开，避免把 MQTT 主链路的读超时一并放宽。
     */
    @Bean("llmRestTemplate")
    public RestTemplate llmRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(llmReadTimeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);
        addAuthInterceptor(restTemplate);
        return restTemplate;
    }

    /** 统一为出站请求附加 ML 服务鉴权头，避免每个调用点重复书写。 */
    private void addAuthInterceptor(RestTemplate restTemplate) {
        if (mlServiceToken == null || mlServiceToken.isBlank()) {
            return;
        }
        List<ClientHttpRequestInterceptor> interceptors =
                new ArrayList<>(restTemplate.getInterceptors());
        interceptors.add((request, body, execution) -> {
            request.getHeaders().set("X-API-Token", mlServiceToken);
            return execution.execute(request, body);
        });
        restTemplate.setInterceptors(interceptors);
    }

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5秒连接超时
        factory.setReadTimeout(30000);   // 30秒读取超时
        return factory;
    }
}