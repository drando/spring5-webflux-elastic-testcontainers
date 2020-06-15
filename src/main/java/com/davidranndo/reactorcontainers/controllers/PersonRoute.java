package com.davidranndo.reactorcontainers.controllers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class PersonRoute {

    @Bean
    RouterFunction<ServerResponse> personRoutes(PersonHandler personHandler){
        return route()
                .GET("/api/v1/allpersons", personHandler::getAllPersons)
                .GET("/api/v1/person/{firstName}", personHandler::getPersonById)
                .build();
    }
}
