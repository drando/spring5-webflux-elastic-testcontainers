package com.davidranndo.reactorcontainers.bootstrap;

import com.davidranndo.reactorcontainers.domain.Person;
import com.davidranndo.reactorcontainers.repositories.ElasticReactiveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Component
public class Bootstrap implements CommandLineRunner {

    @Autowired
    ElasticReactiveRepository elasticReactiveRepository;

    @Override
    public void run(String... args) throws Exception {

        Person person1 = new Person();
        person1.setId(UUID.randomUUID().toString());
        person1.setFirstName("John");
        person1.setLastName("Wick");
        person1.setBirthDate(LocalDateTime.of(
                LocalDate.of(1969, 10, 1),
                LocalTime.of(11,30)
        ));

        Person person2 = new Person();
        person2.setId(UUID.randomUUID().toString());
        person2.setFirstName("Antonio");
        person2.setLastName("Banderas");
        person2.setBirthDate(LocalDateTime.of(
                LocalDate.of(1980, 10, 2),
                LocalTime.of(12,30)
        ));

        this.elasticReactiveRepository.save(person1).block();
        this.elasticReactiveRepository.save(person2).block();
    }
}
