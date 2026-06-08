package io.flowwarden.javers.test;

import org.testcontainers.containers.MongoDBContainer;

public final class SharedMongoContainer {

    public static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    static {
        MONGO.start();
        Runtime.getRuntime().addShutdownHook(new Thread(MONGO::stop));
    }

    private SharedMongoContainer() {}
}
