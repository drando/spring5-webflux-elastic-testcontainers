package com.davidranndo.reactorcontainers.service;

import com.davidranndo.reactorcontainers.domain.Person;
import com.davidranndo.reactorcontainers.repositories.ElasticReactiveRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PersonServiceImpl implements PersonService{

    private final ElasticReactiveRepository reactiveRepository;

    public PersonServiceImpl(ElasticReactiveRepository reactiveRepository) {
        this.reactiveRepository = reactiveRepository;
    }

    @Override
    public Flux<Person> getAllPersons() {
        return this.reactiveRepository.findAll();
    }

    @Override
    public Mono<Person> getPersonByName(String firstName) {
        return this.reactiveRepository.findByFirstName(firstName);
    }
}
