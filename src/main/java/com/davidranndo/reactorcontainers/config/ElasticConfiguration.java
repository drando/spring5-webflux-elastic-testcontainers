package com.davidranndo.reactorcontainers.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient;
import org.springframework.data.elasticsearch.client.reactive.ReactiveRestClients;
import org.springframework.data.elasticsearch.config.AbstractReactiveElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Configuration
@EnableReactiveElasticsearchRepositories
public class ElasticConfiguration extends AbstractReactiveElasticsearchConfiguration {

    @Value(value = "${spring.data.elasticsearch.client.reactive.endpoints}")
    private String ELASTIC_HOST;

    @Override
    @Bean
    public @NotNull ReactiveElasticsearchClient reactiveElasticsearchClient() {
        final ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                                  .connectedTo(ELASTIC_HOST)
                .build();

        return ReactiveRestClients.create(clientConfiguration);
    }

    @Bean
    @Override
    public @NotNull ElasticsearchCustomConversions elasticsearchCustomConversions() {
        return new ElasticsearchCustomConversions(
                Arrays.asList(new LocalDateTimeToString(), new StringToLocalDateTime())
        );
    }

    @WritingConverter
    static class LocalDateTimeToString implements Converter<LocalDateTime, String>{
        @Override
        public String convert(LocalDateTime localDateTime) {
            return localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    @ReadingConverter
    static class StringToLocalDateTime implements Converter<String, LocalDateTime>{

        @Override
        public LocalDateTime convert(String s) {
            return LocalDateTime.parse(s);
        }
    }






}
