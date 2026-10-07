package com.yunmu.config;

import com.yunmu.service.mqtt.MqttMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Slf4j
@Configuration
// 缺陷 F 修复：MQTT broker 不可达时不再让整个 Spring 应用启动失败。
// mqtt.enabled=false（或环境变量 MQTT_ENABLED=false）时完全不装配 MQTT 相关 Bean，
// 应用仍可正常提供 HTTP 接口（含 AI 助手），MQTT 仅作为可选的接入通道。
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    @Value("${mqtt.broker.url:tcp://120.27.235.176:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:yunmu-server-${random.int(1000,9999)}}")
    private String clientId;

    @Value("${mqtt.topics:7249}")
    private String[] topics;

    // 缺陷 E 修复：不再默认 root/root 弱口令。
    // 优先从环境变量 MQTT_USERNAME / MQTT_PASSWORD 读取，未配置则为空（不启用认证）
    @Value("${mqtt.username:${MQTT_USERNAME:}}")
    private String username;

    @Value("${mqtt.password:${MQTT_PASSWORD:}}")
    private String password;

    @Value("${mqtt.qos:1}")
    private int qos;

    @Value("${mqtt.completion-timeout:5000}")
    private int completionTimeout;

    @Autowired
    private MqttMessageHandler mqttMessageHandler;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[]{brokerUrl});
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        // 设置认证信息
        if (username != null && !username.trim().isEmpty()) {
            options.setUserName(username);
            if (password != null && !password.trim().isEmpty()) {
                options.setPassword(password.toCharArray());
            }
            log.info("MQTT认证配置 - 用户名: {}", username);
        }

        factory.setConnectionOptions(options);

        log.info("MQTT客户端工厂配置完成");
        log.info("  Broker: {}", brokerUrl);
        log.info("  Client ID: {}", clientId);
        log.info("  Username: {}", username);
        log.info("  Password: {}", password != null && !password.isEmpty() ? "******" : "未设置");

        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttInbound() {
        try {
            MqttPahoMessageDrivenChannelAdapter adapter =
                    new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory(), topics);

            adapter.setCompletionTimeout(completionTimeout);
            adapter.setConverter(new DefaultPahoMessageConverter());
            adapter.setQos(qos);
            adapter.setOutputChannel(mqttInputChannel());

            // 缺陷 F 修复：MQTT 属于「可选接入通道」，连接失败不应阻断应用启动。
            // 关闭 autoStartup，改为在 ApplicationReadyEvent 后异步启动，
            // 这样 broker 不可达时仅打印告警，HTTP 接口照常可用。
            adapter.setAutoStartup(false);

            log.info("MQTT入站适配器配置完成");
            log.info("  Client ID: {}", clientId);
            log.info("  Topics: {}", String.join(", ", topics));
            log.info("  QoS: {}", qos);
            log.info("  Broker: {}（不可达时仅告警，不影响应用启动）", brokerUrl);

            return adapter;
        } catch (Exception e) {
            log.error("MQTT 入站适配器装配失败，MQTT 接入将被禁用，应用继续启动：{}", e.getMessage());
            // 返回一个空实现的 MessageProducer，保证容器装配不失败
            return new MessageProducer() {
                private volatile MessageChannel outputChannel;

                @Override
                public void setOutputChannel(MessageChannel outputChannel) {
                    this.outputChannel = outputChannel;
                }

                @Override
                public MessageChannel getOutputChannel() {
                    return this.outputChannel;
                }
            };
        }
    }

    /**
     * 应用就绪后再异步启动 MQTT 客户端。
     * 若 broker 不可达，这里只记录告警，不会影响已经启动完成的 HTTP 服务。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startMqttAfterReady() {
        try {
            MessageProducer producer = mqttInbound();
            if (producer instanceof MqttPahoMessageDrivenChannelAdapter) {
                ((MqttPahoMessageDrivenChannelAdapter) producer).start();
                log.info("MQTT 客户端已异步启动");
            }
        } catch (Exception e) {
            log.warn("MQTT 客户端启动失败（broker 可能不可达），已降级为仅 HTTP 模式：{}", e.getMessage());
        }
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler mqttMessageProcessor() {
        return message -> {
            try {
                String topic = (String) message.getHeaders().get("mqtt_receivedTopic");

                // 修复：不直接强转 byte[]，而是处理 Object
                Object payloadObj = message.getPayload();
                String payloadStr;
                byte[] payloadBytes;

                if (payloadObj instanceof byte[]) {
                    payloadBytes = (byte[]) payloadObj;
                    payloadStr = new String(payloadBytes);
                } else {
                    payloadStr = String.valueOf(payloadObj);
                    payloadBytes = payloadStr.getBytes();
                }

                log.info("╔══════════════════════════════════════════════════════════════╗");
                log.info("║                   收到MQTT消息                                ║");
                log.info("╠══════════════════════════════════════════════════════════════╣");
                log.info("║ Topic: {}", topic);
                log.info("║ Payload长度: {} 字节", payloadBytes.length);
                log.info("║ Payload内容: {}", payloadStr);
                log.info("╚══════════════════════════════════════════════════════════════╝");

                // 传递给你的业务逻辑处理器
                mqttMessageHandler.handleMessage(topic, payloadStr);

            } catch (Exception e) {
                log.error("❌ 处理MQTT消息时发生异常", e);
            }
        };
    }
}