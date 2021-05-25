package com.oj.polinojsandbox;

import com.jlefebure.spring.boot.minio.MinioException;
import com.jlefebure.spring.boot.minio.MinioService;
import com.oj.polinojsandbox.openapi.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@EnableConfigurationProperties
@RestController
@SpringBootApplication
public class PolinojSandboxApplication implements CommandLineRunner {

    @Autowired
    private SandBoxProperties sandBoxProperties;


    @Autowired
    private SandboxQueue sandboxQueue;

    public static void main(String[] args) {
        SpringApplication.run(PolinojSandboxApplication.class, args);
    }

    @PostMapping
    SampleTestResponseDTO sampleTest(@RequestBody SampleTestRequestDTO sampleTestRequestDTO) {
        String id = UUID.randomUUID().toString();
        sandboxQueue.submit(sampleTestRequestDTO, id);
        SampleTestResponseDTO sampleTestResponseDTO = new SampleTestResponseDTO();
        sampleTestResponseDTO.setId(id);
        return sampleTestResponseDTO;
    }

    @GetMapping("/{id}")
    SampleTestResultDTO getStatus(@PathVariable String id) {
        return sandboxQueue.getStatus(id);
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
