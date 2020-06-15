package com.davidranndo.reactorcontainers.repository;

import com.davidranndo.reactorcontainers.domain.Person;
import com.davidranndo.reactorcontainers.repositories.ElasticReactiveRepository;
import lombok.extern.log4j.Log4j2;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Log4j2
@Profile("test")
@SpringBootTest

@ContextConfiguration(initializers = {ElasticSearchRepositoryIT.Initializer.class})
public class ElasticSearchRepositoryIT {

    private static final String                 ELASTIC_VERSION            = "7.6.2";
    private static final String                 DOCKER_ELASTIC_URL_OFICIAL = "docker.elastic.co/elasticsearch/elasticsearch:";
    private static       ElasticsearchContainer elasticsearchContainer;

    @Autowired
    private ElasticReactiveRepository elasticReactiveRepository;

    @BeforeAll
    static void setUp() {

        elasticsearchContainer = new ElasticsearchContainer(DOCKER_ELASTIC_URL_OFICIAL + ELASTIC_VERSION)
                .withExposedPorts(9200, 9300);

        elasticsearchContainer.start();

    }

    @AfterAll
    static void afterAll() {
        elasticsearchContainer.stop();
    }

    @BeforeEach
    public void beforeEach() {

        if (this.elasticReactiveRepository.findAll().collectList().block().size() > 0){
            this.elasticReactiveRepository.deleteAll().block();
        }

        Person first = new Person();
        first.setId(UUID.randomUUID().toString());
        first.setFirstName("Lara");
        first.setLastName("Croft");
        first.setBirthDate(LocalDateTime.of(
                LocalDate.of(1980, 10, 1),
                LocalTime.of(10, 30)
        ));

        Person second = new Person();
        second.setId(UUID.randomUUID().toString());
        second.setFirstName("Bruce");
        second.setLastName("Wayne");
        second.setBirthDate(LocalDateTime.of(
                LocalDate.of(1975, 11, 2),
                LocalTime.of(10, 45)
        ));

        this.elasticReactiveRepository.save(first).block();
        this.elasticReactiveRepository.save(second).block();
    }

    @Test
    public void testGetAll() throws Exception {

        List<Person> personList = this.elasticReactiveRepository.findAll().collectList().block();
        assertEquals(2, personList.size());
    }

    @Test
    public void testPerson() throws Exception {

        Person person = this.elasticReactiveRepository.findByFirstName("lara").block(); // using lowercase on purpose

        assertEquals("Lara", person.getFirstName());
        assertEquals("Croft", person.getLastName());
        assertEquals(LocalDateTime.of(1980, 10, 1, 10, 30), person.getBirthDate());
    }


    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(@NotNull ConfigurableApplicationContext configurableApplicationContext) {

            // Set the application values just for this test in the context.
            TestPropertyValues.of(
                    "spring.data.elasticsearch.client.reactive.endpoints=localhost:" + elasticsearchContainer.getMappedPort(9200)
            ).applyTo(configurableApplicationContext);

            log.info("Used port: " + elasticsearchContainer.getMappedPort(9200));
        }
    }


}
