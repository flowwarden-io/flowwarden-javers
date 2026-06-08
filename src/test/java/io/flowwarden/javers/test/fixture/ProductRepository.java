package io.flowwarden.javers.test.fixture;

import org.javers.spring.annotation.JaversSpringDataAuditable;
import org.springframework.data.mongodb.repository.MongoRepository;

@JaversSpringDataAuditable
public interface ProductRepository extends MongoRepository<Product, String> {
}
