package com.oj.polinojsandbox;

import com.google.common.collect.Lists;
import com.jlefebure.spring.boot.minio.MinioException;
import com.jlefebure.spring.boot.minio.MinioService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@EnableConfigurationProperties
@RestController
@SpringBootApplication
public class PolinojSandboxApplication implements CommandLineRunner {

    @Autowired
    private SandBoxProperties sandBoxProperties;

    @Autowired
    private MinioService minioService;


    public void cc(String src, String execDir, String execName, int times) throws IOException {
        String[] cc = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/main.cpp", src),
                "-v", String.format("%s:/out", execDir),
                "gcc",
                "/bin/sh", "-c", String.format("g++ /main.cpp -o /out/%s", execName)
        };

        Process pro = Runtime.getRuntime().exec(cc);


        try {
            pro.waitFor(times, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.COMPILE_TIMEOUT);
        }

        IOUtils.copy(pro.getInputStream(), System.out);
        IOUtils.copy(pro.getErrorStream(), System.out);
    }

    public SampleTestResult run(String target, String sampleInput, String sampleOutput,
                                       String programOutputDir, String programOutputName,
                                       int times) {
        SampleTestResult sampleTestResult = new SampleTestResult();

        // 运行
        String[] run = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/main", target),
                "-v", String.format("%s:/1.in", sampleInput),
                "-v", String.format("%s:/out", programOutputDir),
                "gcc",
                "/bin/sh", "-c", String.format("/main < /1.in > /out/%s && exit $?", programOutputName)
        };

        long beginTime = System.currentTimeMillis();
        try {
            Process pro = Runtime.getRuntime().exec(run);
            final boolean b = pro.waitFor(times, TimeUnit.SECONDS);
            if (!b) {
                sampleTestResult.setTimes((int) (System.currentTimeMillis() - beginTime));
                sampleTestResult.setReturnCode(ProgramResult.TLE);
                return sampleTestResult;
            }
            IOUtils.copy(pro.getInputStream(), System.out);
            IOUtils.copy(pro.getErrorStream(), System.out);

            if (pro.exitValue() != 0) {
                sampleTestResult.setTimes((int) (System.currentTimeMillis() - beginTime));
                sampleTestResult.setReturnCode(ProgramResult.RE);
                return sampleTestResult;
            }
        } catch (InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        } catch (IOException e) {
            sampleTestResult.setTimes((int) (System.currentTimeMillis() - beginTime));
            sampleTestResult.setReturnCode(ProgramResult.RE);
            return sampleTestResult;
        }
        sampleTestResult.setTimes((int) (System.currentTimeMillis() - beginTime));


        // 评测
        String[] check = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/1.out", sampleOutput),
                "-v", String.format("%s/%s:/2.out", programOutputDir, programOutputName),
                "gcc",
                "/bin/sh", "-c", "diff /1.out /2.out"
        };
        Process pro = null;
        try {
            pro = Runtime.getRuntime().exec(check);
            pro.waitFor();
            IOUtils.copy(pro.getInputStream(), System.out);
            IOUtils.copy(pro.getErrorStream(), System.out);
        } catch (IOException | InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        }
        if (pro.exitValue() != 0) {
            sampleTestResult.setReturnCode(ProgramResult.WA);
        } else {
            sampleTestResult.setReturnCode(ProgramResult.AC);
        }
        return sampleTestResult;
    }


    public List<SampleTestResult> test(SampleTestDTO sampleTestDTO) {

        String workspace = sandBoxProperties.getRunning() + "/" + sampleTestDTO.getId();
        File workspaceFile = new File(workspace);
        try {
            workspaceFile.mkdirs();
            workspace = workspaceFile.getAbsolutePath();

            String srcFileName = workspace + "/main.cpp";
            File srcFile = new File(srcFileName);
            srcFile.createNewFile();
            new FileOutputStream(srcFile).write(sampleTestDTO.getCode().getBytes());
            String targetFileDir = workspace + "/target";
            String targetFileName = "main";
            cc(srcFileName, targetFileDir, targetFileName, sampleTestDTO.getCcTimes());

            String samplePath = sandBoxProperties.getRunning() + "/samples/" + sampleTestDTO.getProblemId();
            File sampleFiles = new File(samplePath);
            String[] files = sampleFiles.list();
            List<String> fileList = Lists.newArrayList(files == null ? new String[0] : files);
            fileList.sort(String::compareTo);

            List<SampleTestResult> results = new ArrayList<>();
            for (int i = 0; i < fileList.size(); i += 2) {
                String stdin = fileList.get(i);
                String stdout = fileList.get(i + 1);
                final SampleTestResult run = run(targetFileDir + "/" + targetFileName,
                        sampleFiles.getAbsolutePath() + "/" + stdin,
                        sampleFiles.getAbsolutePath() + "/" + stdout,
                        workspace + "/" + stdin.substring(0, stdin.length() - 3),
                        "out.txt", sampleTestDTO.getRunTimes()
                );
                results.add(run);
            }
            return results;
        } catch (IOException e) {
            e.printStackTrace();
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        } finally {
            workspaceFile.delete();
        }
    }

    public static void main(String[] args) throws IOException {

        String workspace = "/Users/s/Desktop/tmp";

        String code = "#include<bits/stdc++.h>\n" +
                "using namespace std;\n" +
                "\n" +
                "int main(){\n" +
                "  int a,b;\n" +
                "  cin>>a>>b;\n" +
                "  cout<<(a+b)<<endl;\n" +
                "}\n";

        final byte[] encode = Base64.getEncoder().encode(code.getBytes());
        System.out.println(new String(encode));

        SpringApplication.run(PolinojSandboxApplication.class, args);

    }

    @PostMapping
    List<SampleTestResult> postMap(@RequestBody SampleTestDTO sampleTestDTO) {
        byte[] code = Base64.getDecoder().decode(sampleTestDTO.getCode());
        sampleTestDTO.setCode(new String(code));

        String savePath = sandBoxProperties.getRunning() + "/samples/" + sampleTestDTO.getProblemId();

        boolean update = true;
        File md5 = new File(savePath + "/.md5");
        if (md5.exists()) {
            try {
                final String md5Code = FileUtils.readFileToString(md5);
                if (md5Code.equals(sampleTestDTO.getSamplesMD5())) {
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
            String saveZipPath = sandBoxProperties.getRunning() + "/zipsamples/" + sampleTestDTO.getProblemId() + ".zip";
            File saveZipPathFile = new File(saveZipPath);
            if (saveZipPathFile.exists()) {
                savePathFile.delete();
            }
            try {
                minioService.getAndSave(Paths.get(sampleTestDTO.getCosPath()), saveZipPath);
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

        return test(sampleTestDTO);
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
