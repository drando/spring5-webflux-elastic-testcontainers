package com.davidranndo.reactorcontainers.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "persons")
@Data
public class Person {

    @Id
    private String id;

    @Field(name = "firstName", type = FieldType.Text)
    private String firstName;

    @Field(name="lastName", type = FieldType.Text)
    private String lastName;

    @Field(name="birthDate", type= FieldType.Date)
    private LocalDateTime birthDate;
}
