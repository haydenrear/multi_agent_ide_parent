package com.hayden.utilitymodule.kafka;

import com.hayden.utilitymodule.MapFunctions;
import lombok.experimental.UtilityClass;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@UtilityClass
public class KafkaProperties {

    public static Properties kafkaConsumerProperties(
            String bootstrapUrls,
            Class<? extends Deserializer<?>> keySerializer,
            Class<? extends Deserializer<?>> valueSerializer
    ) {
        Map<String, String> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapUrls);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keySerializer.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueSerializer.getName());
//        properties.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, StickyAssignor.class.getName());
        return MapFunctions.CreateProperties(properties);
    }


    public static Properties kafkaProducerProperties(
            String bootstrapUrls,
            Class<? extends Serializer<?>> keySerializer,
            Class<? extends Serializer<?>> valueSerializer
    ) {
        Map<String, String> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapUrls);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.getName());
        return MapFunctions.CreateProperties(properties);
    }

}
