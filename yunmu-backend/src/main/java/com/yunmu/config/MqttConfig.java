package com.yunmu.config;

import com.yunmu.service.mqtt.MqttMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class MqttConfig {

    @Value("${mqtt.broker.url:tcp://120.27.235.176:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:yunmu-server-${random.int(1000,9999)}}")
    private String clientId;

    @Value("${mqtt.topics:7249}")
    private String[] topics;

    @Value("${mqtt.username:root}")
    private String username;

    @Value("${mqtt.password:root}")
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
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory(), topics);

        adapter.setCompletionTimeout(completionTimeout);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel());

        // 注意：Spring Integration 的 MqttPahoMessageDrivenChannelAdapter 不支持直接设置回调
        // 连接状态会在日志中自动输出，我们可以在消息处理器中记录

        log.info("MQTT入站适配器配置完成");
        log.info("  Client ID: {}", clientId);
        log.info("  Topics: {}", String.join(", ", topics));
        log.info("  QoS: {}", qos);
        log.info("正在连接MQTT服务器...");

        return adapter;
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