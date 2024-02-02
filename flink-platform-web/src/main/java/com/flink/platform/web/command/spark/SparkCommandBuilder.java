package com.flink.platform.web.command.spark;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.SparkJob;
import com.flink.platform.dao.service.ResourceService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.command.SqlContextHelper;
import com.flink.platform.web.external.YarnClientService;
import com.flink.platform.web.service.StorageService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import static com.flink.platform.common.constants.Constant.ROOT_DIR;
import static com.flink.platform.common.constants.Constant.SLASH;

import static java.util.stream.Collectors.toList;

@Slf4j
@Component("SparkCommandBuilder")
public class SparkCommandBuilder implements CommandBuilder {

    private static final String EXEC_MODE = " %s %s ";

    private static final String SUBMIT_PREFIX = "spark-submit --master yarn ";

    @Value("${flink.local.jar-dir}")
    private String jobJarDir; // 和flink公用一个文件夹

    @Resource(name = "sqlContextHelper")
    private SqlContextHelper sqlContextHelper;

    @Resource
    private StorageService storageService;

    @Resource
    private ResourceService resourceService;

    @Lazy
    @Resource
    private YarnClientService yarnClientService;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        System.out.println("jobType:" + jobType);
        System.out.println(JobType.SPARK);
        return jobType == JobType.SPARK;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRunInfo) throws Exception {
        SparkJob sparkJob = jobRunInfo.getConfig().unwrap(SparkJob.class);
        initExtJarPaths(sparkJob);
        DeployMode deployMode = jobRunInfo.getDeployMode(); // 部署方式
        SparkCommand command = new SparkCommand(jobRunInfo.getId(), deployMode);
        String execMode = String.format(EXEC_MODE, deployMode.mode, deployMode.target);
        command.setPrefix(SUBMIT_PREFIX  + execMode);

        List<URL> classpaths = getOrCreateClasspaths(jobRunInfo.getJobCode(), sparkJob.getExtJarPaths());
        command.setClasspaths(classpaths);

        String localPathOfMainJar = getLocalPathOfMainJar(jobRunInfo.getJobCode(), jobRunInfo.getSubject());
        command.setMainJar(localPathOfMainJar);
        command.setMainArgs(sparkJob.getMainArgs());
        command.setMainClass(sparkJob.getMainClass());
        command.setOptionArgs(sparkJob.getOptionArgs());

        return command;
    }

    private void initExtJarPaths(SparkJob sparkJob) {
        List<String> jarPaths = ListUtils
                .defaultIfNull(sparkJob.getExtJars(), Collections.emptyList()).stream()
                .map(resourceId -> resourceService.getById(resourceId))
                .map(com.flink.platform.dao.entity.Resource::getFullName)
                .collect(toList());
        sparkJob.setExtJarPaths(jarPaths);
    }

    private List<URL> getOrCreateClasspaths(String jobCode, List<String> extJarList) throws Exception {
        List<URL> classpaths = new ArrayList<>(extJarList.size());
        for (String hdfsExtJar : extJarList) {
            String extJarName = new Path(hdfsExtJar).getName();
            String localPath = String.join(SLASH, ROOT_DIR, jobJarDir, jobCode, extJarName);
            copyToLocalIfChanged(hdfsExtJar, localPath);
            copyToRemoteIfChanged(localPath, hdfsExtJar);
            classpaths.add(Paths.get(localPath).toUri().toURL());
        }
        return classpaths;
    }

    private void copyToRemoteIfChanged(String localFile, String hdfsFile) {
        try {
            yarnClientService.copyIfNewHdfsAndFileChanged(localFile, hdfsFile);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Copy %s from local to remote hdfs failed", localFile), e);
        }
    }

    private String getLocalPathOfMainJar(String jobCode, String jarPath) {
        if (!jarPath.toLowerCase().startsWith("hdfs")) {
            return jarPath;
        }

        String jarName = new Path(jarPath).getName();
        String localJarPath = String.join(SLASH, ROOT_DIR, jobJarDir, jobCode, jarName);
        copyToLocalIfChanged(jarPath, localJarPath);
        return localJarPath;
    }

    private void copyToLocalIfChanged(String hdfsFile, String localFile) {
        try {
            storageService.copyFileToLocalIfChanged(hdfsFile, localFile);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Copy %s from hdfs to local disk failed", hdfsFile), e);
        }
    }
}
