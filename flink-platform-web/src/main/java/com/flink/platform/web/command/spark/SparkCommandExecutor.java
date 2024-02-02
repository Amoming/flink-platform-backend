package com.flink.platform.web.command.spark;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.entity.result.ShellCallback;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.AbstractTask;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.external.YarnClientService;
import com.flink.platform.web.util.YarnHelper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.JobConstant.HADOOP_USER_NAME;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;

@Slf4j
@Component("sparkCommandExecutor")
public class SparkCommandExecutor  implements CommandExecutor {

    @Value("${storage.username}")
    private String hadoopUser;

    @Autowired
    private WorkerConfig workerConfig;

    @Lazy
    @Autowired
    private YarnClientService yarnClientService;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == JobType.SPARK;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        SparkCommand sparkCommand = (SparkCommand) command;
        SparkYarnTask task = new SparkYarnTask(
                sparkCommand.getJobRunId(),
                sparkCommand.getMode(),
                sparkCommand.toCommandString(),
                buildEnvProps(),
                workerConfig.getFlinkSubmitTimeoutMills());
        sparkCommand.setTask(task);
        task.run();

        String appId = task.getAppId();
        log.info("appId:" + appId);
        ShellCallback callback = task.buildShellCallback();

        // call `killCommand` method if execute command failed.
        if (task.finalStatus() != SUCCESS) {
            return new JobCallback(null, appId, null, callback, EMPTY, task.finalStatus());
        }

        // 构造回调体
        if (StringUtils.isNotEmpty(appId)) {
            ExecutionStatus status = SUBMITTED;
            String trackingUrl = EMPTY;
            try {
                ApplicationReport applicationReport = yarnClientService.getApplicationReport(appId);
                status = YarnHelper.getStatus(applicationReport);
                trackingUrl = applicationReport.getTrackingUrl();
            } catch (Exception e) {
                log.error("Failed to get ApplicationReport after command executed", e);
            }
            return new JobCallback(null, appId, trackingUrl, callback, EMPTY, status);
        } else {
            return new JobCallback(null, appId, EMPTY, callback, EMPTY, FAILURE);
        }
    }

    @Override
    public void killCommand(@Nonnull JobCommand command) {
        // Need provide processId, applicationId, deployMode.
        AbstractTask task = command.getTask();
        if (task == null) {
            JobRunInfo jobRun = jobRunInfoService.getById(command.getJobRunId());
            JobCallback jobCallback = jobRun.getBackInfo();
            if (!jobRun.getStatus().isTerminalState() && jobCallback != null) {
                SparkYarnTask newTask = new SparkYarnTask(jobRun.getId(), jobRun.getDeployMode());
                newTask.setProcessId(jobCallback.getProcessId());
                newTask.setAppId(jobCallback.getAppId());
                task = newTask;
            }
        }

        if (task != null) {
            task.cancel();
        }
    }

    private String[] buildEnvProps() {
        return new String[] {String.format("%s=%s", HADOOP_USER_NAME, hadoopUser)};
    }
}
