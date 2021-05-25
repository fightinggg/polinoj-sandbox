package com.oj.polinojsandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@Slf4j
@EnableConfigurationProperties
@RestController
@SpringBootApplication
public class PolinojSandboxApplication implements CommandLineRunner {

    @Autowired
    private SandBoxProperties sandBoxProperties;


    @Autowired
    private KafkaSandboxCompoment kafkaSandboxCompoment;

    public static void main(String[] args) {
        SpringApplication.run(PolinojSandboxApplication.class, args);
    }


    @Override
    public void run(String... args) throws Exception {
        File f = new File(sandBoxProperties.getRunning());
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(sandBoxProperties.getRunning() + "/zipsamples/");
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(sandBoxProperties.getRunning() + "/samples/");
        if (!f.exists()) {
            f.mkdirs();
        }
    }
}
