package com.oj.polinojsandbox;

import com.google.common.collect.Lists;
import com.oj.polinojsandbox.openapi.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class SandboxQueue {

    @Autowired
    private SandBoxProperties sandBoxProperties;

    private Executor executor;

    private Map<String, SampleTestResultDTO> resultMap = new ConcurrentHashMap<>();

    private Map<String, Integer> status = new ConcurrentHashMap<>();

    @PostConstruct

    private void postConstruct() {
        executor = Executors.newFixedThreadPool(sandBoxProperties.getConcurrentTestSize());
    }

    SampleTestResultDTO getStatus(String id) {
        if (status.containsKey(id)) {
            final Integer testStatus = status.get(id);
            if (testStatus.equals(ProgramResultEnum.PENDING) || testStatus.equals(ProgramResultEnum.PROCESS)) {
                SampleTestResultDTO sampleTestResultDTO = new SampleTestResultDTO();
                sampleTestResultDTO.setSampleTestResults(new ArrayList<>());
                sampleTestResultDTO.setStatus(testStatus);
                sampleTestResultDTO.setSubmitId(id);
                return sampleTestResultDTO;
            } else {
                status.remove(id);
                return resultMap.remove(id);
            }
        } else {
            SampleTestResultDTO sampleTestResultDTO = new SampleTestResultDTO();
            sampleTestResultDTO.setSampleTestResults(new ArrayList<>());
            sampleTestResultDTO.setStatus(ProgramResultEnum.MISS);
            sampleTestResultDTO.setSubmitId(id);
            return sampleTestResultDTO;
        }
    }

    void submit(SampleTestRequestDTO sampleTestRequestDTO, String id) {
        status.put(id, ProgramResultEnum.PENDING);
        executor.execute(() -> {
            status.replace(id, ProgramResultEnum.PROCESS);
            final SampleTestResultDTO resultDTO = test(sampleTestRequestDTO, id);
            resultMap.put(id, resultDTO);
            status.replace(id, resultDTO.getStatus());
        });
    }


    private String cc(String src, String execDir, String execName, Long times) throws IOException {
        String[] cc = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/main.cpp", src),
                "-v", String.format("%s:/out", execDir),
                "gcc",
                "/bin/sh", "-c", String.format("g++ /main.cpp -o /out/%s", execName)
        };

        log.info("exec cc command: {}", Lists.newArrayList(cc));
        Process pro = Runtime.getRuntime().exec(cc);


        try {
            pro.waitFor(times, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.COMPILE_TIMEOUT);
        }

        String stdout = IOUtils.toString(pro.getInputStream());
        String errout = IOUtils.toString(pro.getErrorStream());
        return "stdout:\n" + stdout + "errout:\n" + errout;
    }

    private SampleTestResult run(String target, String sampleInput, String sampleOutput,
                                 String programOutputDir, String programOutputName,
                                 Long times) {
        SampleTestResult sampleTestResult = new SampleTestResult();

        // 运行
        String[] run = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/main", target),
                "-v", String.format("%s:/1.in", sampleInput),
                "-v", String.format("%s:/out", programOutputDir),
                "1144560553/polinoj-sandbox-cpp",
                "bash", "-c", String.format("/usr/bin/time -f %%e,%%S,%%U,%%x /main < /1.in > /out/%s && exit 0", programOutputName)
        };

        long beginTime = System.currentTimeMillis();
        try {
            log.info("exec run command: {}", Lists.newArrayList(run));
            Process pro = Runtime.getRuntime().exec(run);
            final boolean b = pro.waitFor(times, TimeUnit.SECONDS);
            if (!b) {
                sampleTestResult.setTimes((int) (System.currentTimeMillis() - beginTime));
                sampleTestResult.setReturnCode(ProgramResultEnum.TLE);
                return sampleTestResult;
            }
            String stdout = IOUtils.toString(pro.getInputStream());
            String errout = IOUtils.toString(pro.getErrorStream());
            log.info("user code with time stdout:{}", stdout);
            log.info("user code with time errout:{}", errout);

            if (pro.exitValue() != 0) {
                sampleTestResult.setTimes((int) (System.currentTimeMillis() - beginTime));
                sampleTestResult.setReturnCode(ProgramResultEnum.RE);
                return sampleTestResult;
            }
        } catch (InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        } catch (IOException e) {
            log.error("", e);
            sampleTestResult.setTimes((int) (System.currentTimeMillis() - beginTime));
            sampleTestResult.setReturnCode(ProgramResultEnum.RE);
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
        Process pro;
        try {
            log.info("exec diff command: {}", Lists.newArrayList(check));
            pro = Runtime.getRuntime().exec(check);
            pro.waitFor();
            IOUtils.copy(pro.getInputStream(), System.out);
            IOUtils.copy(pro.getErrorStream(), System.out);
        } catch (IOException | InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        }
        if (pro.exitValue() != 0) {
            sampleTestResult.setReturnCode(ProgramResultEnum.WA);
        } else {
            sampleTestResult.setReturnCode(ProgramResultEnum.AC);
        }
        return sampleTestResult;
    }


    private SampleTestResultDTO test(SampleTestRequestDTO sampleTestRequestDTO, String id) {
        SampleTestResultDTO sampleTestResultDTO = new SampleTestResultDTO();

        String workspace = sandBoxProperties.getRunning() + "/" + id;
        File workspaceFile = new File(workspace);
        try {
            workspaceFile.mkdirs();
            workspace = workspaceFile.getAbsolutePath();

            String srcFileName = workspace + "/main.cpp";
            File srcFile = new File(srcFileName);
            srcFile.createNewFile();
            new FileOutputStream(srcFile).write(sampleTestRequestDTO.getCode().getBytes());
            String targetFileDir = workspace + "/target";
            String targetFileName = "main";

            String ccInfo = cc(srcFileName, targetFileDir, targetFileName, sampleTestRequestDTO.getCcTimes());
            sampleTestResultDTO.setCcInfo(ccInfo);

            String samplePath = sandBoxProperties.getRunning() + "/samples/" + sampleTestRequestDTO.getProblemId();
            File sampleFiles = new File(samplePath);
            String[] files = sampleFiles.list();
            List<String> fileList = Lists.newArrayList(files == null ? new String[0] : files);
            fileList.sort(String::compareTo);

            sampleTestResultDTO.setStatus(ProgramResultEnum.AC);
            List<SampleTestResult> results = new ArrayList<>();
            for (int i = 0; i < fileList.size(); i += 2) {
                String stdin = fileList.get(i);
                String stdout = fileList.get(i + 1);
                final SampleTestResult run = run(targetFileDir + "/" + targetFileName,
                        sampleFiles.getAbsolutePath() + "/" + stdin,
                        sampleFiles.getAbsolutePath() + "/" + stdout,
                        workspace + "/" + stdin.substring(0, stdin.length() - 3),
                        "out.txt", sampleTestRequestDTO.getRunTimes()
                );
                results.add(run);
                if (!run.getReturnCode().equals(ProgramResultEnum.AC)) {
                    sampleTestResultDTO.setStatus(run.getReturnCode());
                    break;
                }
            }
            sampleTestResultDTO.setSampleTestResults(results);


            return sampleTestResultDTO;
        } catch (IOException e) {
            e.printStackTrace();
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        } finally {
            workspaceFile.delete();
        }
    }

}
