package com.davidranndo.reactorcontainers.repositories;

import com.davidranndo.reactorcontainers.domain.Person;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ElasticReactiveRepository extends ReactiveElasticsearchRepository<Person, String>{

    Mono<Person> findByFirstName(String firstName);

}
