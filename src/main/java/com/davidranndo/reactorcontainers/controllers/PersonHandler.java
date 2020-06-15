package com.davidranndo.reactorcontainers.controllers;

import com.davidranndo.reactorcontainers.domain.Person;
import com.davidranndo.reactorcontainers.service.PersonService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class PersonHandler {

    private final PersonService personService;

    public PersonHandler(PersonService personService) {
        this.personService = personService;
    }

    public Mono<ServerResponse> getAllPersons(ServerRequest serverRequest){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(this.personService.getAllPersons(), Person.class);
    }

    public Mono<ServerResponse> getPersonById(ServerRequest serverRequest){

        String firstName = serverRequest.pathVariable("firstName");

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(this.personService.getPersonByName(firstName), Person.class);
    }
}
