package com.oj.polinojsandbox;

import com.google.common.collect.Lists;
import com.oj.polinojsandbox.openapi.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.*;


@Slf4j
@Component
public class SandboxQueue {

    @Data
    static class DeleteQueueItem {
        private String id;
        private Long time;
    }


    @Autowired
    private SandBoxProperties sandBoxProperties;

    private ExecutorService executor;

    private Queue<DeleteQueueItem> delayQueue = new LinkedBlockingQueue<>();

    private Map<String, SampleTestResultDTO> resultMap = new ConcurrentHashMap<>();

    private Map<String, Integer> status = new ConcurrentHashMap<>();


    @PostConstruct
    private void postConstruct() {
        // 增加一个删除的线程
        executor = Executors.newFixedThreadPool(sandBoxProperties.getConcurrentTestSize() + 1);

        executor.submit(() -> {
            while (true) {
                // 队空 或者 队首没有过期 就 睡眠1秒
                if (delayQueue.isEmpty() || delayQueue.peek().getTime() > System.currentTimeMillis()) {
                    Thread.sleep(1000);
                } else {
                    String id = delayQueue.poll().getId();
                    status.remove(id);
                    resultMap.remove(id);
                }
            }
        });
    }

    @PreDestroy
    private void destroy() {
        executor.shutdownNow();
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
                final DeleteQueueItem deleteQueueItem = new DeleteQueueItem();
                deleteQueueItem.setId(id);
                deleteQueueItem.setTime(System.currentTimeMillis() + 1000 * 600);
                delayQueue.add(deleteQueueItem);
                return resultMap.get(id);

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

    private String outputString(InputStream in) {
        try {
            return IOUtils.toString(in);
        } catch (IOException e) {
            log.error("", e);
            return e.getMessage();
        }
    }

    private boolean cc(String src, String execDir, String execName, Long times,
                       SampleTestResultDTO sampleTestResultDTO) {
        String ccContainer = Base64Utils.encodeToString(sampleTestResultDTO.getSubmitId().getBytes()) + "-cc";
        String[] cc = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/main.cpp", src),
                "-v", String.format("%s:/out", execDir),
                "--cpus", sandBoxProperties.getCcCpus(),
                "--name", ccContainer,
                "gcc",
                "/bin/sh", "-c", String.format("g++ /main.cpp -o /out/%s", execName)
        };

        log.info("exec cc command: {}", Lists.newArrayList(cc));

        Process pro = null;
        try {
            pro = Runtime.getRuntime().exec(cc);
        } catch (IOException e) {
            log.error("cc error: ", e);
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        }

        try {
            if (!pro.waitFor((long) (times / Double.parseDouble(sandBoxProperties.getCcCpus())), TimeUnit.SECONDS)) {
                sampleTestResultDTO.setStatus(ProgramResultEnum.CE);
                sampleTestResultDTO.setCcInfo("编译超时");
                try {
                    log.info("cc timeout , delete container {}", ccContainer);
                    Runtime.getRuntime().exec("docker rm -f " + ccContainer).waitFor();
                } catch (IOException e) {
                    log.info("delete container error", e);
                }
                return false;
            }
        } catch (InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        }


        String stdout = outputString(pro.getInputStream());
        String errout = outputString(pro.getErrorStream());
        sampleTestResultDTO.setCcInfo("stdout:\n" + stdout + "errout:\n" + errout);
        if (pro.exitValue() != 0) {
            sampleTestResultDTO.setStatus(ProgramResultEnum.CE);
            return false;
        } else {
            return true;
        }
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
                "--cpus", sandBoxProperties.getRunCpus(),
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
                "--cpus", sandBoxProperties.getCheckCpus(),
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
        sampleTestResultDTO.setSubmitId(id);

        String workspace = sandBoxProperties.getRunning() + "/" + id;
        File workspaceFile = new File(workspace);
        workspaceFile.mkdirs();
        workspace = workspaceFile.getAbsolutePath();


        String srcFileName = workspace + "/main.cpp";
        File srcFile = new File(srcFileName);
        try {
            srcFile.createNewFile();
            new FileOutputStream(srcFile).write(sampleTestRequestDTO.getCode().getBytes());
        } catch (IOException e) {
            log.error("创建源代码文件失败 ", e);
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        }

        String targetFileDir = workspace + "/target";
        String targetFileName = "main";

        if (!cc(srcFileName, targetFileDir, targetFileName, sampleTestRequestDTO.getCcTimes(), sampleTestResultDTO)) {
            sampleTestResultDTO.setSampleTestResults(new ArrayList<>());
            return sampleTestResultDTO;
        }

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

    }

}
