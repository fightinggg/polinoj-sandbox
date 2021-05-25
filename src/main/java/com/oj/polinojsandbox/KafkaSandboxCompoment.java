package com.oj.polinojsandbox;

import com.google.common.collect.Lists;
import com.jlefebure.spring.boot.minio.MinioException;
import com.jlefebure.spring.boot.minio.MinioService;
import com.oj.polinojsandbox.openapi.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


@Slf4j
@Component
public class KafkaSandboxCompoment {

    @Autowired
    private SandBoxProperties sandBoxProperties;

    @Autowired
    private MinioService minioService;

    @Autowired
    private KafkaTemplate<Object, Object> template;


    @KafkaListener(id = KafkaTopicConsts.submitCodeGroup, topics = KafkaTopicConsts.submitCodeTopic)
    void submit(SubmitCodeMessage submitCodeMessage) {
        log.info("listenSubmit:{}", submitCodeMessage);
        Long id = submitCodeMessage.getSubmitId();
        byte[] code = Base64.getDecoder().decode(submitCodeMessage.getCode());
        submitCodeMessage.setCode(new String(code));

        String savePath = sandBoxProperties.getRunning() + "/samples/" + submitCodeMessage.getProblemId();

        boolean update = true;
        File md5 = new File(savePath + "/.md5");
        if (md5.exists()) {
            try {
                final String md5Code = FileUtils.readFileToString(md5);
                if (md5Code.equals(submitCodeMessage.getSamplesMD5())) {
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


            String saveZipPath = sandBoxProperties.getRunning() + "/zipsamples/" + submitCodeMessage.getProblemId() + ".zip";
            File saveZipPathFile = new File(saveZipPath);
            if (saveZipPathFile.exists()) {
                saveZipPathFile.delete();
            }
            try {
                minioService.getAndSave(Paths.get(submitCodeMessage.getCosPath()), saveZipPath);
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


        String workspace = sandBoxProperties.getRunning() + "/" + id;
        File workspaceFile = new File(workspace);
        workspaceFile.mkdirs();
        workspace = workspaceFile.getAbsolutePath();


        String srcFileName = workspace + "/main.cpp";
        File srcFile = new File(srcFileName);
        try {
            srcFile.createNewFile();
            new FileOutputStream(srcFile).write(submitCodeMessage.getCode().getBytes());
        } catch (IOException e) {
            log.error("创建源代码文件失败 ", e);
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        }

        String targetFileDir = workspace + "/target";
        String targetFileName = "main";

        if (!cc(srcFileName, targetFileDir, targetFileName, submitCodeMessage.getCcTimes(), submitCodeMessage.getSubmitId())) {
            FinalCodeMessage finalCodeMessage = new FinalCodeMessage();
            finalCodeMessage.setSubmitId(submitCodeMessage.getSubmitId());
            finalCodeMessage.setStatus(ProgramResultEnum.CE);
            template.send(KafkaTopicConsts.finalCodeTopic, finalCodeMessage);
        }

        String samplePath = sandBoxProperties.getRunning() + "/samples/" + submitCodeMessage.getProblemId();
        File sampleFiles = new File(samplePath);
        String[] files = sampleFiles.list();
        List<String> fileList = Lists.newArrayList(files == null ? new String[0] : files);
        fileList.sort(String::compareTo);


        FinalCodeMessage finalCodeMessage = new FinalCodeMessage();
        finalCodeMessage.setSubmitId(id);
        finalCodeMessage.setTimes(0L);
        // TODO memory
        finalCodeMessage.setMemory(0L);

        for (int i = 0; i < fileList.size(); i += 2) {
            String stdin = fileList.get(i);
            String stdout = fileList.get(i + 1);

            if (!run(targetFileDir + "/" + targetFileName,
                    sampleFiles.getAbsolutePath() + "/" + stdin,
                    sampleFiles.getAbsolutePath() + "/" + stdout,
                    workspace + "/" + stdin.substring(0, stdin.length() - 3),
                    "out.txt", submitCodeMessage.getRunTimes(), finalCodeMessage
            )) {
                return;
            }
        }

        finalCodeMessage.setStatus(ProgramResultEnum.AC);

        template.send(KafkaTopicConsts.finalCodeTopic, finalCodeMessage);
    }

    private String outputString(InputStream in) {
        try {
            return IOUtils.toString(in);
        } catch (IOException e) {
            log.error("", e);
            return e.getMessage();
        }
    }

    private boolean cc(String src, String execDir, String execName, Long times, Long id) {
        String ccContainer = id + "-cc";
        String[] cc = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/main.cpp", src),
                "-v", String.format("%s:/out", execDir),
                "--cpus", sandBoxProperties.getCcCpus(),
                "-m", sandBoxProperties.getCcMemoryMb() + "m", "--memory-swap=1024M",
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

        boolean timeout = false;
        try {
            timeout = !pro.waitFor((long) (times / Double.parseDouble(sandBoxProperties.getCcCpus())), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        }

        if (timeout) {
            CCCodeMessage ccCodeMessage = new CCCodeMessage();
            ccCodeMessage.setCcInfo("编译超时");
            ccCodeMessage.setSubmitId(id);
            //TODO ccTime
            ccCodeMessage.setCcTime(null);
            template.send(KafkaTopicConsts.ccCodeTopic, ccCodeMessage);
            try {
                log.info("cc timeout , delete container {}", ccContainer);
                Runtime.getRuntime().exec("docker rm -f " + ccContainer).waitFor();
            } catch (IOException | InterruptedException e) {
                log.info("delete container error", e);
            }
            return false;
        }


        String stdout = outputString(pro.getInputStream());
        String errout = outputString(pro.getErrorStream());
        CCCodeMessage ccCodeMessage = new CCCodeMessage();
        ccCodeMessage.setCcInfo("stdout:\n" + stdout + "errout:\n" + errout);
        ccCodeMessage.setSubmitId(id);
        //TODO ccTime
        ccCodeMessage.setCcTime(null);
        template.send(KafkaTopicConsts.ccCodeTopic, ccCodeMessage);

        return pro.exitValue() == 0;
    }


    private void runSuccess(Long id, Integer times) {
        RunCodeMessage runCodeMessage = new RunCodeMessage();
        runCodeMessage.setTimes(times);
        runCodeMessage.setSubmitId(id);
        runCodeMessage.setReturnCode(ProgramResultEnum.AC);
        template.send(KafkaTopicConsts.runCodeTopic, runCodeMessage);
    }

    private void runfailed(Long id, Integer status, Integer times) {
        RunCodeMessage runCodeMessage = new RunCodeMessage();
        runCodeMessage.setTimes(times);
        runCodeMessage.setSubmitId(id);
        runCodeMessage.setReturnCode(status);
        template.send(KafkaTopicConsts.runCodeTopic, runCodeMessage);

        FinalCodeMessage finalCodeMessage = new FinalCodeMessage();
        finalCodeMessage.setSubmitId(id);
        finalCodeMessage.setStatus(status);
        template.send(KafkaTopicConsts.finalCodeTopic, finalCodeMessage);
    }

    private boolean run(String target, String sampleInput, String sampleOutput,
                        String programOutputDir, String programOutputName,
                        Long times, FinalCodeMessage finalCodeMessage) {
        Long id = finalCodeMessage.getSubmitId();
        String runContainer = id + "-run";

        // 运行
        String[] run = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/main", target),
                "-v", String.format("%s:/1.in", sampleInput),
                "-v", String.format("%s:/out", programOutputDir),
                "--cpus", sandBoxProperties.getRunCpus(),
                "--name", runContainer,
                "1144560553/polinoj-sandbox-cpp",
                "bash", "-c", String.format("/usr/bin/time -f %%e,%%S,%%U,%%x /main < /1.in > /out/%s && exit 0", programOutputName)
        };

        long beginTime = System.currentTimeMillis();


        log.info("exec run command: {}", Lists.newArrayList(run));
        Process pro = null;
        try {
            pro = Runtime.getRuntime().exec(run);
        } catch (IOException e) {
            log.error("", e);
            runfailed(id, ProgramResultEnum.RE, (int) (System.currentTimeMillis() - beginTime));
            return false;
        }

        boolean timeout;
        try {
            timeout = !pro.waitFor(3 + (long) (times * 3 / Double.parseDouble(sandBoxProperties.getRunCpus())), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw SandBoxException.buildException(SandBoxErrorCode.UNKNOW_ERROR);
        }

        if (timeout) {
            runfailed(id, ProgramResultEnum.TLE, (int) (times * 1000));
            try {
                Runtime.getRuntime().exec("docker rm -f " + runContainer).waitFor();
            } catch (InterruptedException | IOException e) {
                log.error("结束超时进程失败", e);
            }
            return false;
        }


        String stdout = outputString(pro.getInputStream());
        String errout = outputString(pro.getErrorStream());
        log.info("user code with time stdout:{}", stdout);
        log.info("user code with time errout:{}", errout);
        String[] errorLine = errout.split("\n");
        String[] runInfo = errorLine[errorLine.length - 1].split("[,\\s]");
        int exitValue = Integer.parseInt(runInfo[3]);
        int runTime = (int) (Double.parseDouble(runInfo[2]) * 1000);


        if (runTime > 1000 * times) {
            runfailed(id, ProgramResultEnum.TLE, runTime);
            return false;
        }

        if (exitValue != 0) {
            runfailed(id, ProgramResultEnum.RE, runTime);
            return false;
        }


        // 评测
        String[] check = {
                "docker", "run", "--rm",
                "-v", String.format("%s:/1.out", sampleOutput),
                "-v", String.format("%s/%s:/2.out", programOutputDir, programOutputName),
                "--cpus", sandBoxProperties.getCheckCpus(),
                "gcc",
                "/bin/sh", "-c", "diff /1.out /2.out"
        };

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
            runfailed(id, ProgramResultEnum.WA, runTime);
            return false;
        } else {
            runSuccess(id, runTime);
            Long newTime = Math.max(finalCodeMessage.getTimes(), runTime);
            finalCodeMessage.setTimes(newTime);
            return true;
        }
    }
}
