package io.github.tochinicky.payouts;

import org.springframework.boot.SpringApplication;

public class TestMarketplacePayoutsApplication {

	public static void main(String[] args) {
		SpringApplication.from(MarketplacePayoutsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
