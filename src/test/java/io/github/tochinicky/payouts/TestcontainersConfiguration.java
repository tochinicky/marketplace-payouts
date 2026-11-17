package io.github.tochinicky.payouts;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	@Bean
	GenericContainer<?> stripeMockContainer() {
		return new GenericContainer<>(DockerImageName.parse("stripe/stripe-mock:latest")).withExposedPorts(12111);
	}

	@Bean
	DynamicPropertyRegistrar stripeMockProperties(GenericContainer<?> stripeMockContainer) {
		return registry -> registry.add("stripe.api-base",
				() -> "http://" + stripeMockContainer.getHost() + ":" + stripeMockContainer.getMappedPort(12111));
	}

}
