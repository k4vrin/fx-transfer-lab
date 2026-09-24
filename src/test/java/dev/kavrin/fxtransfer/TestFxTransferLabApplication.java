package dev.kavrin.fxtransfer;

import org.springframework.boot.SpringApplication;

public class TestFxTransferLabApplication {

	public static void main(String[] args) {
		SpringApplication.from(FxTransferLabApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
