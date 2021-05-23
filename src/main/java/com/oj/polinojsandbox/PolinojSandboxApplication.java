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
    private MinioService minioService;

    @Autowired
    private SandboxQueue sandboxQueue;

    public static void main(String[] args) {
        SpringApplication.run(PolinojSandboxApplication.class, args);
    }

    @PostMapping
    SampleTestResponseDTO sampleTest(@RequestBody SampleTestRequestDTO sampleTestRequestDTO) {
        byte[] code = Base64.getDecoder().decode(sampleTestRequestDTO.getCode());
        sampleTestRequestDTO.setCode(new String(code));

        String savePath = sandBoxProperties.getRunning() + "/samples/" + sampleTestRequestDTO.getProblemId();

        boolean update = true;
        File md5 = new File(savePath + "/.md5");
        if (md5.exists()) {
            try {
                final String md5Code = FileUtils.readFileToString(md5);
                if (md5Code.equals(sampleTestRequestDTO.getSamplesMD5())) {
                    update = false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (update) {
            File savePathFile = new File(savePath);
            if (savePathFile.exists()) {
                savePathFile.delete();
            }
            savePathFile.mkdirs();


            String saveZipPath = sandBoxProperties.getRunning() + "/zipsamples/" + sampleTestRequestDTO.getProblemId() + ".zip";
            File saveZipPathFile = new File(saveZipPath);
            if (saveZipPathFile.exists()) {
                saveZipPathFile.delete();
            }
            try {
                minioService.getAndSave(Paths.get(sampleTestRequestDTO.getCosPath()), saveZipPath);
                ZipFile zipFile = new ZipFile(saveZipPath);
                final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry zipEntry = entries.nextElement();

                    final InputStream inputStream = zipFile.getInputStream(zipEntry);
                    File output = new File(savePath + "/" + zipEntry.getName());
                    IOUtils.copy(inputStream, new FileOutputStream(output));
                }
            } catch (MinioException | IOException e) {
                e.printStackTrace();
            }
        }
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
