# Spring Webflux Elasticsearch integration tests using Junit 5 and Testcontainers

Recently I introduced myself to the Reactive programming world using Spring Webflux, with a project that uses the outstanding Elasticsearch engine for storing data.

Developing a reactive application makes things different. This technology is young and sometimes, the lack of documentation, and having to do everything in a "non-blocking-stream" makes things trickier. But if you work with projects with a good amount of simultaneous connections, it's worth it. 

Having to develop it with Elastic on the data layer had me to deal with an important issue. The integration tests against it. I no longer have a beautiful embedded H2/MongoDB instance available for me. 
I needed a true solution to fire up an Elastic instance in the CI server and my development environment. 

Not doing a real test over the data server is an absolute no-go. You want to be **very sure** that the server will treat your data, your models, your domain objects correctly, and will keep doing it with future releases 
(and I can tell you that, elastic did change a lot of things with new mayor versions in the past). 

I've suffered in the relational world before because of it too (moving away from Mysql 5.6 for instance), so do not skip these kinds of tests ever.
This is also important for not only the data layer but for the service layer, where we have our B.I. dealing with it. You won't want to test them only with mocks, right? 

So you start wondering what to do if you don't have any good solution for it. A first approach could be the usual: "Ok, I just install a docker, and then remember myself to start and stop it before the tests". 
This docker has to be in a different port because you don't want to delete or change any data on the docker instance that you use for developing.

These tests will fail then in two cases:

1. If you don't remember to start the docker instance before the tests.
2. Not having that docker instance in the CI/CD server (with the very same configuration). I'm afraid Jenkins will bring you some rain for you.

Another idea in the plan could be: "Alright, I fire up another server and keep it up all the time, just for the tests".

Yes, that could be a solution. Apart from the resources taken, this would work until any connection problem or any server problem will make our tests fail. Also, you need to be the only one working on the project. If you work in a team, all your teammates will need to use the same server. 

So you have to discuss with them the indexes to be used inside Elastic, but that's nonsense because they will work eventually in the same project with you, right? They will need to work on the same documents and indexes, 
and you will have to document all of this. Anyone (even you in the future) will need to have a new development environment, and this server should be deployed together with it too. Things will get worse when you add more team members to the project. 


Looks like this solution is not looking good. I could guess what will be the next step: Just test manually on your machine before committing to GIT, and the CI server won't run the tests, or that tests will be disabled before you commit the code.

Ok, ok, there's something that can be done in the CI server:  launch a docker instance in my pipeline, before pulling the new changes from Git, compile and run the tests. But there's another problem you have to deal with: The CI/CD server environment. 
This server will have its own collection of ports used, and it will be running tests not only from you but from all the other projects, even from some other people working on the same one as you. So you have to program something that runs the docker instance on a random available port. 

So "Testcontainers" comes into rescue. You can use it with any Java application (not necessary with Spring), and it allows you to start a docker programmatically from your test class. It will download the docker image if it's not already there, starts it on a random available port, and just throw it away when you finish with it. Amazing. It was love at first sight.

But configuring it with Spring was a bit hard for me using elastic server and spring webflux. The documentation to configure everything that I could find was for deprecated versions, and most of the time disperse or very simple. So when I finally joined all pieces together, I decided to write this article to describe the whole process, and add a small Spring project to show it working. 

If you're a Spring developer, or you're starting with it, it will give you just the exact configuration that will help you out, or at least, some hints to deal with the problems. The project is in this very same Github repository.

## About this application

This is a very simple REST api that offers person data over two different endpoints using HTTP GET. It responses using the typical JSON formatted message:

1. api/v1/allpersons: Shows all the stored person data.
2. api/v1/person/{firstName}: Replies with a single person query. For instance: if the person's first name is "David", it will return that data using: /api/v1/person/david 

For each person, it will store the following data:

* First Name as a String
* Last Name as a String
* Date and Time of Birth as a LocalDateTime (to show a special case for Elastic mappings) 

## Dependencies

We use [Spring initializr](https://start.spring.io/) to create the Spring project. For this application we will use:

* spring-boot-starter-data-elasticsearch
* spring-boot-starter-webflux
* org.projectlombok (to help us with our POJOs and Log4j)

We based it on Java 14 JDK and Maven.

The only "external" dependency outside Spring Initializr is Testcontainers, so we add it to our maven pom.xml file:

```xml
<dependency>
   <groupId>org.testcontainers</groupId>
   <artifactId>elasticsearch</artifactId>
   <version>1.14.1</version>
</dependency>
```
This adds the dependency from testcontainers to run elasticsearch docker instances.

## Domain object

As we said, the data we're storing is very simple:

```java
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
```
About the annotations, all of them are from the [official Spring Data ElasticSearch](https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/#preface) 

* @Document from Spring Data: We annotate this class as a Document to be stored in Elasticsearch. If you've worked with MongoDB, it's the same idea. With Elastic, we set an index name for it (persons)
* @Data: From Project Lombok. It enables @ToString, @EqualsAndHashCode, @Getter, @Setter, @RequiredArgsConstructor for our class. 
* @Id: From Spring Data, just set the variable as the primary key. This ID will be autogenerated by Elasticsearch itself. 
* @Field: Using Elastic field types, we set with this annotation a name, and an elasticsearch type. 

## Setting up the repository

We set our reactive repository for elastic extending "ReactiveElasticsearchRepository".

We'll use the typical findAll available by default, and we include a "findByFirstName" for the other endpoint operation: 

```java
@Repository
public interface ElasticReactiveRepository extends ReactiveElasticsearchRepository<Person, String>{

    public Mono<Person> findByFirstName(String firstName);

}
```

## Spring configuration

We use a Java class for configuration, setting some values on the Spring context, related on how we'll configure the elasticsearch client on our application. 

```java
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
```

First, we'll enable the reactive repositories for elasticsearch using @EnableReactiveElasticsearchRepositories (mandatory).
But, why setting the rest of the Beans in that class? Just setting some properties in the application.properties file could be enough to use the default Spring configuration. Yes and no. 

Spring connects to Elastic using its REST API. We won't use any connection driver like it's needed with Mysql. Being this way, we have two different clients available:

#### 1. High Level Rest Client

This one is the default client for Spring, and it's recommended over the now deprecated "TransportClient";

#### 2. Reactive Elasticsearch Client

As the documentation says:

> The ReactiveElasticsearchClient is a non official driver based on WebClient. It uses the request/response objects provided by the Elasticsearch core project. Calls are directly operated on the reactive stack, not wrapping async (thread pool bound) responses into reactive types.

So we'll configure our class to use this client (we want to be fully reactive, right?), inside the Spring context for all the request using the repository interface. 

First step is extend from AbstractReactiveElasticsearchConfiguration, overriding this method:

```
@Override
    @Bean
    public @NotNull ReactiveElasticsearchClient reactiveElasticsearchClient() {
        final ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                                  .connectedTo(ELASTIC_HOST)
                .build();

        return ReactiveRestClients.create(clientConfiguration);
    }
```

If you need some other connection properties (like SSH, or AUTH configuration), that is the place to set them.  

### Note:
There's a ELASTIC_HOST constant used in the previous example. That string is filled from the file application.properties using the @Value annotation. We'll see how and why we need to use it that way later.

### The Date problem with Elasticsearch

We mentioned that our Person class stores the birth date using a LocalDateTime. This is done on purpose because, if you ever want to store dates and times, we'll quickly find a mapping problem (from our POJO to the message sent to the elastic rest API), 
and how Elastic deals with that value. 

Reading the Spring elasticsearch documentation, we know about the existence of a Meta Model Object Mapping, used for the following reasons:

> Earlier versions of Spring Data Elasticsearch used a Jackson based conversion, Spring Data Elasticsearch 3.2.x introduced the Meta Model Object Mapping. As of version 4.0 only the Meta Object Mapping is used, the Jackson based mapper is not available anymore and the MappingElasticsearchConverter is used.
  The main reasons for the removal of the Jackson based mapper are:  
  Custom mappings of fields needed to be done with annotations like @JsonFormat or @JsonInclude. This often caused problems when the same object was used in different JSON based datastores or sent over a JSON based API.
  Custom field types and formats also need to be stored into the Elasticsearch index mappings. The Jackson based annotations did not fully provide all the information that is necessary to represent the types of Elasticsearch.
  Fields must be mapped not only when converting from and to entities, but also in query argument, returned data and on other places.
  Using the MappingElasticsearchConverter now covers all these cases.

As you can read, it gives us the opportunity to have custom mappings using MappingElasticsearchConverters. 

If we don't do any conversion, and send the POJO as is, Elastic will store the Person class with this index mapping:

```json
{
"birthDate": {
          "type": "long"
        },
        "firstName": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "id": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "lastName": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
              }
          }
        }
}
```


birthDate as a long value. That doesn't look good. Checking it using Discover in Kibana shows this data:

```
id:0aba499a-85f7-4ef0-879a-7187c770d938 
firstName:John 
lastName:Wick 
birthDate:-7,911,000,000 
_id:0aba499a-85f7-4ef0-879a-7187c770d938 
_type:_doc 
_index:persons
```

Hmm, birthDate is absolutely not looking correct.

If we read data from Elastic, the mapper will throw an exception, trying to convert the long value to a LocalDateTime.

```
org.springframework.core.convert.ConverterNotFoundException: No converter found capable of converting from type [java.lang.Long] to type [java.time.LocalDateTime]
```

So to fix this, we'll have to register a custom converter. We can see that date formats are supported by Elastic [here](https://www.elastic.co/guide/en/elasticsearch/reference/current/date.html)

Overriding the elasticserachCustomConversions() from the previous class will enable it:


```java
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
```

The ElasticsearchCustomConversions class takes a Collection of converters as the constructor parameter. The two converters that we need to add are a WritingConverter (from our POJO to the Elasticsearch), and a ReadingConverter
(from Elasticsearch to our POJO class). 

For our project, we're storing the dates in ISO format without a 'Z' character at the end, with a T without any space around it: (YYYY-MM-DDTHH:MM:SS). 
The format that Elasticsearch returns for us can be perfectly stored as a LocalDateTime object.  

Now the index mapping in Elasticsearch is correct:

```json
{
"birthDate": {
          "type": "date"
        }
}
```

Kibana shows everything as it should be (even ask you if the birthdate can be used as a time field)

```
id:9d4451fc-1af4-4262-a000-0b57b15af926 
firstName:Antonio 
lastName:Banderas 
birthDate:Oct 2, 1980 @ 13:30:00.000 
_id:9d4451fc-1af4-4262-a000-0b57b15af926 _type:_doc 
_index:persons
```

Using the rest endpoint, asking for the first name "Antonio" returns a correct response:

```json
{
"id":"9d4451fc-1af4-4262-a000-0b57b15af926",
"firstName":"Antonio",
"lastName":"Banderas",
"birthDate":"1980-10-02T12:30:00"
}
```

### Note:
We didn't use any DTO, so what is shown is the Domain Object as is, with the default mapping that Spring uses to build the reply using Jackson.

## Service

We set a very simple service layer, creating this interface:


```java
public interface PersonService {

    Flux<Person> getAllPersons();
    Mono<Person> getPersonByName(String firstName);
}
```

Its implementation:

```java
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
```

## Routes

Spring webflux gives us the possibility to use annotations to create controllers like in Spring MVC. For this project, we'll use the [Functional Endpoints](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#webflux-fn) approach, 
that allow us to have more control over the routing. 

To keep the same structure as we do with Spring MVC, we store the classes for routing in a controller package.  

Inside, we create two classes, a PersonRoute class, that sets the two endpoints that our application will offer, and a PersonHandler class, with the corresponding Handlers for each endpoint. 

**PersonRoute**

```java
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
```

**PersonHandler**

```java
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
```

Very straight forward. We return responses from the Service using methods available in the ServerResponse class, returning an HTTP 200 (ServerResponse.ok()), and for the body we encapsulate the service responses (the flux and mono objects).

## Bootstrapping our application

To load a few values to our application to check the endpoints, we add a "bootstrap" package with a simple command-line runner.

```java
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
```

## Integration test using Testcontainers

Ok, now we've finished describing the parts of our project, let's start testing the Repository (the main purpose of this article).

We'll use for our tests the Elasticsearch version 7.6.2 and Junit 5.

### What is Testcontainers?

[Testcontainers](https://www.testcontainers.org/) is, like we can see on their website:

>Testcontainers is a Java library that supports JUnit tests, providing lightweight, throwaway instances of common databases, Selenium web browsers, or anything else that can run in a Docker container.
>Testcontainers make the following kinds of tests easier:
>Data access layer integration tests: use a containerized instance of a MySQL, PostgreSQL or Oracle database to test your data access layer code for complete compatibility, but without requiring complex setup on developers' machines and safe in the knowledge that your tests will always start with a known DB state. Any other database type that can be containerized can also be used.
Application integration tests: for running your application in a short-lived test mode with dependencies, such as databases, message queues or web servers.
>UI/Acceptance tests: use containerized web browsers, compatible with Selenium, for conducting automated UI tests. Each test can get a fresh instance of the browser, with no browser state, plugin variations or automated browser upgrades to worry about. And you get a video recording of each test session, or just each session where tests failed.

"Throwaway instances of common databases". Great! This is looking promising. You can see there Kafka, RabbitMQ, Nginx, Cassandra, InfluxDB... So learning about it will give tools for any other data server you use in the future. 

### Testcontainers in our Spring application using Junit 5. 

Here's the class we're going to create for testing (just two simple tests);

```java
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
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Log4j2 // To just help us with logs
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
```

To define our class, we'll use these annotations:

```java
@Log4j2 // To just help us
@SpringBootTest
@ContextConfiguration(initializers = {ElasticSearchRepositoryIT.Initializer.class})
public class ElasticSearchRepositoryIT 
```

@Log4j2 from Lombok to use Log4j inside our class. 

@SpringBootTest:
We need to configure the client connection to the elasticsearch container from testcontainers, so using the full Spring Context to load some other configuration is necessary.
 
@ContextConfiguration(initializers = {ElasticSearchRepositoryIT.Initializer.class}):

This loads an initializer that is **mandatory** to configure the connection to the container. I will comment on this issue later.

### TestContainers configuration for our tests.

First, let's configure two constants:

```java
private static final String                 ELASTIC_VERSION            = "7.6.2";
private static final String                 DOCKER_ELASTIC_URL_OFICIAL = "docker.elastic.co/elasticsearch/elasticsearch:";
```

First one will set the version for elasticsearch. 
The second, the [link for the docker containers, official from elasticsearch](https://www.docker.elastic.co/). 

Testcontainers needs this complete URL in order to download and use the docker image. 
 
```java
@BeforeAll
    static void setUp() {

        elasticsearchContainer = new ElasticsearchContainer(DOCKER_ELASTIC_URL_OFICIAL + ELASTIC_VERSION)
                .withExposedPorts(9200, 9300);

        elasticsearchContainer.start();

    }

```

Next, we'll add a method that will be executed before everything. This will create the TestContainers object and start the container. Everything will be done even before loading the Spring context. 
On the first run, TestContainers will download the docker image, so depending on your internet connection could take some time (the Elasticsearch docker takes around 400-500Mb)

After that, you could see at the end some lines like these:
```
19:28:47.593 [main] DEBUG org.testcontainers.dockerclient.auth.AuthDelegatingDockerClientConfig - Effective auth config [null]
19:28:47.601 [main] DEBUG com.github.dockerjava.core.command.AbstrDockerCmd - Cmd: com.github.dockerjava.core.command.CreateContainerCmdImpl@7a93b263[name=<null>,hostName=<null>,domainName=<null>,user=<null>,attachStdin=<null>,attachStdout=<null>,attachStderr=<null>,portSpecs=<null>,tty=<null>,stdinOpen=<null>,stdInOnce=<null>,env={discovery.type=single-node},cmd={},healthcheck=<null>,argsEscaped=<null>,entrypoint=<null>,image=docker.elastic.co/elasticsearch/elasticsearch:7.6.2,volumes=com.github.dockerjava.api.model.Volumes@35178483,workingDir=<null>,macAddress=<null>,onBuild=<null>,networkDisabled=<null>,exposedPorts=com.github.dockerjava.api.model.ExposedPorts@bd1111a,stopSignal=<null>,stopTimeout=<null>,hostConfig=HostConfig(binds=[], blkioWeight=null, blkioWeightDevice=null, blkioDeviceReadBps=null, blkioDeviceWriteBps=null, blkioDeviceReadIOps=null, blkioDeviceWriteIOps=null, memorySwappiness=null, nanoCPUs=null, capAdd=null, capDrop=null, containerIDFile=null, cpuPeriod=null, cpuRealtimePeriod=null, cpuRealtimeRuntime=null, cpuShares=null, cpuQuota=null, cpusetCpus=null, cpusetMems=null, devices=null, deviceCgroupRules=null, deviceRequests=null, diskQuota=null, dns=null, dnsOptions=null, dnsSearch=null, extraHosts=[], groupAdd=null, ipcMode=null, cgroup=null, links=[], logConfig=com.github.dockerjava.api.model.LogConfig@1a480135, lxcConf=null, memory=null, memorySwap=null, memoryReservation=null, kernelMemory=null, networkMode=null, oomKillDisable=null, init=null, autoRemove=null, oomScoreAdj=null, portBindings={}, privileged=null, publishAllPorts=true, readonlyRootfs=null, restartPolicy=null, ulimits=null, cpuCount=null, cpuPercent=null, ioMaximumIOps=null, ioMaximumBandwidth=null, volumesFrom=[], mounts=null, pidMode=null, isolation=null, securityOpts=null, storageOpt=null, cgroupParent=null, volumeDriver=null, shmSize=null, pidsLimit=null, runtime=null, tmpFs=null, utSMode=null, usernsMode=null, sysctls=null, consoleSize=null),labels={org.testcontainers=true, org.testcontainers.sessionId=2449fc23-0a5e-4647-98a0-88226d24fd58},shell=<null>,networkingConfig=<null>,ipv4Address=<null>,ipv6Address=<null>,aliases=<null>,authConfig=<null>]
19:28:47.642 [main] INFO 🐳 [docker.elastic.co/elasticsearch/elasticsearch:7.6.2] - Starting container with ID: ecd5f7125f8b02b26111af28b3d4842f3014a85508d9dff107fcee1b2b276fdb
19:28:47.643 [main] DEBUG com.github.dockerjava.core.command.AbstrDockerCmd - Cmd: ecd5f7125f8b02b26111af28b3d4842f3014a85508d9dff107fcee1b2b276fdb
19:28:48.254 [main] INFO 🐳 [docker.elastic.co/elasticsearch/elasticsearch:7.6.2] - Container docker.elastic.co/elasticsearch/elasticsearch:7.6.2 is starting: ecd5f7125f8b02b26111af28b3d4842f3014a85508d9dff107fcee1b2b276fdb
19:28:48.255 [main] DEBUG com.github.dockerjava.core.command.AbstrDockerCmd - Cmd: ecd5f7125f8b02b26111af28b3d4842f3014a85508d9dff107fcee1b2b276fdb,false
19:28:48.256 [main] DEBUG com.github.dockerjava.core.exec.InspectContainerCmdExec - GET: com.github.dockerjava.okhttp.OkHttpWebTarget@745c2004
19:28:48.260 [ducttape-0] DEBUG com.github.dockerjava.core.command.AbstrDockerCmd - Cmd: ecd5f7125f8b02b26111af28b3d4842f3014a85508d9dff107fcee1b2b276fdb,false
19:28:48.260 [ducttape-0] DEBUG com.github.dockerjava.core.exec.InspectContainerCmdExec - GET: com.github.dockerjava.okhttp.OkHttpWebTarget@594f36e
19:28:48.266 [main] INFO org.testcontainers.containers.wait.strategy.HttpWaitStrategy - /eloquent_shannon: Waiting for 120 seconds for URL: http://localhost:32770/
19:29:04.492 [main] INFO 🐳 [docker.elastic.co/elasticsearch/elasticsearch:7.6.2] - Container docker.elastic.co/elasticsearch/elasticsearch:7.6.2 started in PT16.900811S
19:29:04.528 [main] DEBUG org.springframework.test.context.support.DependencyInjectionTestExecutionListener - Performing dependency injection for test context [[DefaultTestContext@2250b9f2 testClass = ElasticSearchRepositoryIT, testInstance = com.davidranndo.reactorcontainers.repository.ElasticSearchRepositoryIT@2b917fb0, testMethod = [null], testException = [null], mergedContextConfiguration = [ReactiveWebMergedContextConfiguration@7e3181aa testClass = ElasticSearchRepositoryIT, locations = '{}', classes = '{class com.davidranndo.reactorcontainers.ReactorcontainersApplication}', contextInitializerClasses = '[class com.davidranndo.reactorcontainers.repository.ElasticSearchRepositoryIT$Initializer]', activeProfiles = '{}', propertySourceLocations = '{}', propertySourceProperties = '{org.springframework.boot.test.context.SpringBootTestContextBootstrapper=true}', contextCustomizers = set[org.springframework.boot.test.context.filter.ExcludeFilterContextCustomizer@e15b7e8, org.springframework.boot.test.json.DuplicateJsonObjectContextCustomizerFactory$DuplicateJsonObjectContextCustomizer@2ed2d9cb, org.springframework.boot.test.mock.mockito.MockitoContextCustomizer@0, org.springframework.boot.test.web.client.TestRestTemplateContextCustomizer@6404f418, org.springframework.boot.test.web.reactive.server.WebTestClientContextCustomizer@36916eb0, org.springframework.boot.test.autoconfigure.properties.PropertyMappingContextCustomizer@0, org.springframework.boot.test.autoconfigure.web.servlet.WebDriverContextCustomizerFactory$Customizer@506ae4d4, org.springframework.boot.test.context.SpringBootTestArgs@1], contextLoader = 'org.springframework.boot.test.context.SpringBootContextLoader', parent = [null]], attributes = map[[empty]]]].
19:29:04.692 [main] DEBUG org.springframework.test.context.support.TestPropertySourceUtils - Adding inlined properties to environment: {spring.jmx.enabled=false, org.springframework.boot.test.context.SpringBootTestContextBootstrapper=true}
```

You can see that elasticsearch is running on port 32770. Executing "docker ps" on the CLI:

```
ecd5f7125f8b        docker.elastic.co/elasticsearch/elasticsearch:7.6.2   "/usr/local/bin/dock…"   About a minute ago   Up 59 seconds       0.0.0.0:32770->9200/tcp, 0.0.0.0:32769->9300/tcp   eloquent_shannon
e39e438ddfff        quay.io/testcontainers/ryuk:0.2.3                     "/app"                   About a minute ago   Up About a minute   0.0.0.0:32768->8080/tcp                            testcontainers-ryuk-2449fc23-0a5e-4647-98a0-88226d24
```

Now let's configure to stop the container after all the tests finish.

```java
@AfterAll
    static void afterAll() {
        elasticsearchContainer.stop();
    }
```

We have the container ready, let's configure the client connection to it.  
There is a class that configures the ReactiveElasticsearchClient with the connection info on this SpringBoot application and will be used for the tests too, tweaking it a bit with the info returned by TestContainer. To achieve that, we will include an Initializer into the Context, using this annotation:

```java
@ContextConfiguration(initializers = {ElasticSearchRepositoryIT.Initializer.class})
```

"Initializer" is a nested class we created that implements ApplicationContextInitializer<ConfigurableApplicationContext>. Being nested is not mandatory, but we'll use it just for
our convenience, following the single responsibility rule. It can be used on an external class to be shared between tests.

```java
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
```

There we're overriding the value for the property: "spring.data.elasticsearch.client.reactive.endpoints", but this time, using the port mapped by TestContainers. 

### IMPORTANT

As we commented before, in our configuration class, we've configured the Elasticsearch client connection using a value from the application.properties file, so when you run your application, this property will be available for the whole Spring Context. 

In my tests, I've found that, if I configure the Spring app to use the HighLevelRestClient (and not use anything in the application.properties file), setting that value in the Initializer makes Highlevelrestclient use it in the SpringBootTest Context, instead of the connection settings from the configuration class. 

With the reactive client, the connection configuration will always be used from the configuration class, no matter what I set in the Initializer. So having that property set in the file and just change the value in the main configuration class or in the Initializer class works for our purposes.   

I don't know if it's a bug or something that is outside my knowledge. If you do know about it, any tip or help will be very appreciated.  

### Test execution

Now, finally, we can run our tests. We set some values on the @BeforeEach method, and create two simple tests to see if our repository stores and retrieves data correctly into and from our POJO.

Now we can easily set some service layer tests with no Mocks over the repository, or even integration tests against the endpoints using WebTestClient. 
 
This kind of tests **will take way more time** than running them with mocks, or the regular repository tests using @DataJpaTest (it needs to load the container, and then the Spring Context) but, at least in my opinion, is absolutely worth it.

I hope this article helps you if you're trying to figure out how to run all of this, and of course, if you see any mistake by my side, anything that can be improved, or you have any questions that I can help you with, you have the issues available here in Github, or you can do a pull request.

Thank you for reading this, and have a nice day. 
