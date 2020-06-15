package com.davidranndo.reactorcontainers.service;

import com.davidranndo.reactorcontainers.domain.Person;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PersonService {

    Flux<Person> getAllPersons();
    Mono<Person> getPersonByName(String firstName);
}
